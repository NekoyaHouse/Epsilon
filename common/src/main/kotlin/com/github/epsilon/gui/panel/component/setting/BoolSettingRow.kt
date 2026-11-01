package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class BoolSettingRow(private val boolSetting: BoolSetting) : SettingRow<BoolSetting>(boolSetting) {

    private val hoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)
    private val toggleAnimation = Animation(Easing.EASE_OUT_ELASTIC, 620L)

    init {
        hoverAnimation.startValue = 0.0f
        toggleAnimation.startValue = if (boolSetting.value) 1.0f else 0.0f
    }

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val supported = boolSetting.isSupported()
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f
        val animatedHover = scope.animate(hoverAnimation, if (supported) hoverProgress else 0.0f)
        val toggleProgress = scope.animate(toggleAnimation, boolSetting.value)
        val switchBounds = getSwitchBounds(bounds).relativeTo(bounds)

        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height, MD3Theme.CARD_RADIUS.toFloat(),
            MD3Theme.rowSurface(if (supported) animatedHover else 0.0f)
        )
        scope.text(
            boolSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale,
            if (supported) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_MUTED
        )
        if (!supported) {
            LabelBadges.platformOnlyChip(scope, textRenderer, bounds, switchBounds, boolSetting.unsupportedLabel)
        }
        scope.toggle(switchBounds, if (supported) toggleProgress else 0.0f, animatedHover)
    }

    private fun getSwitchBounds(bounds: UiRect): UiRect = PanelElements.switchBounds(bounds)

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x, event.y) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        if (!boolSetting.isSupported()) {
            PlatformNoticeScreen.show(boolSetting)
            return true
        }
        boolSetting.value = !boolSetting.value
        SoundManager.playInUi(if (boolSetting.value) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE)
        return true
    }

    override fun hasActiveAnimation(): Boolean = !hoverAnimation.isFinished || !toggleAnimation.isFinished

}
