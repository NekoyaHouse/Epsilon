package com.github.epsilon.elements.impl.notification.style

import com.github.epsilon.elements.HudModule
import com.github.epsilon.elements.impl.notification.Notification
import com.github.epsilon.elements.impl.notification.NotificationMode
import com.github.epsilon.elements.impl.notification.Notifications
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.render.animation.Easing
import java.awt.Color
import java.util.*
import kotlin.math.*

class QuantumNotification(private val owner: Notifications) {

    private val active: Setting.Dependency = { owner.isStyle(Notifications.Style.Quantum) }

    private val states: IdentityHashMap<Notification, SpringState> = IdentityHashMap()
    private val backgroundColor by owner.colorSetting("Background Color", Color(0, 0, 0, 50), dependency = active)
    private val backgroundBlur by owner.boolSetting("Background Blur", true, dependency = active)
    private val blurStrength by owner.doubleSetting(
        "Blur Strength", 5.0, 1.0, 20.0, 1.0,
        dependency = { active.check() && backgroundBlur }
    )
    private val drawShadow by owner.boolSetting("Drop Shadow", true, dependency = active)
    private val shadowBlur by owner.doubleSetting(
        "Shadow Blur", 10.0, 2.0, 32.0, 1.0,
        dependency = { active.check() && drawShadow }
    )
    private val shadowColor by owner.colorSetting(
        "Shadow Color", Color(255, 255, 255, 110),
        dependency = { active.check() && drawShadow }
    )
    private val displayTimeMs by owner.intSetting("Quantum Display Time", 5000, 500, 10000, 100, dependency = active)

    fun getDisplayTime(): Int {
        return displayTimeMs
    }

    fun width(scale: Float): Float {
        return TOAST_WIDTH * scale
    }

    fun height(scale: Float): Float {
        return TOAST_HEIGHT * scale
    }

    fun totalHeight(count: Int, scale: Float): Float {
        if (count <= 0) return height(scale)
        return (TOAST_HEIGHT * count + TOAST_GAP * (count - 1)) * scale
    }

