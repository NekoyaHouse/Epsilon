package com.github.epsilon.gui.screen.accounts

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import java.awt.Color

class AccountSortButton {

    private val hoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)

    init {
        hoverAnimation.startValue = 0.0f
    }

    fun measureWidth(textRenderer: TextRenderer, mode: AccountSortMode): Float {
        val iconWidth = textRenderer.getWidth(IconChars.SWAP_VERT, 1.0f, StaticFontLoader.ICONS)
        val labelWidth = textRenderer.getWidth(mode.getLabel(), LABEL_SCALE)
        return INSET + iconWidth + GAP + labelWidth + INSET
    }

    fun draw(
        scope: UiTree.Scope,
        bounds: UiRect,
        mouseX: Double,
        mouseY: Double,
        textRenderer: TextRenderer,
        mode: AccountSortMode,
    ) {
        val hovered = bounds.contains(mouseX, mouseY)
        val hoverProgress = scope.animate(hoverAnimation, hovered)

        scope.roundRect(bounds.x, bounds.y, bounds.width, bounds.height, MD3Theme.CONTROL_RADIUS.toFloat(), MD3Theme.SECONDARY_CONTAINER)
        if (hoverProgress > 0.01f) {
            scope.roundRect(
                bounds.x, bounds.y, bounds.width, bounds.height,
                MD3Theme.CONTROL_RADIUS.toFloat(),
                MD3Theme.stateLayer(MD3Theme.PRIMARY, hoverProgress, 34),
            )
        }

        val fg = MD3Theme.ON_SECONDARY_CONTAINER
        val iconWidth = textRenderer.getWidth(IconChars.SWAP_VERT, 1.0f, StaticFontLoader.ICONS)
        val iconHeight = textRenderer.getHeight(1.0f, StaticFontLoader.ICONS)
        scope.text(
            IconChars.SWAP_VERT,
            bounds.x + INSET,
            bounds.y + (bounds.height - iconHeight) / 2.0f,
            1.0f,
            fg,
            StaticFontLoader.ICONS,
        )

        val label = mode.getLabel()
        val labelHeight = textRenderer.getHeight(LABEL_SCALE)
        scope.text(label, bounds.x + INSET + iconWidth + GAP, bounds.y + (bounds.height - labelHeight) / 2.0f, LABEL_SCALE, fg)
    }

    fun hasActiveAnimations(): Boolean {
        return !hoverAnimation.isFinished
    }

    private companion object {

        private const val LABEL_SCALE = 0.75f
        private const val INSET = 8.0f
        private const val GAP = 5.0f

    }

}
