package com.github.epsilon.graphics.video

import com.github.epsilon.Constants
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

object VideoPlayer {

    private val VIDEO_TEX: VideoGpuTexture = VideoGpuTexture()

    private val GUI_TEXTURE_ID: Identifier = Identifier.fromNamespaceAndPath("video_mainmenu_epsilon", "video")

    private var registeredTexture: DynamicTexture? = null

    private var grabber: FFmpegFrameGrabber? = null

    private var mediaStartUs = 0L
    private var wallStartNs = 0L

    @Volatile
    private var lastPtsUs = 0L

    private var executor: ExecutorService? = null
    private var decodeFuture: Future<*>? = null

    private val pauseLock: java.lang.Object = java.lang.Object()

    @Volatile
    private var paused = false

    @Volatile
    private var stopped = true

    private var uploadedFrameId = -1L

    private val GRABBER_LOCK: java.lang.Object = java.lang.Object()

    private val latestFrame = AtomicReference<FrameBuffer?>()

    private const val POOL = 4

    private val pool = arrayOfNulls<ByteBuffer>(POOL)
    private var poolCursor = 0

    private fun releaseGuiTexture() {
        if (registeredTexture == null) {
            return
        }
        Constants.client.textureManager.release(GUI_TEXTURE_ID)
        registeredTexture = null
    }

    private fun acquire(size: Int): ByteBuffer {
        val cur = latestFrame.get()
        val curBuf = cur?.buffer

        for (n in 0 until POOL) {
            val idx = (poolCursor + n) % POOL
            var b = pool[idx]

            if (b === curBuf) continue
            if (b == null || b.capacity() < size) {
                b = ByteBuffer.allocateDirect(size)
                pool[idx] = b
            }

            poolCursor = (idx + 1) % POOL
            return b
        }

        return ByteBuffer.allocateDirect(size)
    }

    fun init(file: File) {
        stop()

        try {
            executor = Executors.newSingleThreadExecutor { r ->
                val t = Thread(r, "VideoUtil-Decode")
                t.isDaemon = true
                t
            }

            synchronized(GRABBER_LOCK) {
                val newGrabber = FFmpegFrameGrabber(file)
                grabber = newGrabber
                newGrabber.setPixelFormat(AV_PIX_FMT_RGBA)
                newGrabber.setOption("threads", "4")
                newGrabber.start()

                val first = newGrabber.grabImage()
                if (first != null && first.image != null) {
                    val fb = copyFrameLocked(first)
                    latestFrame.set(fb)
                    lastPtsUs = if (first.timestamp > 0) first.timestamp else newGrabber.getTimestamp()
                }

                mediaStartUs = newGrabber.getTimestamp()
                wallStartNs = System.nanoTime()
                lastPtsUs = mediaStartUs
            }

            stopped = false
            paused = false

            startDecodeThread()
        } catch (e: Throwable) {
            Constants.LOGGER.error("[VideoPlayer] Init error:", e)
            stop()
        }
    }

    fun pause() {
        if (stopped) return
        paused = true
    }

    fun resume() {
        if (!paused) return

        synchronized(pauseLock) {
            paused = false
            wallStartNs = System.nanoTime() - (lastPtsUs - mediaStartUs) * 1000L
            pauseLock.notifyAll()
        }
    }

    fun stop() {
        try {
            if (stopped && grabber == null) return

            stopped = true

            synchronized(pauseLock) {
                paused = false
                pauseLock.notifyAll()
            }

            val decodeFuture = decodeFuture
            if (decodeFuture != null) {
                decodeFuture.cancel(true)
                this.decodeFuture = null
            }

            val executor = executor
            if (executor != null) {
                executor.shutdown()
                executor.awaitTermination(2, TimeUnit.SECONDS)
                this.executor = null
            }

            synchronized(GRABBER_LOCK) {
                val grabber = grabber
                if (grabber != null) {
                    try {
                        grabber.stop()
                    } catch (ignored: Throwable) {
                    }
                    try {
                        grabber.close()
                    } catch (ignored: Throwable) {
                    }
                    this.grabber = null
                }
            }

            latestFrame.set(null)
            uploadedFrameId = -1L


            val closeGpu = Runnable { VIDEO_TEX.close() }
            if (RenderSystem.isOnRenderThread()) {
                closeGpu.run()
            } else {
                RenderSystem.queueFencedTask(closeGpu)
            }

            Constants.LOGGER.info("[VideoPlayer] Stopped")
        } catch (e: Throwable) {
            Constants.LOGGER.error("[VideoPlayer] Stop error:", e)
        }
    }

    fun getGuiTexture(): Identifier? {
        if (stopped || paused) return null
        if (!RenderSystem.isOnRenderThread()) return null

        val frame = latestFrame.get()
        if (frame == null) return null

        if (frame.id != uploadedFrameId) {
            VIDEO_TEX.uploadRgba(frame.buffer, frame.width, frame.height)
            uploadedFrameId = frame.id
        }

        val texture = VIDEO_TEX.texture()
        if (texture == null) return null

        if (registeredTexture !== texture) {
            Constants.client.textureManager.register(GUI_TEXTURE_ID, texture)
            registeredTexture = texture
        }

        return GUI_TEXTURE_ID
    }

