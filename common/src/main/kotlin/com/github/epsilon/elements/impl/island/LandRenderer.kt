package com.github.epsilon.elements.impl.island

import com.github.epsilon.Constants
import com.github.epsilon.elements.impl.island.instance.LandController
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.jvm.runSafeOrElse
import com.github.epsilon.kernel.render.animation.Animation
import com.github.epsilon.kernel.render.animation.Easing
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.multiplayer.ServerData
import java.awt.Color

class LandRenderer(
    private val controller: LandController,
    defaultWidth: Float,
    defaultHeight: Float,
) {

    private val radiusAnim = Animation(Easing.DECELERATE, 160)
    private val widthAnim = Animation(Easing.EASE_OUT_BACK, 220)
    private val heightAnim = Animation(Easing.EASE_OUT_BACK, 220)

    private val watermarkAlpha = Animation(Easing.DECELERATE, 90)
    private val contentAlpha = Animation(Easing.DECELERATE, 90)

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    private var watermarkWidth: Float = 154f

    private var watermarkSegments: List<WatermarkSegment> = emptyList()

    private var current: LandInstance? = null

    private lateinit var textRenderer: TextRenderer

    init {
        radiusAnim.startValue = 1.0f
        widthAnim.startValue = defaultWidth
        heightAnim.startValue = defaultHeight

        watermarkAlpha.startValue = 1.0f
        contentAlpha.startValue = 0.0f
    }

    /**
     * 提供用于测量文本的渲染器。
     *
     * @param textRenderer 文本渲染器
     */
    fun setTextRenderer(textRenderer: TextRenderer) {
        this.textRenderer = textRenderer
    }

    /**
     * 选出当前实例并推进所有形变与淡入淡出动画。
     */
    fun update() {
        val current = controller.update()
        updateWatermarkText()
        val hasContent = current != null

        watermarkAlpha.run(if (hasContent) 0.0f else 1.0f)
        contentAlpha.run(if (hasContent) 1.0f else 0.0f)

        if (current != null) {
            current.update()
            radiusAnim.run(current.targetRadius)
            widthAnim.run(current.targetWidth)
            heightAnim.run(current.targetHeight)
        } else {
            radiusAnim.run(1.0f)
            widthAnim.run(watermarkWidth)
            heightAnim.run(WATERMARK_HEIGHT)
        }

        this.current = current
    }

    /**
     * 绘制容器与当前实例。
     *
     * 作用域原点就是岛屿元素左上角：容器本身占满该作用域，实例内容再通过
     * [Positioned] 建立「容器原点」子作用域，因此实例内部坐标相对容器。
     */
    fun draw(island: Island, scope: UiScope) {
        val w = scope.width
        val h = scope.height

        if (w < 1f || h < 1f) return

        val radius = minOf(w, h) * 0.4f * radiusAnim.value.coerceIn(0f, 1f)

        if (island.backgroundBlur) {
            // 背景模糊直接绘制到屏幕，需要绝对坐标
            BlurShader.render(scope.origin.x, scope.origin.y, w, h, radius, island.blurStrength.toFloat())
        }
        if (island.drawShadow) {
            scope.Shadow(0f, 0f, w, h, radius, island.shadowBlur.toFloat(), island.shadowColor)
        }
        scope.RoundRect(0f, 0f, w, h, radius, island.backgroundColor)
        if (island.outline) {
            scope.Outline(0f, 0f, w, h, radius, island.outlineWidth.toFloat(), island.outlineColor)
        }

        val watermarkAlpha = this.watermarkAlpha.value
        if (watermarkAlpha > 0.001f) {
            scope.Clip(0f, 0f, w, h) { drawWatermark(island, this, watermarkAlpha) }
        }

        val current = this.current
        val contentAlpha = this.contentAlpha.value
        if (current != null && contentAlpha > 0.001f) {
            current.setContentAlpha(contentAlpha)
            scope.Clip(0f, 0f, w, h) {
                Positioned(0f, 0f, w, h) {
                    current.draw(this, w, h)
                }
            }
        }
    }

    private fun updateWatermarkText() {
        val playerName = runSafeOrElse("Player") { player.gameProfile.name }
        val serverAddress: String
        if (mc.isLocalServer) {
            serverAddress = "Singleplayer"
        } else {
            val serverData: ServerData? = mc.currentServer
            serverAddress = if (serverData == null || serverData.ip.isBlank()) "Unknown server" else serverData.ip
        }

        val latency = getLatency()
        val latencyColor = getLatencyColor(latency)
        val latencyText = if (latency < 0) "--ms" else latency.toString() + "ms"

        watermarkSegments = listOf(
            WatermarkSegment(
                IconChars.AUTO_AWESOME,
                Constants.NAME,
                IslandPalette.ACCENT_ALT,
                IslandPalette.ACCENT_ALT,
                true
            ),
            WatermarkSegment(
                IconChars.PERSON,
                playerName,
                IslandPalette.TEXT_PRIMARY,
                IslandPalette.TEXT_PRIMARY,
                false
            ),
            WatermarkSegment(
                IconChars.GROUPS,
                getPlayerCount().toString(),
                IslandPalette.TEXT_SECONDARY,
                IslandPalette.TEXT_SECONDARY,
                false
            ),
            WatermarkSegment(
                IconChars.PUBLIC,
                serverAddress,
                IslandPalette.TEXT_SECONDARY,
                IslandPalette.TEXT_SECONDARY,
                false
            ),
            WatermarkSegment(IconChars.NETWORK_PING, latencyText, latencyColor, latencyColor, false),
            WatermarkSegment(
                IconChars.SPEED,
                mc.fps.toString() + " FPS",
                IslandPalette.TEXT_SECONDARY,
                IslandPalette.TEXT_SECONDARY,
                false
            )
        )

        var contentWidth = 0f
        for (i in watermarkSegments.indices) {
            val segment = watermarkSegments[i]
            val font: TtfFontLoader = StaticFontLoader.defaultFont()
            contentWidth += textRenderer.getWidth(segment.icon, ICON_SCALE, StaticFontLoader.ICONS)
            contentWidth += ICON_TEXT_GAP
            contentWidth += textRenderer.getWidth(segment.text, TEXT_SCALE, font)
            if (i + 1 < watermarkSegments.size) {
                contentWidth += SEGMENT_GAP
            }
        }
        watermarkWidth = HORIZONTAL_PADDING * 2f + contentWidth
    }

    private fun getPlayerCount(): Int {
        val connection: ClientPacketListener? = mc.connection
        if (connection != null) {
            val listed = connection.listedOnlinePlayers.size
            if (listed > 0) return listed

            val online = connection.onlinePlayers.size
            if (online > 0) return online
        }
        return runSafeOrElse(0) { level.players().size }
    }

    private fun getLatency(): Int {
        if (mc.isLocalServer) return 0

        runSafe {
            val playerInfo: PlayerInfo? = connection.getPlayerInfo(player.uuid)
            if (playerInfo != null && playerInfo.latency >= 0) {
                return playerInfo.latency
            }
        }

        val serverData: ServerData? = mc.currentServer
        if (serverData == null || serverData.ping < 0) return -1
        return minOf(serverData.ping, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun getLatencyColor(latency: Int): Color {
        if (latency < 0) return IslandPalette.TEXT_MUTED
        if (latency < 150) return IslandPalette.SUCCESS
        if (latency < 300) return IslandPalette.WARNING
        return IslandPalette.DANGER
    }

    private fun drawWatermark(island: Island, scope: UiScope, alpha: Float) {
        val centerY = WATERMARK_HEIGHT * 0.5f
        var contentX = HORIZONTAL_PADDING

        for (i in watermarkSegments.indices) {
            val segment = watermarkSegments[i]
            val font: TtfFontLoader = StaticFontLoader.defaultFont()
            val iconWidth = textRenderer.getWidth(segment.icon, ICON_SCALE, StaticFontLoader.ICONS)
            val iconY = centerY - textRenderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS) * 0.5f
            scope.Text(
                segment.icon, contentX, iconY, ICON_SCALE,
                IslandPalette.withAlphaMul(segment.iconColor, alpha), StaticFontLoader.ICONS
            )
            contentX += iconWidth + ICON_TEXT_GAP

            val textY = centerY - textRenderer.getHeight(TEXT_SCALE, font) * 0.5f
            val textColor = IslandPalette.withAlphaMul(segment.textColor, alpha)
            if (segment.glitch && island.textGlitch) {
                scope.GlitchText(
                    segment.text, contentX, textY, TEXT_SCALE, textColor, TextGlitchEffect(
                        island.chromaticX.toFloat(),
                        island.chromaticY.toFloat(),
                        island.glitchGlowRadius.toFloat(),
                        island.glitchGlowIntensity.toFloat(),
                        island.sliceHeight.toFloat(),
                        island.sliceAmount.toFloat(),
                        island.glitchStrength.toFloat(),
                        island.scanlineStrength.toFloat(),
                        island.glitchColor,
                        island.noiseStrength.toFloat()
                    ), font
                )
            } else {
                scope.Text(segment.text, contentX, textY, TEXT_SCALE, textColor, font)
            }

            contentX += textRenderer.getWidth(segment.text, TEXT_SCALE, font)
            if (i + 1 < watermarkSegments.size) {
                contentX += SEGMENT_GAP
            }
        }
    }

    fun syncPosition(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    val width: Float
        get() = widthAnim.value

    val height: Float
        get() = heightAnim.value

    private data class WatermarkSegment(
        val icon: String,
        val text: String,
        val iconColor: Color,
        val textColor: Color,
        val glitch: Boolean,
    )

    private companion object {

        private const val WATERMARK_HEIGHT = 24f
        private const val HORIZONTAL_PADDING = 7.5f
        private const val SEGMENT_GAP = 4.5f
        private const val ICON_TEXT_GAP = 2.0f
        private const val ICON_SCALE = 1.2f
        private const val TEXT_SCALE = 0.85f

    }

}
