package com.github.epsilon.elements.impl.notification.style

import com.github.epsilon.elements.HudModule
import com.github.epsilon.elements.impl.notification.Notification
import com.github.epsilon.elements.impl.notification.Notifications
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.ClipIf
import com.github.epsilon.gui.lib.Rect
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.utils.render.animation.Easing
import java.awt.Color
import kotlin.math.roundToInt

class NVIDIANotification(private val owner: Notifications) {

    private enum class RenderStage {
        ENTER_BAR,
        ENTER_CONTENT,
        SHOW,
        EXIT_CONTENT,
        EXIT_BAR,
        HIDDEN,
    }

    private val active: () -> Boolean = { owner.isStyle(Notifications.Style.NVIDIA) }

    private val subtitleYOffset by owner.doubleSetting("Subtitle Y Offset", 0.4, -10.0..20.0, 0.1, { active() })
    private val boxWidth by owner.intSetting("Width", 120, 80..300, 1, { active() })
    private val boxHeight by owner.intSetting("Height", 30, 24..80, 1, { active() })
    private val backgroundAlpha by owner.intSetting("Background Alpha", 145, 0..255, 1, { active() })
    private val displayTimeMs by owner.intSetting("Display Time", 2000, 500..5000, 100, { active() })

    fun getDisplayTime(): Int {
        return displayTimeMs
    }

    /**
     * 布局阶段：统计本帧通知栈并更新元素尺寸。
     *
     * @return 是否有可绘制内容
     */
    fun measure(previewNotification: Notification?): Boolean {
        val scale = owner.getScale()
        val toastHeight = boxHeight * scale
        val spacing = toastHeight + ENTRY_GAP * scale
        var count = 0
        for (notification in NotificationManager.getNotifications()) {
            val frame = getRenderFrame(notification, spacing)
            if (frame.stage == RenderStage.HIDDEN) continue
            count++
        }
        if (count == 0 && previewNotification == null) return false

        owner.setNotificationBounds(boxWidth * scale, toastHeight)
        return true
    }

    /**
     * 绘制通知栈；坐标相对元素作用域原点（水平方向贴元素左侧，垂直方向按锚点堆叠）。
     */
    fun draw(scope: UiScope, metrics: TextRenderer, previewNotification: Notification?) {
        val scale = owner.getScale()
        val textScale = owner.getFontScale() * scale
        val anchorWidth = boxWidth * scale
        val toastHeight = boxHeight * scale
        val spacing = toastHeight + ENTRY_GAP * scale
        val bgAlpha = backgroundAlpha

        val entries: MutableList<RenderEntry> = ArrayList()
        var totalHeight = 0.0f
        for (notification in NotificationManager.getNotifications()) {
            val frame = getRenderFrame(notification, spacing)
            if (frame.stage == RenderStage.HIDDEN) continue
            totalHeight += frame.occupiedHeight
            entries.add(RenderEntry(notification, frame))
        }

        if (entries.isEmpty() && previewNotification != null) {
            totalHeight = spacing
            entries.add(RenderEntry(previewNotification, RenderFrame(RenderStage.SHOW, 1.0f, spacing)))
        }
        if (entries.isEmpty()) return

        val resolvedHeight = maxOf(toastHeight, totalHeight)
        // 堆叠向上时从元素底边往上排；其余从元素顶边往下排
        var currentY = if (owner.verticalAnchor == HudModule.VerticalAnchor.Bottom) {
            scope.height - resolvedHeight
        } else {
            0.0f
        }
        for (entry in entries) {
            renderNotification(
                scope, metrics, entry.notification, entry.frame, 0.0f, currentY,
                anchorWidth, anchorWidth, toastHeight, scale, textScale, bgAlpha
            )
            currentY += entry.frame.occupiedHeight
        }
    }