    fun clear() {
        states.clear()
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
        scale: Float,
        fontScale: Float,
        horizontalAnchor: HudModule.HorizontalAnchor,
        verticalAnchor: HudModule.VerticalAnchor,
    ) {
        states.keys.removeAll { existing -> entries.none { entry -> entry === existing } }

        val toastWidth = width(scale)
        val toastHeight = height(scale)
        val gap = TOAST_GAP * scale
        val totalHeight = totalHeight(entries.size, scale)
        val stackUp = verticalAnchor == HudModule.VerticalAnchor.Bottom
        var targetY = if (stackUp) boundsY + totalHeight - toastHeight else boundsY

        for (notification in entries) {
            val progress = animationProgress(notification)
            if (progress <= 0.001f) continue

            val state = states[notification] ?: SpringState().also { states[notification] = it }
            val renderY = state.update(targetY)
            renderToast(
                scope, metrics, notification, boundsX, renderY, toastWidth, toastHeight,
                scale, fontScale, horizontalAnchor, progress
            )
            targetY += if (stackUp) -(toastHeight + gap) else toastHeight + gap
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
        scale: Float,
        fontScale: Float,
        horizontalAnchor: HudModule.HorizontalAnchor,
        progress: Float,
    ) {
        val eased = Easing.SMOOTH_STEP.function.apply(progress)
        val direction = if (horizontalAnchor == HudModule.HorizontalAnchor.Left) -1.0f else 1.0f
        val actualX = x + direction * (1.0f - eased) * (width + SLIDE_MARGIN * scale)
        val alpha = ((255.0f * eased).roundToInt().toLong()).coerceIn((0).toLong(), (255).toLong()).toInt()

        if (backgroundBlur) {
            BlurShader.render(actualX, y, width, height, BORDER_RADIUS * scale, blurStrength.toFloat())
        }
        if (drawShadow) {
            scope.shadow(actualX, y, width, height, BORDER_RADIUS * scale, shadowBlur.toFloat() * scale, shadowColor)
        }
        scope.roundRect(
            actualX, y, width, height, BORDER_RADIUS * scale,
            withAlpha(backgroundColor, (backgroundColor.alpha * eased).roundToInt())
        )

        val iconX = actualX + PADDING_H * scale
        val iconY = y + (height - ICON_SIZE * scale) * 0.5f
        drawIcon(scope, metrics, notification.mode, iconX, iconY, scale, alpha)

        val contentX = iconX + ICON_SIZE * scale + CONTENT_GAP * scale
        val contentRight = actualX + width - PADDING_H * scale
        scope.scissor(
            actualX, y, width, height
        ) { textScope ->
            drawContent(textScope, metrics, notification, contentX, contentRight, y, height, scale, fontScale, alpha)
        }
    }

    private fun drawContent(
        scope: UiTree.Scope,
        metrics: TextRenderer,
        notification: Notification,
        x: Float,
        right: Float,
        y: Float,
        height: Float,
        scale: Float,
        fontScale: Float,
        alpha: Int,
    ) {
        val titleScale = fitTextScale(metrics, notification.title, fontScale * scale, right - x)
        val messageScale = fitTextScale(metrics, notification.subTitle, titleScale * 0.86f, right - x)
        val hasMessage = notification.subTitle.isNotEmpty()
        val titleHeight = metrics.getHeight(titleScale)
        val messageHeight = if (hasMessage) metrics.getHeight(messageScale) else 0.0f
        val lineGap = if (hasMessage) 1.5f * scale else 0.0f
        val contentHeight = titleHeight + lineGap + messageHeight
        val titleY = y + (height - contentHeight) * 0.5f

        scope.text(notification.title, x, titleY, titleScale, Color(255, 255, 255, alpha))
        if (hasMessage) {
            scope.text(
                notification.subTitle, x, titleY + titleHeight + lineGap, messageScale,
                Color(255, 255, 255, (alpha * 0.7f).roundToInt())
            )
        }
    }

    private fun drawIcon(
        scope: UiTree.Scope,
        metrics: TextRenderer,
        mode: NotificationMode,
        x: Float,
        y: Float,
        scale: Float,
        alpha: Int
    ) {
        val iconScale = ICON_SIZE * scale / 48.0f
        val centerX = x + ICON_SIZE * scale * 0.5f
        val centerY = y + ICON_SIZE * scale * 0.5f
        val time = System.nanoTime() / 1_000_000_000.0f
        val color = quantumColor(mode, alpha)

        when (mode) {
            NotificationMode.Success -> drawSuccess(scope, centerX, centerY, iconScale, time, color, alpha)
            NotificationMode.Info -> drawInfo(scope, centerX, centerY, iconScale, time, color, alpha)
            NotificationMode.Warning -> drawWarning(scope, metrics, centerX, centerY, iconScale, time, color, alpha)
            NotificationMode.Error -> drawError(scope, centerX, centerY, iconScale, time, color, alpha)
        }
    }

    private fun drawSuccess(
        scope: UiTree.Scope,
        cx: Float,
        cy: Float,
        s: Float,
        time: Float,
        color: Color,
        alpha: Int
    ) {
        drawDashedCircle(
            scope,
            cx,
            cy,
            22.0f * s,
            2.0f * s,
            16.0f,
            12.0f,
            0.0f,
            withAlpha(color, (alpha * 0.2f).roundToInt())
        )

        val phase = positiveModulo(time, 2.5f) / 2.5f
        if (phase < 0.4f) {
            val local = if (phase < 0.15f) phase / 0.15f else (phase - 0.15f) / 0.25f
            val ringScale = if (phase < 0.15f) 0.8f + 0.3f * local else 1.1f + 0.2f * local
            val ringAlpha = if (phase < 0.15f) 0.4f * local else 0.4f * (1.0f - local)
            scope.arc(
                cx,
                cy,
                18.0f * s * ringScale,
                maxOf(0.5f * s, 2.0f * s),
                0.0f,
                360.0f,
                true,
                withAlpha(color, (alpha * ringAlpha).roundToInt())
            )
        }

        val checkProgress = minOf(1.0f, positiveModulo(time, 3.0f) / 0.7f)
        val x1 = cx - 10.0f * s
        val x2 = cx - 3.0f * s
        val y2 = cy + 7.0f * s
        val x3 = cx + 10.0f * s
        val y3 = cy - 7.0f * s
        drawProgressLine(scope, x1, cy, x2, y2, 1.4f * s, checkProgress * 1.85f, color)
        drawProgressLine(scope, x2, y2, x3, y3, 1.4f * s, (checkProgress - 0.46f) * 1.85f, color)
    }

    private fun drawInfo(scope: UiTree.Scope, cx: Float, cy: Float, s: Float, time: Float, color: Color, alpha: Int) {
        scope.arc(cx, cy, 20.0f * s, 0.5f * s, 0.0f, 360.0f, false, withAlpha(color, (alpha * 0.2f).roundToInt()))
        scope.arc(cx, cy, 12.0f * s, 0.5f * s, 0.0f, 360.0f, false, withAlpha(color, (alpha * 0.1f).roundToInt()))

        val angle = positiveModulo(time, 4.0f) / 4.0f * 360.0f
        scope.gradientArc(
            cx,
            cy,
            15.5f * s,
            9.0f * s,
            angle - 90.0f,
            78.0f,
            false,
            angle,
            withAlpha(color, 0),
            withAlpha(color, (alpha * 0.12f).roundToInt()),
            withAlpha(color, (alpha * 0.38f).roundToInt())
        )

        val points =
            arrayOf(floatArrayOf(14.0f, -6.0f, 0.2f), floatArrayOf(-14.0f, 6.0f, 0.8f), floatArrayOf(4.0f, 16.0f, 1.4f))
        for (point in points) {
            val pointPhase = positiveModulo(time - point[2], 2.0f) / 2.0f
            val pulse = if (pointPhase < 0.5f) pointPhase * 2.0f else (1.0f - pointPhase) * 2.0f
            dot(
                scope,
                cx + point[0] * s,
                cy + point[1] * s,
                (0.75f + pulse * 0.45f) * s,
                withAlpha(color, (alpha * pulse).roundToInt())
            )
        }
        dot(scope, cx, cy, 1.5f * s, color)
    }

    private fun drawWarning(
        scope: UiTree.Scope,
        metrics: TextRenderer,
        cx: Float,
        cy: Float,
        s: Float,
        time: Float,
        color: Color,
        alpha: Int,
    ) {
        val renderScale = s * 48.0f / ICON_SIZE
        scope.arc(
            cx,
            cy,
            10.2f * renderScale,
            0.45f * renderScale,
            0.0f,
            360.0f,
            false,
            withAlpha(color, (alpha * 0.16f).roundToInt())
        )
        scope.arc(
            cx,
            cy,
            8.0f * renderScale,
            0.35f * renderScale,
            0.0f,
            360.0f,
            false,
            withAlpha(color, (alpha * 0.09f).roundToInt())
        )

        val sweep = 58.0f + 10.0f * (0.5f + 0.5f * sin((time * 1.4f).toDouble()).toFloat())
        val angle = positiveModulo(time * 38.0f, 360.0f)
        scope.gradientArc(
            cx,
            cy,
            11.0f * renderScale,
            1.15f * renderScale,
            angle,
            sweep,
            true,
            angle - 90.0f,
            withAlpha(color, 0),
            withAlpha(color, (alpha * 0.12f).roundToInt()),
            withAlpha(color, (alpha * 0.32f).roundToInt())
        )

        val pulse = positiveModulo(time, 2.8f) / 2.8f
        val pulseWave = if (pulse < 0.5f) pulse * 2.0f else (1.0f - pulse) * 2.0f
        scope.arc(
            cx, cy, (11.4f + pulseWave * 1.1f) * renderScale, 0.3f * renderScale, 0.0f, 360.0f, false,
            withAlpha(color, (alpha * (0.04f + pulseWave * 0.05f)).roundToInt())
        )

        val iconWidth = metrics.getWidth(IconChars.WARNING_AMBER, renderScale, StaticFontLoader.ICONS)
        val iconHeight = metrics.getHeight(renderScale, StaticFontLoader.ICONS)
        scope.text(
            IconChars.WARNING_AMBER,
            cx - iconWidth * 0.5f,
            cy - iconHeight * 0.5f,
            renderScale,
            color,
            StaticFontLoader.ICONS
        )
    }

    private fun drawError(scope: UiTree.Scope, cx: Float, cy: Float, s: Float, time: Float, color: Color, alpha: Int) {
        val phase = positiveModulo(time, 2.0f) / 2.0f
        if (phase < 0.6f) {
            val local = if (phase < 0.3f) phase / 0.3f else (phase - 0.3f) / 0.3f
            val ringScale = if (phase < 0.3f) 1.4f - 0.4f * local else 1.0f - 0.3f * local
            val ringAlpha = if (phase < 0.3f) 0.4f * local else 0.4f * (1.0f - local)
            drawDashedCircle(
                scope,
                cx,
                cy,
                20.0f * s * ringScale,
                1.0f * s,
                22.0f,
                18.0f,
                0.0f,
                withAlpha(color, (alpha * ringAlpha).roundToInt())
            )
        }

        val crossTime = positiveModulo(time, 2.0f)
        val first = ((crossTime - 0.1f) / 0.4f).coerceIn(0.0f, 1.0f)
        val second = ((crossTime - 0.2f) / 0.4f).coerceIn(0.0f, 1.0f)
        drawProgressLine(scope, cx - 8.0f * s, cy - 8.0f * s, cx + 8.0f * s, cy + 8.0f * s, 1.4f * s, first, color)
        drawProgressLine(scope, cx + 8.0f * s, cy - 8.0f * s, cx - 8.0f * s, cy + 8.0f * s, 1.4f * s, second, color)
    }

    private fun drawDashedCircle(
        scope: UiTree.Scope,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
        dashDegrees: Float,
        gapDegrees: Float,
        rotation: Float,
        color: Color,
    ) {
        val step = dashDegrees + gapDegrees
        var angle = 0.0f
        while (angle < 360.0f) {
            scope.arc(cx, cy, radius, stroke, rotation + angle, minOf(dashDegrees, 360.0f - angle), true, color)
            angle += step
        }
    }

    private fun drawProgressLine(
        scope: UiTree.Scope,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        progress: Float,
        color: Color,
    ) {
        val clampedProgress = (progress).coerceIn(0.0f, 1.0f)
        if (clampedProgress <= 0.0f) return
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val steps = maxOf(2, ceil((length / maxOf(radius, 0.35f)).toDouble()).toInt())
        val visibleSteps = maxOf(1, (steps * clampedProgress).roundToInt())
        for (i in 0 until visibleSteps) {
            val t = i / (steps - 1).toFloat()
            dot(scope, x1 + dx * t, y1 + dy * t, radius, color)
        }
    }

    private fun dot(scope: UiTree.Scope, centerX: Float, centerY: Float, radius: Float, color: Color) {
        scope.roundRect(centerX - radius, centerY - radius, radius * 2.0f, radius * 2.0f, radius, color)
    }

    private fun animationProgress(notification: Notification): Float {
        val enter = if (notification.shouldSkipIntroAnim()) 1.0f
        else (notification.elapsedTime / TRANSITION_DURATION.toFloat()).coerceIn(0.0f, 1.0f)
        val exitTime = notification.exitTime
        if (exitTime < 0L) return enter
        return 1.0f - (exitTime / TRANSITION_DURATION.toFloat()).coerceIn(0.0f, 1.0f)
    }

    private class SpringState {

        private var currentY: Float = Float.NaN
        private var velocityY: Float = 0f
        private var lastTime: Long = System.nanoTime()

        fun update(targetY: Float): Float {
            val now = System.nanoTime()
            if (!currentY.isFinite()) {
                currentY = targetY
                lastTime = now
                return currentY
            }

            val delta = minOf((now - lastTime) / 1_000_000_000.0f, 0.1f)
            lastTime = now
            val displacement = currentY - targetY
            val force = -200.0f * displacement - 25.0f * velocityY
            velocityY += force * delta
            currentY += velocityY * delta
            if (abs(displacement) < 0.5f && abs(velocityY) < 5.0f) {
                currentY = targetY
                velocityY = 0.0f
            }
            return currentY
        }

    }

    private companion object {

        private const val TOAST_WIDTH = 160.0f
        private const val TOAST_HEIGHT = 30.0f
        private const val BORDER_RADIUS = 9.0f
        private const val ICON_SIZE = 18.0f
        private const val PADDING_H = 8.0f
        private const val CONTENT_GAP = 6.0f
        private const val TOAST_GAP = 5.0f
        private const val SLIDE_MARGIN = 12.0f
        private const val TRANSITION_DURATION = 200L

        private fun fitTextScale(
            metrics: TextRenderer,
            text: String,
            desiredScale: Float,
            availableWidth: Float
        ): Float {
            if (text.isEmpty()) return desiredScale
            val width = metrics.getWidth(text, desiredScale)
            if (width <= availableWidth) return desiredScale
            return maxOf(0.35f, desiredScale * availableWidth / width)
        }

        private fun quantumColor(mode: NotificationMode, alpha: Int): Color {
            return when (mode) {
                NotificationMode.Success -> Color(0x00, 0xFF, 0xAA, alpha)
                NotificationMode.Info -> Color(0x00, 0xAA, 0xFF, alpha)
                NotificationMode.Warning -> Color(0xFF, 0xCC, 0x00, alpha)
                NotificationMode.Error -> Color(0xFF, 0x33, 0x66, alpha)
            }
        }

        private fun withAlpha(color: Color, alpha: Int): Color {
            return Color(
                color.red,
                color.green,
                color.blue,
                (alpha.toLong()).coerceIn(((0).toLong()).toLong(), ((255).toLong()).toLong()).toInt().toInt()
            )
        }

        private fun positiveModulo(value: Float, divisor: Float): Float {
            val result = value % divisor
            return if (result < 0.0f) result + divisor else result
        }

    }

}
