package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.utils.network.Http
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import javax.imageio.ImageIO

object QQAvatarManager {

    private enum class State {
        UNINITIALIZED,
        LOADING,
        READY,
        FAILED
    }

    private val AVATAR_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("qq/avatar")
    private val FALLBACK_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/gui/steve.png")
    private val QQ_PARTITION = Pattern.compile("qqnt_([1-9][0-9]{5,11})")
    private val QQ_DIRECTORY = Pattern.compile("([1-9][0-9]{5,11})")
    private const val TEXTURE_SIZE = 128

    private var avatarTexture: DynamicTexture? = null
    private var state = State.UNINITIALIZED
    private var generation = 0L

    fun texture(): Identifier {
        return if (state == State.READY) AVATAR_TEXTURE else FALLBACK_TEXTURE
    }

    fun linearFilter(): Boolean {
        return state == State.READY
    }

    fun requestLoad() {
        if (state != State.UNINITIALIZED) return

        val qq = findLocalAccount()
        if (qq == null) {
            state = State.FAILED
            return
        }

        state = State.LOADING
        val requestGeneration = ++generation
        ExecutorManager.execute { loadAvatar(qq, requestGeneration) }
    }

    fun release() {
        generation++
        state = State.UNINITIALIZED
        if (avatarTexture != null) {
            Constants.client.textureManager.release(AVATAR_TEXTURE)
            avatarTexture = null
        }
    }

    private fun loadAvatar(qq: String, requestGeneration: Long) {
        val image: BufferedImage
        try {
            image = downloadAvatar(qq)
        } catch (e: IOException) {
            Constants.client.execute { fail(requestGeneration) }
            return
        } catch (e: RuntimeException) {
            Constants.client.execute { fail(requestGeneration) }
            return
        }

        Constants.client.execute { uploadAvatar(image, requestGeneration) }
    }

    private fun uploadAvatar(source: BufferedImage, requestGeneration: Long) {
        if (requestGeneration != generation || state != State.LOADING) return

        var image: NativeImage? = null
        var texture: DynamicTexture? = null
        try {
            image = toNativeImage(source)
            texture = DynamicTexture({ "Epsilon local QQ avatar" }, image)
            image = null
            Constants.client.textureManager.register(AVATAR_TEXTURE, texture)
            avatarTexture = texture
            texture = null
            state = State.READY
        } catch (e: RuntimeException) {
            if (texture != null) {
                texture.close()
            } else if (image != null) {
                image.close()
            }
            fail(requestGeneration)
        }
    }

    private fun fail(requestGeneration: Long) {
        if (requestGeneration == generation && state == State.LOADING) {
            state = State.FAILED
        }
    }

    @Throws(IOException::class)
    private fun downloadAvatar(qq: String): BufferedImage {
        val url = "https://q1.qlogo.cn/g?b=qq&nk=$qq&s=640"
        val response = Http.get(url).exceptionHandler { }
            .sendInputStream()
            ?: throw IOException("QQ avatar request failed")

        response.use { input ->
            val image = ImageIO.read(input)
            if (image == null || image.width <= 0 || image.height <= 0) {
                throw IOException("Unsupported QQ avatar image")
            }
            return image
        }
    }

    private fun toNativeImage(source: BufferedImage): NativeImage {
        val cropSize = Math.min(source.width, source.height)
        val cropX = (source.width - cropSize) / 2
        val cropY = (source.height - cropSize) / 2
        val normalized = BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = normalized.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(
            source, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE,
            cropX, cropY, cropX + cropSize, cropY + cropSize, null
        )
        graphics.dispose()

        val result = NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false)
        try {
            for (y in 0 until TEXTURE_SIZE) {
                for (x in 0 until TEXTURE_SIZE) {
                    result.setPixel(x, y, normalized.getRGB(x, y))
                }
            }
            return result
        } catch (e: RuntimeException) {
            result.close()
            throw e
        }
    }

    private fun findLocalAccount(): String? {
        val appData = System.getenv("APPDATA")
        if (appData != null && !appData.isBlank()) {
            val account = newestMatchingDirectory(Path.of(appData, "QQ", "Partitions"), QQ_PARTITION)
            if (account != null) return account
        }

        val userHome = System.getProperty("user.home")
        if (userHome == null || userHome.isBlank()) return null
        return newestMatchingDirectory(Path.of(userHome, "Documents", "Tencent Files"), QQ_DIRECTORY)
    }

    private fun newestMatchingDirectory(parent: Path, pattern: Pattern): String? {
        if (!Files.isDirectory(parent)) return null

        var newestAccount: String? = null
        var newestModified = Long.MIN_VALUE
        try {
            Files.newDirectoryStream(parent, DirectoryStream.Filter { path -> Files.isDirectory(path) }).use { directories ->
                for (directory in directories) {
                    val fileName = directory.fileName ?: continue

                    val matcher = pattern.matcher(fileName.toString())
                    if (!matcher.matches()) continue

                    val modified = Files.getLastModifiedTime(directory).toMillis()
                    if (modified > newestModified) {
                        newestModified = modified
                        newestAccount = matcher.group(1)
                    }
                }
            }
        } catch (ignored: IOException) {
            return null
        } catch (ignored: SecurityException) {
            return null
        }
        return newestAccount
    }

}
