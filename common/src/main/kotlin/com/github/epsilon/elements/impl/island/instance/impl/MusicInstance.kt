package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.Constants
import com.github.epsilon.elements.impl.island.IslandPalette
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.mojang.blaze3d.platform.NativeImage
import com.github.epsilon.smtc.SmtcService
import com.github.epsilon.smtc.SmtcSnapshot
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*
import java.util.function.Supplier
import javax.imageio.ImageIO
import kotlin.math.sin

/**
 * 将 Windows SMTC 当前媒体会话绘制为 Island 内容。
 */
class MusicInstance(
    private val service: SmtcService,
    private val textRendererSupplier: Supplier<TextRenderer>,
    pattern: LCPattern,
) : LandInstance(pattern, 1) {

    private var snapshot: SmtcSnapshot = SmtcSnapshot.UNAVAILABLE
    private var coverRevision: Long = Long.MIN_VALUE
    private var coverTexture: DynamicTexture? = null
    private var coverAvailable: Boolean = false
    private var marqueeTitle: String = ""
    private var marqueeStartedAtNs: Long = System.nanoTime()

    override fun update() {
        snapshot = service.snapshot()
        updateCoverTexture(snapshot)

        val nextTitle = title(snapshot)
        if (nextTitle != marqueeTitle) {
            marqueeTitle = nextTitle
            marqueeStartedAtNs = System.nanoTime()
        }

        targetRadius = 0.42f
        targetHeight = PADDING * 2f + COVER_SIZE

        val textRenderer = textRendererSupplier.get()
        val title = title(snapshot)
        val artist = artist(snapshot)
        val metadata = metadata(snapshot)
        val desiredContent = maxOf(textRenderer.getWidth(title, TITLE_SCALE) + WAVE_WIDTH + TITLE_STATUS_GAP, maxOf(textRenderer.getWidth(artist, ARTIST_SCALE), textRenderer.getWidth(metadata, META_SCALE)))
        val desiredWidth = PADDING * 2f + COVER_SIZE + COVER_GAP + desiredContent
        val screenMaxWidth = maxOf(MIN_WIDTH, LuminRenderSystem.getScaledWidthInt() - 50f)
        targetWidth = Mth.clamp(desiredWidth, MIN_WIDTH, minOf(MAX_WIDTH, screenMaxWidth))
    }

    override fun draw(scope: UiTree.Scope, translateX: Float, translateY: Float, width: Float, height: Float) {
        if (!snapshot.available()) return

        drawCover(scope)

        val textRenderer = textRendererSupplier.get()
        val textX = PADDING + COVER_SIZE + COVER_GAP
        val contentRight = width - PADDING
        val titleRight = contentRight - WAVE_WIDTH - TITLE_STATUS_GAP
        val titleViewport = maxOf(1f, titleRight - textX)

        val titleY = PADDING + 1f
        drawMarqueeTitle(scope, textRenderer, title(snapshot), textX, titleY, titleViewport)
        drawPlaybackWave(scope, contentRight - WAVE_WIDTH, titleY + 1f, WAVE_WIDTH, 9f)

        val artistY = titleY + textRenderer.getHeight(TITLE_SCALE) + 3f
        drawIconText(
            scope, IconChars.ARTIST, artist(snapshot), textX, artistY,
            maxOf(1f, contentRight - textX), ARTIST_SCALE, IslandPalette.TEXT_SECONDARY
        )

        val metaY = height - PADDING - textRenderer.getHeight(META_SCALE) - 1f
        drawIconText(
            scope, if (snapshot.albumTitle().isBlank()) IconChars.AUDIO_FILE else IconChars.ALBUM,
            metadata(snapshot), textX, metaY, maxOf(1f, contentRight - textX), META_SCALE, IslandPalette.TEXT_MUTED
        )
    }

    private fun drawCover(scope: UiTree.Scope) {
        scope.outline(PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 0.75f, fade(Color(255, 255, 255, 48)))

        if (coverAvailable) {
            scope.roundedTexture(
                COVER_TEXTURE, PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS,
                0f, 0f, 1f, 1f, fade(Color.WHITE), true
            )
        } else {
            scope.roundRectHorizontalGradient(
                PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS,
                fade(Color(75, 88, 116)), fade(Color(67, 125, 122))
            )
            val iconScale = 1.35f
            val textRenderer = textRendererSupplier.get()
            val iconWidth = textRenderer.getWidth(IconChars.ALBUM, iconScale, StaticFontLoader.ICONS)
            val iconHeight = textRenderer.getHeight(iconScale, StaticFontLoader.ICONS)
            scope.text(
                IconChars.ALBUM, PADDING + (COVER_SIZE - iconWidth) * 0.5f, PADDING + (COVER_SIZE - iconHeight) * 0.5f,
                iconScale, fade(IslandPalette.TEXT_PRIMARY), StaticFontLoader.ICONS
            )
        }
    }

    private fun drawMarqueeTitle(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        title: String,
        x: Float,
        y: Float,
        viewportWidth: Float,
    ) {
        val textWidth = textRenderer.getWidth(title, TITLE_SCALE)
        if (textWidth <= viewportWidth) {
            scope.text(title, x, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
            return
        }

        val elapsedSeconds = (System.nanoTime() - marqueeStartedAtNs) / 1_000_000_000.0f
        val scrollSeconds = maxOf(0f, elapsedSeconds - MARQUEE_START_HOLD_SECONDS)
        val cycle = textWidth + MARQUEE_GAP
        val offset = -(scrollSeconds * MARQUEE_SPEED % cycle)
        val clip = UiRect(x, y - 1f, viewportWidth, textRenderer.getHeight(TITLE_SCALE) + 2f)
        scope.scissor(clip) { inner ->
            inner.text(title, x + offset, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
            inner.text(title, x + offset + cycle, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY))
        }
    }

    private fun drawIconText(
        scope: UiTree.Scope,
        icon: String,
        text: String,
        x: Float,
        y: Float,
        viewportWidth: Float,
        textScale: Float,
        color: Color,
    ) {
        val textRenderer = textRendererSupplier.get()
        val iconWidth = textRenderer.getWidth(icon, ICON_SCALE, StaticFontLoader.ICONS)
        val iconY = y + (textRenderer.getHeight(textScale) - textRenderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS)) * 0.5f
        scope.text(icon, x, iconY, ICON_SCALE, fade(IslandPalette.ACCENT), StaticFontLoader.ICONS)

        val textX = x + iconWidth + 3f
        val textViewport = maxOf(1f, viewportWidth - iconWidth - 3f)
        val clip = UiRect(textX, y - 1f, textViewport, textRenderer.getHeight(textScale) + 2f)
        scope.scissor(clip) { inner -> inner.text(text, textX, y, textScale, fade(color)) }
    }

    private fun drawPlaybackWave(scope: UiTree.Scope, x: Float, y: Float, width: Float, height: Float) {
        val bars = 4
        val barWidth = 2f
        val gap = (width - bars * barWidth) / (bars - 1)
        val time = System.nanoTime() / 1_000_000_000.0

        for (index in 0 until bars) {
            val amplitude = 0.28f + 0.72f * ((sin(time * 4.8 + index * 1.7) + 1.0) * 0.5).toFloat()
            val barHeight = maxOf(2f, height * amplitude)
            val barX = x + index * (barWidth + gap)
            val barY = y + (height - barHeight) * 0.5f
            val color = if (index < 2) IslandPalette.ACCENT else IslandPalette.ACCENT_ALT
            scope.roundRect(barX, barY, barWidth, barHeight, barWidth * 0.5f, fade(color))
        }
    }

    private fun updateCoverTexture(next: SmtcSnapshot) {
        if (next.thumbnailRevision() == coverRevision) return

        coverRevision = next.thumbnailRevision()
        coverAvailable = false
        val thumbnail = next.thumbnail()
        if (thumbnail == null || thumbnail.isEmpty()) return

        var image: NativeImage? = null
        var newTexture: DynamicTexture? = null
        try {
            image = decodeThumbnail(thumbnail)
            val existingTexture = coverTexture
            if (existingTexture == null) {
                newTexture = DynamicTexture(Supplier { "Epsilon SMTC album art" }, image)
                image = null
                Constants.client.textureManager.register(COVER_TEXTURE, newTexture)
                coverTexture = newTexture
                newTexture = null
            } else {
                existingTexture.pixels = image
                image = null
                existingTexture.upload()
            }
            coverAvailable = true
        } catch (e: IOException) {
            if (newTexture != null) {
                newTexture.close()
            } else if (image != null) {
                image.close()
            }
            Constants.LOGGER.warn("Failed to decode SMTC album art revision {}", coverRevision, e)
        } catch (e: RuntimeException) {
            if (newTexture != null) {
                newTexture.close()
            } else if (image != null) {
                image.close()
            }
            Constants.LOGGER.warn("Failed to decode SMTC album art revision {}", coverRevision, e)
        }
    }

    /**
     * SMTC 封面可能是 JPEG；Minecraft 26.3 的 NativeImage.read 会先强制校验 PNG。
     * 这里先通过 ImageIO 解码并裁成方形，再显式写入 NativeImage。
     */
    private fun decodeThumbnail(thumbnail: ByteArray): NativeImage {
        val source: BufferedImage? = ImageIO.read(ByteArrayInputStream(thumbnail))
        if (source == null || source.width <= 0 || source.height <= 0) {
            throw IOException("Unsupported SMTC thumbnail image format")
        }

        val cropSize = minOf(source.width, source.height)
        val cropX = (source.width - cropSize) / 2
        val cropY = (source.height - cropSize) / 2
        val normalized = BufferedImage(COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = normalized.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(
            source, 0, 0, COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE,
            cropX, cropY, cropX + cropSize, cropY + cropSize, null
        )
        graphics.dispose()

        val result = NativeImage(COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE, false)
        try {
            for (y in 0 until COVER_TEXTURE_SIZE) {
                for (x in 0 until COVER_TEXTURE_SIZE) {
                    result.setPixel(x, y, normalized.getRGB(x, y))
                }
            }
            return result
        } catch (e: RuntimeException) {
            result.close()
            throw e
        }
    }

    private fun releaseCoverTexture() {
        if (coverTexture != null) {
            Constants.client.textureManager.release(COVER_TEXTURE)
            coverTexture = null
        }
        coverAvailable = false
    }

    override fun onRemoved() {
        releaseCoverTexture()
    }

    private companion object {

        private val COVER_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("epsilon", "smtc/album_art")

        private const val PADDING = 7f
        private const val COVER_SIZE = 42f
        private const val COVER_RADIUS = 8f
        private const val COVER_GAP = 8f
        private const val MIN_WIDTH = 216f
        private const val MAX_WIDTH = 286f
        private const val TITLE_SCALE = 1.0f
        private const val ARTIST_SCALE = 0.78f
        private const val META_SCALE = 0.68f
        private const val ICON_SCALE = 0.78f
        private const val WAVE_WIDTH = 18f
        private const val TITLE_STATUS_GAP = 8f
        private const val MARQUEE_SPEED = 22f
        private const val MARQUEE_START_HOLD_SECONDS = 1.25f
        private const val MARQUEE_GAP = 28f
        private const val COVER_TEXTURE_SIZE = 256

        private fun title(snapshot: SmtcSnapshot): String {
            return if (snapshot.title().isBlank()) "Unknown track" else snapshot.title()
        }

        private fun artist(snapshot: SmtcSnapshot): String {
            return if (snapshot.artist().isBlank()) "Unknown artist" else snapshot.artist()
        }

        private fun metadata(snapshot: SmtcSnapshot): String {
            val source = sourceName(snapshot.sourceAppId())
            if (snapshot.albumTitle().isBlank()) return source
            if (source.isBlank()) return snapshot.albumTitle()
            return snapshot.albumTitle() + "  /  " + source
        }

        private fun sourceName(sourceAppId: String?): String {
            if (sourceAppId == null || sourceAppId.isBlank()) return "Windows media"

            var value = sourceAppId
            val appSeparator = value.lastIndexOf('!')
            if (appSeparator >= 0 && appSeparator + 1 < value.length) {
                value = value.substring(appSeparator + 1)
            }
            if (value.lowercase(Locale.ROOT).endsWith(".exe")) {
                value = value.substring(0, value.length - 4)
            }
            return if (value.isBlank()) "Windows media" else value
        }

    }

}
