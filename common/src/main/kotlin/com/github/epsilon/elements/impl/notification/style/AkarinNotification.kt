package com.github.epsilon.elements.impl.notification.style

import com.github.epsilon.elements.HudModule
import com.github.epsilon.elements.impl.ModuleList
import com.github.epsilon.elements.impl.notification.Notification
import com.github.epsilon.elements.impl.notification.Notifications
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.render.animation.Easing
import java.awt.Color
import kotlin.math.roundToInt

/**
 * The compact translucent notification style from Kiwi's Akarin HUD.
 */
class AkarinNotification(private val owner: Notifications) {

    private val active: Setting.Dependency = { owner.isStyle(Notifications.Style.Akarin) }

    private val backgroundBlur by owner.boolSetting("Akarin Background Blur", true, dependency = active)
    private val blurStrength by owner.doubleSetting(
        "Akarin Blur Strength", 15.0, 1.0, 30.0, 1.0,
        dependency = { active.check() && backgroundBlur }
    )
    private val backgroundAlpha by owner.intSetting("Akarin Background Alpha", 80, 0, 255, 1, dependency = active)
    private val radius by owner.doubleSetting("Akarin Radius", CARD_RADIUS.toDouble(), 0.0, 14.0, 0.5, dependency = active)
    private val displayTimeMs by owner.intSetting("Akarin Display Time", 800, 500, 10000, 100, dependency = active)

    fun getDisplayTime(): Int {
        return displayTimeMs
    }

    fun height(scale: Float): Float {
        return CARD_HEIGHT * scale
    }

    fun totalHeight(count: Int, scale: Float): Float {
        if (count <= 0) return height(scale)
        return (CARD_HEIGHT * count + ENTRY_GAP * (count - 1)) * scale
    }

    fun width(metrics: TextRenderer, entries: List<Notification>, scale: Float, fontScale: Float): Float {
        var maxWidth = MIN_WIDTH * scale
        val titleScale = titleScale(scale, fontScale)
        val statusScale = statusScale(scale, fontScale)
        for (notification in entries) {
            maxWidth = maxOf(maxWidth, TEXT_X * scale
                        + maxOf(metrics.getWidth("Module", titleScale), metrics.getWidth(statusText(notification), statusScale))
                        + RIGHT_PADDING * scale)
        }
        return maxWidth
    }

    fun isVisible(notification: Notification): Boolean {
        return animationProgress(notification) > 0.001f
    }

    fun render(
        scope: UiTree.Scope,
        metrics: TextRenderer,
        entries: List<Notification>,
        boundsX: Float,
        boundsY: Float,
        boundsWidth: Float,
        scale: Float,
        fontScale: Float,
        horizontalAnchor: HudModule.HorizontalAnchor,
        verticalAnchor: HudModule.VerticalAnchor,
    ) {
        val cardHeight = height(scale)
        val gap = ENTRY_GAP * scale
        val totalHeight = totalHeight(entries.size, scale)
        val stackUp = verticalAnchor == HudModule.VerticalAnchor.Bottom
        var targetY = if (stackUp) boundsY + totalHeight - cardHeight else boundsY
        val cardRadius = radius.toFloat() * scale

        for (notification in entries) {
            val progress = animationProgress(notification)
            if (progress <= 0.001f) continue

            val cardWidth = width(metrics, listOf(notification), scale, fontScale)
            val baseX = when (horizontalAnchor) {
                HudModule.HorizontalAnchor.Right -> boundsX + boundsWidth - cardWidth
                HudModule.HorizontalAnchor.Center -> boundsX + (boundsWidth - cardWidth) * 0.5f
                HudModule.HorizontalAnchor.Left -> boundsX
            }
            renderToast(
                scope, metrics, notification, baseX, targetY, cardWidth, cardHeight,
                cardRadius, scale, fontScale, horizontalAnchor, progress
            )
            targetY += if (stackUp) -(cardHeight + gap) else cardHeight + gap
        }
    }

