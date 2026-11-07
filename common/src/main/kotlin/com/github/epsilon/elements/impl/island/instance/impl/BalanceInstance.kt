package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.elements.impl.island.IslandPalette
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.RoundRectHorizontalGradient
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.Circle
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import java.util.function.Supplier
import kotlin.math.roundToInt

class BalanceInstance(
    private val balanceSupplier: Supplier<Long>,
    private val maxBalanceSupplier: Supplier<Long>,
    private val textRendererSupplier: Supplier<TextRenderer>,
    pattern: LCPattern,
) : LandInstance(pattern, 2) {

    private val progressAnim = Animation(Easing.DECELERATE, 200)

    override fun update() {
        targetRadius = 0.5f

        val balance = maxOf(0L, balanceSupplier.get())
        val maxBalance = maxOf(1L, maxBalanceSupplier.get())

        val textRenderer = textRendererSupplier.get()
        val titleWidth = textRenderer.getWidth(TITLE, TEXT_SCALE)
        val valueWidth = textRenderer.getWidth(balance.toString() + "ms", TEXT_SCALE)
        val textHeight = textRenderer.getHeight(TEXT_SCALE)

        targetWidth = maxOf(100f, PADDING * 2f + titleWidth + GAP + valueWidth)
        targetHeight = PADDING * 2f + textHeight + GAP + BAR_HEIGHT

        progressAnim.run((balance.toFloat() / maxBalance).coerceIn(0.0f, 1.0f))
    }

    override fun draw(scope: UiScope, width: Float, height: Float) {
        if (width < 20f || height < 10f) return

        val textRenderer = textRendererSupplier.get()
        val balance = maxOf(0L, balanceSupplier.get())
        val valueText = balance.toString() + "ms"

        val titleWidth = textRenderer.getWidth(TITLE, TEXT_SCALE)
        val valueWidth = textRenderer.getWidth(valueText, TEXT_SCALE)
        val textHeight = textRenderer.getHeight(TEXT_SCALE)

        val barWidth = maxOf(1f, width - PADDING * 2f)
        val barY = height - PADDING - BAR_HEIGHT
        val textX = ((width - (titleWidth + GAP + valueWidth)) / 2f).roundToInt().toFloat()
        val textY = maxOf(PADDING, (barY - PADDING - textHeight) / 2f + PADDING * 0.5f)

        scope.Text(TITLE, textX, textY, TEXT_SCALE, fade(IslandPalette.TEXT_PRIMARY))
        scope.Text(valueText, textX + titleWidth + GAP, textY, TEXT_SCALE, fade(IslandPalette.ACCENT))

        val barRadius = BAR_HEIGHT * 0.5f
        scope.RoundRect(PADDING, barY, barWidth, BAR_HEIGHT, barRadius, fade(IslandPalette.TRACK))

        val progress = progressAnim.value.coerceIn(0f, 1f)
        if (progress > 0f) {
            scope.RoundRectHorizontalGradient(
                PADDING, barY, barWidth * progress, BAR_HEIGHT, barRadius,
                fade(IslandPalette.ACCENT), fade(IslandPalette.ACCENT_ALT)
            )
            scope.Circle(PADDING + barWidth * progress, barY + barRadius, 1.4f, 1.2f, fade(IslandPalette.ACCENT))
        }
    }

    companion object {

        private const val PADDING = 6f
        private const val BAR_HEIGHT = 4f
        private const val TEXT_SCALE = 1.0f
        private const val GAP = 5f
        private const val TITLE = "Timer Balance"

    }

}