    private fun getRenderFrame(notification: Notification, occupiedHeight: Float): RenderFrame {
        val elapsedTime = notification.elapsedTime
        if (!notification.shouldSkipIntroAnim()) {
            if (elapsedTime <= 300L) {
                val progress = Easing.EASE_OUT_CUBIC.function.apply(elapsedTime / 300.0f)
                return RenderFrame(RenderStage.ENTER_BAR, progress, occupiedHeight * progress)
            }
            if (elapsedTime <= 500L) {
                val progress = Easing.EASE_OUT_CUBIC.function.apply((elapsedTime - 300L) / 200.0f)
                return RenderFrame(RenderStage.ENTER_CONTENT, progress, occupiedHeight)
            }
        }

        val exitTime = notification.exitTime
        if (exitTime < 0L) return RenderFrame(RenderStage.SHOW, 1.0f, occupiedHeight)
        if (exitTime <= 200L) {
            val progress = 1.0f - Easing.EASE_OUT_CUBIC.function.apply(exitTime / 200.0f)
            return RenderFrame(RenderStage.EXIT_CONTENT, progress, occupiedHeight)
        }
        if (exitTime <= 500L) {
            val progress = 1.0f - Easing.EASE_OUT_CUBIC.function.apply((exitTime - 200L) / 300.0f)
            return RenderFrame(RenderStage.EXIT_BAR, progress, occupiedHeight * progress)
        }
        return RenderFrame(RenderStage.HIDDEN, 0.0f, 0.0f)
    }

    private fun renderNotification(
        scope: UiScope,
        metrics: TextRenderer,
        notification: Notification,
        frame: RenderFrame,
        x: Float,
        y: Float,
        anchorWidth: Float,
        boxWidth: Float,
        boxHeight: Float,
        scale: Float,
        textScale: Float,
        bgAlpha: Int,
    ) {
        when (frame.stage) {
            RenderStage.ENTER_BAR, RenderStage.EXIT_BAR ->
                renderStage1(scope, notification, x, y, anchorWidth, boxWidth, boxHeight, frame.progress)

            RenderStage.ENTER_CONTENT, RenderStage.EXIT_CONTENT, RenderStage.SHOW ->
                renderStage2(
                    scope,
                    metrics,
                    notification,
                    x,
                    y,
                    boxWidth,
                    boxHeight,
                    scale,
                    textScale,
                    bgAlpha,
                    frame.progress
                )

            RenderStage.HIDDEN -> {
            }
        }
    }

    private fun renderStage1(
        scope: UiScope,
        notification: Notification,
        x: Float,
        y: Float,
        anchorWidth: Float,
        boxWidth: Float,
        boxHeight: Float,
        progress: Float,
    ) {
        val left = owner.horizontalAnchor == HudModule.HorizontalAnchor.Left
        val width = if (left) boxWidth * progress else boxWidth - anchorWidth * (1.0f - progress)
        val renderX = if (left) x else x + boxWidth - width
        scope.Rect(renderX, y, width, boxHeight, notification.mode.getColor())
    }

    private fun renderStage2(
        scope: UiScope,
        metrics: TextRenderer,
        notification: Notification,
        x: Float,
        y: Float,
        boxWidth: Float,
        boxHeight: Float,
        scale: Float,
        textScale: Float,
        bgAlpha: Int,
        progress: Float,
    ) {
        scope.Rect(x, y, boxWidth, boxHeight, Color(0, 0, 0, bgAlpha))
        val clip = textExceedsBox(metrics, notification, boxWidth, boxHeight, scale, textScale)
        scope.ClipIf(
            clip, x, y, boxWidth, boxHeight
        ) {
            renderText(
                this,
                metrics,
                notification,
                x,
                y,
                boxWidth,
                boxHeight,
                scale,
                textScale,
                (255.0f * progress).roundToInt()
            )
        }
        val accentWidth = ACCENT_BAR_WIDTH * scale + (boxWidth - ACCENT_BAR_WIDTH * scale) * (1.0f - progress)
        val accentX = if (owner.horizontalAnchor == HudModule.HorizontalAnchor.Left) x + boxWidth - accentWidth else x
        scope.Rect(accentX, y, accentWidth, boxHeight, notification.mode.getColor())
    }