    private fun renderToast(
        scope: UiTree.Scope,
        metrics: TextRenderer,
        notification: Notification,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cardRadius: Float,
        scale: Float,
        fontScale: Float,
        horizontalAnchor: HudModule.HorizontalAnchor,
        progress: Float,
    ) {
        val eased = Easing.EASE_OUT_CUBIC.function.apply(progress)
        val direction = if (horizontalAnchor == HudModule.HorizontalAnchor.Left) -1.0f else 1.0f
        val actualX = x + direction * (1.0f - eased) * (width + SLIDE_MARGIN * scale)
        val alpha = ((255.0f * eased).roundToInt().toLong()).coerceIn((0).toLong(), (255).toLong()).toInt()

        if (backgroundBlur) {
            BlurShader.render(actualX, y, width, height, cardRadius, blurStrength.toFloat() * scale)
        }

        scope.roundRect(
            actualX, y, width, height, cardRadius,
            Color(8, 12, 22, (backgroundAlpha * eased).roundToInt())
        )

        val topColor = ModuleList.getThemeColor(0, 0L)
        val bottomColor = ModuleList.getThemeColor(1, 1L)
        val titleScale = titleScale(scale, fontScale)
        val titleHeight = metrics.getHeight(titleScale)
        val stripeY = y + 5.0f * scale
        val stripeHeight = maxOf(4.0f * scale, titleHeight * 0.68f)
        val stripeGlow = withAlpha(topColor, (alpha * 0.13f).roundToInt())
        scope.roundRect(
            actualX - 0.2f * scale, stripeY - 1.5f * scale,
            (STRIPE_WIDTH + 1.5f) * scale, stripeHeight + 3.0f * scale, 2.0f * scale, stripeGlow
        )
        scope.roundRectVerticalGradient(
            actualX, stripeY, STRIPE_WIDTH * scale, stripeHeight,
            STRIPE_WIDTH * 0.5f * scale, withAlpha(topColor, alpha), withAlpha(bottomColor, alpha)
        )

        val textX = actualX + TEXT_X * scale
        scope.text("Module", textX, y + 4.5f * scale, titleScale, Color(244, 246, 250, alpha))
        val statusScale = statusScale(scale, fontScale)
        scope.scissor(
            actualX, y, width, height
        ) { textScope ->
            textScope.text(
                statusText(notification), textX, y + 15.5f * scale, statusScale,
                Color(244, 246, 250, (alpha * (160.0f / 255.0f)).roundToInt())
            )
        }
    }

    private fun animationProgress(notification: Notification): Float {
        val enter = if (notification.shouldSkipIntroAnim()) 1.0f
        else (notification.elapsedTime / TRANSITION_DURATION.toFloat()).coerceIn(0.0f, 1.0f)
        val exitTime = notification.exitTime
        if (exitTime < 0L) return enter
        return enter * (1.0f - (exitTime / TRANSITION_DURATION.toFloat()).coerceIn(0.0f, 1.0f))
    }

    private fun statusText(notification: Notification): String {
        val title = notification.title
        val subTitle = notification.subTitle
        if (subTitle.isEmpty()) return title
        if (title.isEmpty()) return subTitle
        return "$subTitle $title"
    }

    private fun titleScale(scale: Float, fontScale: Float): Float {
        return maxOf(0.25f, fontScale * 0.95f * scale)
    }

    private fun statusScale(scale: Float, fontScale: Float): Float {
        return maxOf(0.25f, fontScale * scale)
    }

    private fun withAlpha(color: Color, alpha: Int): Color {
        return Color(color.red, color.green, color.blue, (alpha.toLong()).coerceIn(((0).toLong()).toLong(), ((255).toLong()).toLong()).toInt().toInt())
    }

    private fun withAlpha(color: Color, alpha: Float): Color {
        return withAlpha(color, (color.alpha * (alpha).coerceIn(0.0f, 1.0f)).roundToInt())
    }

    private companion object {

        private const val MIN_WIDTH = 110.0f
        private const val CARD_HEIGHT = 29.0f
        private const val CARD_RADIUS = 6.0f
        private const val TEXT_X = 6.0f
        private const val RIGHT_PADDING = 6.0f
        private const val STRIPE_WIDTH = 1.5f
        private const val ENTRY_GAP = 5.0f
        private const val SLIDE_MARGIN = 10.0f
        private const val TRANSITION_DURATION = 200L

    }

}
