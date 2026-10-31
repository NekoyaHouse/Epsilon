package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class ButtonSettingRow(private val buttonSetting: ButtonSetting) : SettingRow<ButtonSetting>(buttonSetting) {

    private val hoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)

    init {
        hoverAnimation.startValue = 0.0f
    }

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f
        hoverAnimation.run(hoverProgress)

        val animatedHover = hoverAnimation.value

        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(animatedHover)
        )
        scope.text(buttonSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)
    }

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x, event.y) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        buttonSetting.value.run()
        return true
    }

    override fun hasActiveAnimation(): Boolean = !hoverAnimation.isFinished

}