    private fun renderText(
        scope: UiScope,
        metrics: TextRenderer,
        notification: Notification,
        x: Float,
        y: Float,
        boxWidth: Float,
        boxHeight: Float,
        scale: Float,
        desiredTextScale: Float,
        alpha: Int,
    ) {
        val hasSubtitle = notification.subTitle.isNotEmpty()
        val fittedScale = getFittedTextScale(metrics, notification, boxWidth, scale, desiredTextScale)
        val subtitleScale = fittedScale * SUBTITLE_SCALE
        val lineGap = LINE_GAP * fittedScale + subtitleYOffset.toFloat() * scale
        val titleHeight = metrics.getHeight(fittedScale)
        val subtitleHeight = if (hasSubtitle) metrics.getHeight(subtitleScale) else 0.0f
        val contentHeight = titleHeight + subtitleHeight + (if (hasSubtitle) lineGap else 0.0f)
        val textX = x + if (owner.horizontalAnchor == HudModule.HorizontalAnchor.Left) TEXT_PADDING * scale
        else (ACCENT_BAR_WIDTH + TEXT_PADDING) * scale
        val titleY = y + (boxHeight - contentHeight) / 2.0f

        scope.Text(notification.title, textX, titleY, fittedScale, Color(255, 255, 255, alpha))
        if (hasSubtitle) {
            scope.Text(
                notification.subTitle, textX, titleY + titleHeight + lineGap, subtitleScale,
                notification.mode.getColor((alpha * 0.86f).roundToInt())
            )
        }
    }

    private fun getFittedTextScale(
        metrics: TextRenderer,
        notification: Notification,
        boxWidth: Float,
        scale: Float,
        desiredTextScale: Float,
    ): Float {
        val maxWidth = maxOf(
            metrics.getWidth(notification.title, desiredTextScale),
            metrics.getWidth(notification.subTitle, desiredTextScale * SUBTITLE_SCALE)
        )
        val availableWidth = maxOf(1.0f, boxWidth - (TEXT_PADDING * 2.0f + ACCENT_BAR_WIDTH) * scale)
        return maxOf(0.35f, desiredTextScale * (if (maxWidth > availableWidth) availableWidth / maxWidth else 1.0f))
    }

    private fun textExceedsBox(
        metrics: TextRenderer,
        notification: Notification,
        boxWidth: Float,
        boxHeight: Float,
        scale: Float,
        desiredTextScale: Float,
    ): Boolean {
        val fittedScale = getFittedTextScale(metrics, notification, boxWidth, scale, desiredTextScale)
        val subtitleScale = fittedScale * SUBTITLE_SCALE
        val maxWidth = maxOf(
            metrics.getWidth(notification.title, fittedScale),
            metrics.getWidth(notification.subTitle, subtitleScale)
        )
        val availableWidth = maxOf(1.0f, boxWidth - (TEXT_PADDING * 2.0f + ACCENT_BAR_WIDTH) * scale)
        val titleHeight = metrics.getHeight(fittedScale)
        if (notification.subTitle.isEmpty()) return maxWidth > availableWidth || titleHeight > boxHeight
        val subtitleHeight = metrics.getHeight(subtitleScale)
        val lineGap = LINE_GAP * fittedScale + subtitleYOffset.toFloat() * scale
        val contentHeight = titleHeight + lineGap + subtitleHeight
        return maxWidth > availableWidth || (boxHeight - contentHeight) * 0.5f < 0.0f ||
                (boxHeight - contentHeight) * 0.5f + contentHeight > boxHeight
    }

    private data class RenderFrame(val stage: RenderStage, val progress: Float, val occupiedHeight: Float)

    private data class RenderEntry(val notification: Notification, val frame: RenderFrame)

    private companion object {

        private const val ACCENT_BAR_WIDTH = 2.4f
        private const val TEXT_PADDING = 6.0f
        private const val ENTRY_GAP = 3.0f
        private const val LINE_GAP = 1.8f
        private const val SUBTITLE_SCALE = 0.92f

    }

}
