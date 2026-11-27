package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.elements.impl.island.IslandPalette
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
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
    private var marqueeTrack = ""
    private var marqueeStartedAtNs = System.nanoTime()

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
            val texture = device.createTexture("epsilon/island_music_cover", GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, image.width, image.height, 1, 1)
            var view: GpuTextureView? = null
            try {
                device.createCommandEncoder().writeToTexture(texture, image)
                view = device.createTextureView(texture)
                coverTexture = LuminTexture(texture, view,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), true, false)
            } catch (_: Exception) {
                view?.close()
                texture.close()
            }
        }
    }

    override fun update() {
        frame = snapshotSupplier.get()?.takeIf { it.playing() }
        val data = frame ?: return
        val track = data.source() + "\u0000" + data.title() + "\u0000" + data.artist()
        if (track != marqueeTrack) {
            marqueeTrack = track
            marqueeStartedAtNs = System.nanoTime()
        }

        val renderer = textRendererSupplier.get()
        val artist = data.artist().ifBlank { sourceName(data.source()) }
        val desiredContent = maxOf(
            renderer.getWidth(data.title(), TITLE_SCALE) + WAVE_WIDTH + TITLE_STATUS_GAP,
            iconRowWidth(IconChars.ARTIST, artist, ARTIST_SCALE, renderer),
            iconRowWidth(IconChars.AUDIO_FILE, sourceName(data.source()), META_SCALE, renderer)
        )
        val screenMaxWidth = maxOf(MIN_WIDTH, LuminRenderSystem.getScaledWidthInt() - 50f)
        targetWidth = (PADDING * 2f + COVER_SIZE + COVER_GAP + desiredContent)
            .coerceIn(MIN_WIDTH, minOf(MAX_WIDTH, screenMaxWidth))
        targetHeight = PADDING * 2f + COVER_SIZE
        targetRadius = 0.42f
    }

    override val maxContainerRadius: Float get() = 12f

    override fun draw(scope: UiScope, width: Float, height: Float) {
        val data = frame ?: return
        if (width < 100f || height < 28f) return
        syncCover(data.artwork())
        drawCover(scope)

        val renderer = textRendererSupplier.get()
        val textX = PADDING + COVER_SIZE + COVER_GAP
        val contentRight = width - PADDING
        val titleViewport = maxOf(1f, contentRight - WAVE_WIDTH - TITLE_STATUS_GAP - textX)
        val titleY = PADDING + 1f
        drawMarqueeTitle(scope, renderer, data.title(), textX, titleY, titleViewport)
        drawPlaybackWave(scope, contentRight - WAVE_WIDTH, titleY + 1f)

        val artistY = titleY + renderer.getHeight(TITLE_SCALE) + 3f
        drawIconText(scope, renderer, IconChars.ARTIST, data.artist().ifBlank { sourceName(data.source()) },
            textX, artistY, maxOf(1f, contentRight - textX), ARTIST_SCALE, IslandPalette.TEXT_SECONDARY)
        val metaY = height - PADDING - renderer.getHeight(META_SCALE) - 1f
        drawIconText(scope, renderer, IconChars.AUDIO_FILE, sourceName(data.source()),
            textX, metaY, maxOf(1f, contentRight - textX), META_SCALE, IslandPalette.TEXT_MUTED)
    }

    private fun drawCover(scope: UiScope) {
        val cover = coverTexture
        if (cover != null) {
            scope.RoundedTexture(cover, PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 0f, 0f, 1f, 1f, fade(Color.WHITE))
        } else {
            scope.RoundRectHorizontalGradient(PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, fade(Color(75, 88, 116)), fade(Color(67, 125, 122)))
            val renderer = textRendererSupplier.get()
            val iconScale = 1.35f
            val iconWidth = renderer.getWidth(IconChars.ALBUM, iconScale, StaticFontLoader.ICONS)
            val iconHeight = renderer.getHeight(iconScale, StaticFontLoader.ICONS)
            scope.Text(IconChars.ALBUM, PADDING + (COVER_SIZE - iconWidth) * 0.5f, PADDING + (COVER_SIZE - iconHeight) * 0.5f, iconScale, fade(IslandPalette.TEXT_PRIMARY), StaticFontLoader.ICONS)
        }
        scope.Outline(PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 0.75f, fade(IslandPalette.OUTLINE))
    }

    private fun drawMarqueeTitle(scope: UiScope, renderer: TextRenderer, title: String, x: Float, y: Float, viewportWidth: Float) {
        val textWidth = renderer.getWidth(title, TITLE_SCALE)
        if (textWidth <= viewportWidth) {
            scope.Text(title, x, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
            return
        }
        val seconds = (System.nanoTime() - marqueeStartedAtNs).coerceAtLeast(0L) / 1_000_000_000.0
        val cycle = textWidth + MARQUEE_GAP
        val offset = -((seconds - MARQUEE_START_HOLD_SECONDS).coerceAtLeast(0.0) * MARQUEE_SPEED % cycle).toFloat()
        scope.Clip(x, y - 1f, viewportWidth, renderer.getHeight(TITLE_SCALE) + 2f) {
            Text(title, x + offset, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
            Text(title, x + offset + cycle, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
        }
    }

    private fun iconRowWidth(icon: String, text: String, scale: Float, renderer: TextRenderer): Float = renderer.getWidth(icon, ICON_SCALE, StaticFontLoader.ICONS) + 3f + renderer.getWidth(text, scale)

    private fun drawIconText(scope: UiScope, renderer: TextRenderer, icon: String, text: String, x: Float, y: Float, viewportWidth: Float, scale: Float, color: Color) {
        val iconWidth = renderer.getWidth(icon, ICON_SCALE, StaticFontLoader.ICONS)
        val iconY = y + (renderer.getHeight(scale) - renderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS)) * 0.5f
        scope.Text(icon, x, iconY, ICON_SCALE, fade(IslandPalette.ACCENT), StaticFontLoader.ICONS)
        val textX = x + iconWidth + 3f
        scope.Clip(textX, y - 1f, maxOf(1f, viewportWidth - iconWidth - 3f), renderer.getHeight(scale) + 2f) {
            Text(text, textX, y, scale, fade(color))
        }
    }

    private fun drawPlaybackWave(scope: UiScope, x: Float, y: Float) {
        val barWidth = 2f
        val gap = (WAVE_WIDTH - 4f * barWidth) / 3f
        val time = (System.nanoTime() % 60_000_000_000L) / 1_000_000_000.0
        repeat(4) { index ->
            val amplitude = 0.28f + 0.72f * ((sin(time * 4.8 + index * 1.7) + 1.0) * 0.5).toFloat()
            val barHeight = maxOf(2f, 9f * amplitude)
            scope.RoundRect(x + index * (barWidth + gap), y + (9f - barHeight) * 0.5f, barWidth, barHeight, 1f, fade(if (index < 2) IslandPalette.ACCENT else IslandPalette.ACCENT_ALT))
        }
    }

    private fun sourceName(source: String): String = source.substringAfterLast('!').replace(Regex("\\.exe$", RegexOption.IGNORE_CASE), "")

    private companion object {
        const val PADDING = 7f
        const val COVER_SIZE = 42f
        const val COVER_RADIUS = 8f
        const val COVER_GAP = 8f
        const val MIN_WIDTH = 216f
        const val MAX_WIDTH = 286f
        const val TITLE_SCALE = 1f
        const val ARTIST_SCALE = 0.78f
        const val META_SCALE = 0.68f
        const val ICON_SCALE = 0.78f
        const val WAVE_WIDTH = 18f
        const val TITLE_STATUS_GAP = 8f
        const val MARQUEE_SPEED = 22f
        const val MARQUEE_START_HOLD_SECONDS = 1.25
        const val MARQUEE_GAP = 28f
    }
}