    fun isStopped(): Boolean {
        return stopped
    }

    fun isPaused(): Boolean {
        return paused
    }

    fun getGuiTextureWidth(): Int {
        val width = VIDEO_TEX.width()
        if (width > 0) {
            return width
        }
        val frame = latestFrame.get()
        return frame?.width ?: -1
    }

    fun getGuiTextureHeight(): Int {
        val height = VIDEO_TEX.height()
        if (height > 0) {
            return height
        }
        val frame = latestFrame.get()
        return frame?.height ?: -1
    }

    private fun startDecodeThread() {
        decodeFuture = executor!!.submit(Runnable {
            try {
                while (!stopped) {
                    if (paused) {
                        synchronized(pauseLock) {
                            while (paused && !stopped) pauseLock.wait()
                        }
                        if (stopped) break
                    }

                    lateinit var decodedFrame: FrameBuffer
                    var ptsUs = 0L

                    synchronized(GRABBER_LOCK) {
                        val grabber = grabber
                        if (stopped || grabber == null) break

                        val frame = grabber.grabImage()
                        if (frame == null || frame.image == null) {
                            grabber.setTimestamp(0)
                            val first = grabber.grabImage()
                            if (first != null && first.image != null) {
                                ptsUs = if (first.timestamp > 0) first.timestamp else grabber.getTimestamp()
                                lastPtsUs = ptsUs

                                latestFrame.set(copyFrameLocked(first))
                                mediaStartUs = grabber.getTimestamp()
                                wallStartNs = System.nanoTime()
                            }
                            continue
                        }

                        ptsUs = if (frame.timestamp > 0) frame.timestamp else grabber.getTimestamp()
                        lastPtsUs = ptsUs
                        decodedFrame = copyFrameLocked(frame)
                    }

                    val targetNs = wallStartNs + (ptsUs - mediaStartUs) * 1000L
                    while (!stopped && !paused) {
                        val sleepNs = targetNs - System.nanoTime()
                        if (sleepNs <= 0L) break
                        LockSupport.parkNanos(Math.min(sleepNs, 5_000_000L))
                        if (Thread.interrupted()) {
                            throw InterruptedException()
                        }
                    }
                    if (!stopped && !paused) {
                        latestFrame.set(decodedFrame)
                    }
                }
            } catch (ignored: InterruptedException) {
            } catch (e: Throwable) {
                Constants.LOGGER.error("[VideoPlayer] Decode thread error:", e)
            }
        })
    }

    private fun copyFrameLocked(frame: Frame): FrameBuffer {
        val w = frame.imageWidth
        val h = frame.imageHeight

        val channels = 4
        val rowBytes = w * channels
        val size = rowBytes * h

        val src = frame.image[0] as ByteBuffer
        val dst = acquire(size)
        dst.clear()

        val stride = Math.abs(frame.imageStride)
        if (stride == rowBytes) {
            val contiguous = src.duplicate()
            contiguous.rewind()
            contiguous.limit(Math.min(contiguous.remaining(), size))
            dst.put(contiguous)
        } else {
            for (row in 0 until h) {
                val sourceRow = if (frame.imageStride >= 0) row else h - row - 1
                val start = sourceRow * stride
                val line = src.duplicate()
                line.position(start)
                line.limit(Math.min(start + rowBytes, src.capacity()))
                dst.put(line)
            }
        }
        dst.flip()

        return FrameBuffer(dst, w, h, System.nanoTime())
    }

    private class VideoGpuTexture {

        private var tex: DynamicTexture? = null
        private var w = -1
        private var h = -1

        fun close() {
            val tex = tex
            if (tex != null) {
                if (registeredTexture === tex) {
                    releaseGuiTexture()
                } else {
                    try {
                        tex.close()
                    } catch (ignored: Throwable) {
                    }
                }
                this.tex = null
            }
            w = -1
            h = -1
        }

        fun view(): GpuTextureView? {
            return tex?.textureView
        }

        fun texture(): DynamicTexture? {
            return tex
        }

        fun width(): Int {
            return w
        }

        fun height(): Int {
            return h
        }

        fun ensureSize(newW: Int, newH: Int) {
            if (tex != null && w == newW && h == newH) return

            close()
            tex = DynamicTexture("video", newW, newH, true)
            w = newW
            h = newH
        }

        fun uploadRgba(rgba: ByteBuffer, frameW: Int, frameH: Int) {
            ensureSize(frameW, frameH)

            val src = rgba.duplicate()
            src.rewind()

            val enc: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
            enc.writeToTexture(
                tex!!.texture,
                src,
                0,
                0,
                0,
                0,
                frameW,
                frameH
            )
        }
    }

    private data class FrameBuffer(val buffer: ByteBuffer, val width: Int, val height: Int, val id: Long)
}
