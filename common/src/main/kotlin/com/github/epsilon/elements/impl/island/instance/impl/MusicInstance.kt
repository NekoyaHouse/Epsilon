package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.elements.impl.island.IslandPalette
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.kernel.media.MediaArtwork
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import java.awt.Color
import com.github.epsilon.gui.lib.*
import com.github.epsilon.kernel.media.MediaSessionSnapshot
import java.util.function.Supplier
import kotlin.math.sin

class MusicInstance(
    private val snapshotSupplier: Supplier<MediaSessionSnapshot?>,
    private val textRendererSupplier: Supplier<TextRenderer>,
    pattern: LCPattern,
) : LandInstance(pattern, 1) {
    private var frame: MediaSessionSnapshot? = null
    private var uploadedArtwork: MediaArtwork? = null
    private var coverTexture: LuminTexture? = null

    private fun retire(texture: LuminTexture?) {
        if (texture == null) return
        mc.execute { RenderSystem.queueFencedTask { texture.close() } }
    }

    override fun onRemoved() {
        retire(coverTexture)
        coverTexture = null
        uploadedArtwork = null
        frame = null
    }

    private fun syncCover(artwork: MediaArtwork?) {
        if (uploadedArtwork === artwork) return
        RenderSystem.assertOnRenderThread()
        retire(coverTexture)
        coverTexture = null
        uploadedArtwork = artwork
        if (artwork == null) return
        NativeImage(MediaArtwork.SIZE, MediaArtwork.SIZE, false).use { image ->
            image.pixelBytes.put(artwork.pixels()).rewind()
            val device = RenderSystem.getDevice()
            val texture = device.createTexture(
                "epsilon/island_music_cover", GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, image.width, image.height, 1, 1
            )
            var view: GpuTextureView? = null
            try {
                device.createCommandEncoder().writeToTexture(texture, image)
                view = device.createTextureView(texture)
                coverTexture = LuminTexture(texture, view,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), true, false)
            } catch (failure: Exception) {
                view?.close()
                texture.close()
                // A cover upload failure should leave a placeholder, not suppress the whole HUD.
                System.getLogger("Epsilon-Island-Music").log(System.Logger.Level.WARNING,
                    "Unable to upload SMTC cover", failure)
            }
        }
    }

    override fun update() {
        frame = snapshotSupplier.get()?.takeIf { it.playing() }
        val data = frame ?: return
        val renderer = textRendererSupplier.get()
        targetRadius = 0.8f
        targetWidth = (renderer.getWidth(data.title(), TITLE_SCALE) + 114f).coerceIn(252f, 328f)
        targetHeight = 88f
    }

    override val maxContainerRadius: Float get() = 16f

    override fun draw(scope: UiScope, width: Float, height: Float) {
        val data = frame ?: return
        if (width < 110f || height < 64f) return
        syncCover(data.artwork())
        val renderer = textRendererSupplier.get()
        val textX = 84f
        val textWidth = (width - textX - 30f).coerceAtLeast(1f)
        val secondary = data.artist().ifBlank { data.source() }
        val cover = coverTexture
        scope.RoundRect(9f, 9f, 66f, 66f, 12f, fade(IslandPalette.TRACK))
        if (cover != null) {
            scope.RoundedTexture(cover, 9f, 9f, 66f, 66f, 12f, 0f, 0f, 1f, 1f, fade(Color.WHITE))
        } else {
            // Neutral record silhouette when the player does not publish artwork.
            scope.Circle(42f, 42f, 23f, 1f, fade(IslandPalette.TRACK))
            scope.Circle(42f, 42f, 9f, 1f, fade(IslandPalette.ACCENT_ALT))
            scope.Circle(42f, 42f, 3f, 1f, fade(IslandPalette.TEXT_PRIMARY))
        }
        scope.Outline(9f, 9f, 66f, 66f, 12f, 0.7f, fade(IslandPalette.OUTLINE))
        scope.Text(ellipsize(data.title(), textWidth, TITLE_SCALE, renderer), textX, 12f,
            TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
        scope.Text(ellipsize(secondary, width - textX - 12f, DETAIL_SCALE, renderer), textX, 30f,
            DETAIL_SCALE, fade(IslandPalette.TEXT_SECONDARY))

        // Compact status glyph aligned with the title, leaving the actual cover unobstructed.
        if (data.playing()) {
            val phase = (System.nanoTime() % 10_000_000_000L) / 1_000_000_000.0
            repeat(3) { index ->
                val barHeight = 4f + ((sin(phase * 5.0 + index * 1.8) + 1.0) * 4.0).toFloat()
                scope.RoundRect(width - 24f + index * 4f, 20f - barHeight / 2f, 2f, barHeight, 1f,
                    fade(IslandPalette.ACCENT))
            }
        } else {
            repeat(2) { scope.RoundRect(width - 23f + it * 6f, 15f, 3f, 10f, 1f, fade(IslandPalette.TEXT_MUTED)) }
        }
        val barWidth = (width - textX - 12f).coerceAtLeast(1f)
        val barY = 55f
        scope.RoundRect(textX, barY, barWidth, 3f, 1.5f, fade(IslandPalette.TRACK))
        val duration = data.durationMillis()
        val position = data.positionAt(System.nanoTime())
        if (duration > 0L) {
            val progress = (position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
            if (progress > 0f) scope.RoundRectHorizontalGradient(textX, barY, barWidth * progress, 3f, 1.5f,
                fade(IslandPalette.ACCENT), fade(IslandPalette.ACCENT_ALT))
        }
        val elapsed = if (duration > 0L) timestamp(position) else "--:--"
        val total = if (duration > 0L) timestamp(duration) else "--:--"
        scope.Text(elapsed, textX, barY + 9f, DETAIL_SCALE, fade(IslandPalette.TEXT_MUTED))
        scope.Text(total, width - 12f - renderer.getWidth(total, DETAIL_SCALE), barY + 9f,
            DETAIL_SCALE, fade(IslandPalette.TEXT_MUTED))
    }

    private fun ellipsize(text: String, width: Float, scale: Float, renderer: TextRenderer): String {
        if (renderer.getWidth(text, scale) <= width) return text
        val suffix = "…"
        if (renderer.getWidth(suffix, scale) > width) return ""
        // Code-point boundaries avoid splitting surrogate pairs in titles from native UTF-16 strings.
        val points = text.codePoints().toArray()
        var low = 0
        var high = points.size
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (renderer.getWidth(String(points, 0, mid) + suffix, scale) <= width) low = mid
            else high = mid - 1
        }
        return String(points, 0, low) + suffix
    }

    private fun timestamp(millis: Long): String {
        val seconds = millis.coerceAtLeast(0L) / 1000L
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    companion object {
        private const val TITLE_SCALE = 1f
        private const val DETAIL_SCALE = 0.8f
    }
}
