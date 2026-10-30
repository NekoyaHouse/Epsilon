package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.Mth

class BoolWidget(setting: BoolSetting) : SettingWidget<BoolSetting>(setting) {

    private val toggleAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE)
    private val knobBounceAnim: Animation = Animation(Easing.EASE_OUT_ELASTIC, 450L)
    private val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)

    init {
        val initial = if (setting.value) 1.0f else 0.0f
        toggleAnim.startValue = initial
        knobBounceAnim.startValue = initial
    }

    override fun getHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        val supported = setting.isPlatformSupported()
        val target = if (setting.value) 1.0f else 0.0f
        toggleAnim.run(target)
        knobBounceAnim.run(target)
        val t = if (supported) toggleAnim.value else 0.0f
        val bounce = if (supported) knobBounceAnim.value else 0.0f

        val sw = SWITCH_WIDTH
        val sh = SWITCH_HEIGHT
        val sx = width - DropdownTheme.SETTING_PADDING_X - sw
        val sy = (getHeight() - sh) * 0.5f

        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), absoluteX(sx - 2), absoluteY(sy - 2), sw + 4, sh + 4)
        hoverAnim.run(if (supported && hovered) 1.0f else 0.0f)
        val hoverProgress = hoverAnim.value

        scope.text(
            setting.displayName, DropdownTheme.SETTING_PADDING_X,
            (getHeight() - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
            DropdownTheme.SETTING_TEXT_SCALE,
            if (supported) DropdownTheme.settingLabel() else MD3Theme.TEXT_MUTED
        )

        if (!supported) {
            drawPlatformBadge(scope, textMetrics, sx - 6.0f)
        }

        scope.roundRect(sx, sy, sw, sh, SWITCH_RADIUS, MD3Theme.switchTrack(t))

        val outlineW = MD3Theme.switchTrackOutlineWidth(t)
        if (outlineW > 0.01f) {
            scope.outline(sx, sy, sw, sh, SWITCH_RADIUS, outlineW, MD3Theme.switchTrackOutline(t, hoverProgress))
        }

        val knobSize = Mth.lerp(Mth.clamp(t, 0.0f, 1.0f), KNOB_SIZE_OFF, KNOB_SIZE_ON)
        val stretchFactor = 4.0f * t * (1.0f - t)
        val knobW = knobSize + KNOB_STRETCH * stretchFactor
        val inset = Mth.lerp(t, KNOB_INSET_OFF, KNOB_INSET_ON)
        val knobMinX = sx + inset + knobW * 0.5f
        val knobMaxX = sx + sw - inset - knobW * 0.5f
        val knobCx = Mth.lerp(bounce, knobMinX, knobMaxX)
        val knobCy = sy + sh * 0.5f

        if (hoverProgress > 0.02f) {
            val haloX = knobCx - STATE_LAYER_SIZE * 0.5f
            val haloY = knobCy - STATE_LAYER_SIZE * 0.5f
            scope.roundRect(haloX, haloY, STATE_LAYER_SIZE, STATE_LAYER_SIZE, STATE_LAYER_SIZE * 0.5f, MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, 18))
        }

        val knobRadius = knobSize * 0.5f
        scope.roundRect(knobCx - knobW * 0.5f, knobCy - knobSize * 0.5f, knobW, knobSize, knobRadius, MD3Theme.switchKnob(t))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            val sw = SWITCH_WIDTH
            val sh = SWITCH_HEIGHT
            val sx = absoluteX(width - DropdownTheme.SETTING_PADDING_X - sw)
            val sy = absoluteY((getHeight() - sh) * 0.5f)
            if (isHovered(mouseX, mouseY, sx - 2, sy - 2, sw + 4, sh + 4)) {
                if (!setting.isPlatformSupported()) {
                    PlatformNoticeScreen.show(setting)
                    return true
                }
                val newValue = !setting.value
                setting.value = newValue
                SoundManager.playInUi(if (newValue) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE)
                DropdownScreen.react(
                    if (newValue) ReisaDropdownCompanion.Action.TOGGLE_ON else ReisaDropdownCompanion.Action.TOGGLE_OFF
                )
                return true
            }
        }
        return false
    }

    private fun drawPlatformBadge(scope: UiTree.Scope, textMetrics: UiTextMetrics, trailingX: Float) {
        val label = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName()
        val scale = 0.5f
        // assist chip 固定 8px 左内边距，左右各留 8px 才能让文字居中。
        val width = textMetrics.textWidth(label, scale) + 16.0f
        val height = 12.0f
        val x = Math.max(DropdownTheme.SETTING_PADDING_X, trailingX - width)
        val y = (getHeight() - SWITCH_HEIGHT) * 0.5f + (SWITCH_HEIGHT - height) * 0.5f
        scope.chip(
            UiRect(x, y, width, height), label, scale,
            MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255), MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null
        )
    }

    companion object {
        private const val SWITCH_WIDTH = 22.0f
        private const val SWITCH_HEIGHT = 12.0f
        private const val SWITCH_RADIUS = 6.0f

        private const val KNOB_SIZE_OFF = 6.0f
        private const val KNOB_SIZE_ON = 9.0f
        private const val KNOB_INSET_OFF = 3.5f
        private const val KNOB_INSET_ON = 2.0f
        private const val STATE_LAYER_SIZE = 16.0f
        private const val KNOB_STRETCH = 3.5f
    }

}
