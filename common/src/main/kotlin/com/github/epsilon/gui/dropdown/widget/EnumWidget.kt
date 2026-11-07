package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import java.awt.Color
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class EnumWidget(setting: EnumSetting<*>) : SettingWidget<EnumSetting<*>>(setting) {

    private val expandAnim: Animation = Animation(Easing.DECELERATE, DropdownTheme.ANIM_EXPAND)
    private val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)
    private var expanded: Boolean by mutableStateOf<Boolean>(false)

    override fun getHeight(): Float {
        expandAnim.run(if (shouldExpand()) 1.0f else 0.0f)
        return getCollapsedHeight() + getExpandedTotalHeight() * expandAnim.value
    }

    override fun UiScope.draw(mouseX: Int, mouseY: Int) {
        val expand = updateExpandProgress()
        val hover = updateHoverProgress(isFieldHovered(mouseX.toDouble(), mouseY.toDouble()))
        val fieldX = getLocalFieldX()
        val fieldY = getLocalFieldY()
        val fieldW = getFieldWidth()

        Text(
            setting.displayName,
            DropdownTheme.SETTING_PADDING_X,
            1.0f,
            DropdownTheme.SETTING_TEXT_SCALE,
            if (setting.isCurrentValueSupported()) DropdownTheme.settingLabel() else MD3Theme.TEXT_MUTED
        )

        if (!setting.isCurrentValueSupported()) {
            drawPlatformBadge(fieldX)
        }
        drawCurrentValueField(fieldX, fieldY, fieldW, hover, expand)

        if (expand > 0.001f && getHiddenModeCount() > 0) {
            drawExpandedOptions(mouseX, mouseY, fieldX, fieldW, expand)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isFieldHovered(mouseX, mouseY)) {
            return handleFieldClick(button)
        }

        if (expanded) {
            return handleExpandedClick(mouseX, mouseY)
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (expanded && keyCode == InputConstants.KEY_ESCAPE) {
            expanded = false
            SoundManager.playInUi(SoundKey.SETTINGS_CLOSE)
            DropdownScreen.react(ReisaDropdownCompanion.Action.CANCEL)
            return true
        }
        return false
    }

    private fun UiScope.drawCurrentValueField(
        fieldX: Float, fieldY: Float, fieldW: Float, hover: Float, expand: Float,
    ) {
        val supported = setting.isCurrentValueSupported()
        val background: Color = MD3Theme.filledFieldSurface(expanded, hover)
        val outline: Color = MD3Theme.filledFieldIndicator(expanded, hover)
        val textY = fieldY + (FIELD_HEIGHT - metrics.textHeight(FIELD_TEXT_SCALE)) * 0.5f
        val arrowCenterX = fieldX + fieldW - 10.0f
        val arrowCenterY = fieldY + FIELD_HEIGHT * 0.5f

        RoundRect(
            fieldX, fieldY, fieldW, FIELD_HEIGHT, FIELD_RADIUS,
            if (supported) background else MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, 255)
        )
        Outline(
            fieldX, fieldY, fieldW, FIELD_HEIGHT, FIELD_RADIUS, 0.7f,
            if (supported) outline else MD3Theme.OUTLINE_SOFT
        )
        Text(
            setting.translatedValue,
            fieldX + FIELD_TEXT_PADDING_X,
            textY,
            FIELD_TEXT_SCALE,
            if (supported) MD3Theme.filledFieldContent(expanded) else MD3Theme.TEXT_MUTED
        )
        Triangle(
            arrowCenterX, arrowCenterY, FIELD_ARROW_SIZE, expand,
            if (supported) DropdownTheme.expandArrow(expand) else MD3Theme.TEXT_MUTED
        )
    }

    private fun UiScope.drawExpandedOptions(
        mouseX: Int, mouseY: Int, fieldX: Float, fieldW: Float, expand: Float,
    ) {
        val listX = fieldX
        val listY = getLocalListY()
        val listH = getListHeight()
        val clipH = listH * expand
        val visibleBottom = listY + clipH

        drawExpandedBackground(listX, listY, fieldW, clipH)

        val hiddenModes = getHiddenModes()
        for (optionIndex in hiddenModes.indices) {
            drawOption(mouseX, mouseY, listX, fieldW, visibleBottom, optionIndex, hiddenModes[optionIndex])
        }
    }

    private fun getHoveredOption(mouseX: Double, mouseY: Double): Enum<*>? {
        val hiddenModes = getHiddenModes()
        for (optionIndex in hiddenModes.indices) {
            if (isOptionHovered(mouseX, mouseY, optionIndex)) {
                return hiddenModes[optionIndex]
            }
        }
        return null
    }

    private fun handleFieldClick(button: Int): Boolean {
        if ((button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_LEFT)
            && getHiddenModeCount() > 0
        ) {
            expanded = !expanded
            SoundManager.playInUi(if (expanded) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE)
            DropdownScreen.react(
                if (expanded) ReisaDropdownCompanion.Action.PANEL_OPEN else ReisaDropdownCompanion.Action.PANEL_CLOSE
            )
            return true
        }
        return expanded
    }

    private fun handleExpandedClick(mouseX: Double, mouseY: Double): Boolean {
        val mode = getHoveredOption(mouseX, mouseY)
        if (mode != null) {
            if (!setting.isModeSupportedUnchecked(mode)) {
                PlatformNoticeScreen.showOption(setting, mode)
                return true
            }
            setting.setMode(mode.name)
            expanded = false
            SoundManager.playInUi(SoundKey.SETTINGS_CLOSE)
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        expanded = false
        SoundManager.playInUi(SoundKey.SETTINGS_CLOSE)
        return false
    }

    private fun updateExpandProgress(): Float {
        expandAnim.run(if (shouldExpand()) 1.0f else 0.0f)
        return expandAnim.value
    }

    private fun updateHoverProgress(fieldHovered: Boolean): Float {
        hoverAnim.run(if (fieldHovered || expanded) 1.0f else 0.0f)
        return hoverAnim.value
    }

    private fun UiScope.drawExpandedBackground(listX: Float, listY: Float, fieldW: Float, clipH: Float) {
        RoundRect(listX, listY, fieldW, clipH, FIELD_RADIUS, DropdownTheme.settingSurface())
        Outline(listX, listY, fieldW, clipH, FIELD_RADIUS, 0.7f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 96))
    }

    private fun UiScope.drawOption(
        mouseX: Int, mouseY: Int,
        listX: Float, fieldW: Float, visibleBottom: Float, optionIndex: Int, mode: Enum<*>,
    ) {
        val optionY = getLocalOptionY(optionIndex)
        if (optionY >= visibleBottom) {
            return
        }

        val visibleHeight = minOf(OPTION_HEIGHT, visibleBottom - optionY)
        if (visibleHeight <= 0.0f) {
            return
        }

        val hovered = isOptionHovered(mouseX.toDouble(), mouseY.toDouble(), optionIndex)
        if (hovered) {
            RoundRect(
                listX + 1.5f,
                optionY,
                fieldW - 3.0f,
                visibleHeight,
                FIELD_RADIUS - 1.0f,
                MD3Theme.rowSurface(1.0f)
            )
        }

        val lineHeight = metrics.textHeight(OPTION_TEXT_SCALE)
        val textY = optionY + (OPTION_HEIGHT - lineHeight) * 0.5f
        if (textY + lineHeight > visibleBottom) {
            return
        }

        val alpha = ((visibleBottom - optionY) / OPTION_HEIGHT).coerceIn(0.0f, 1.0f)
        val supported = setting.isModeSupportedUnchecked(mode)
        var textColor: Color = if (!supported) {
            MD3Theme.TEXT_MUTED
        } else if (hovered) {
            MD3Theme.TEXT_PRIMARY
        } else {
            DropdownTheme.settingLabelMuted()
        }
        textColor = MD3Theme.withAlpha(
            textColor,
            ((textColor.alpha * alpha).toInt().toLong()).coerceIn(((0).toLong()).toLong(), ((255).toLong()).toLong())
                .toInt().toInt()
        )
        Text(
            setting.getTranslatedValueUnchecked(mode),
            listX + FIELD_TEXT_PADDING_X,
            textY,
            OPTION_TEXT_SCALE,
            textColor
        )
        if (!supported) {
            val badge = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName()
            val badgeScale = 0.48f
            // assist chip 固定 8px 左内边距，左右各留 8px 才能让文字居中。
            val badgeWidth = metrics.textWidth(badge, badgeScale) + 16.0f
            val badgeHeight = 11.0f
            val badgeX = listX + fieldW - badgeWidth - 8.0f
            val badgeY = optionY + (OPTION_HEIGHT - badgeHeight) * 0.5f
            Chip(
                UiRect(badgeX, badgeY, badgeWidth, badgeHeight), badge, badgeScale,
                MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255), MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null
            )
        }
    }

    private fun UiScope.drawPlatformBadge(trailingX: Float) {
        val label = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName()
        val scale = 0.5f
        val width = metrics.textWidth(label, scale) + 16.0f
        val height = 12.0f
        val x = maxOf(DropdownTheme.SETTING_PADDING_X, trailingX - width)
        val labelHeight = metrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)
        val y = 1.0f + (labelHeight - height) * 0.5f
        Chip(
            UiRect(x, y, width, height), label, scale,
            MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255), MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null
        )
    }

    private fun isFieldHovered(mouseX: Double, mouseY: Double): Boolean {
        return isHovered(mouseX, mouseY, getFieldX(), getFieldY(), getFieldWidth(), FIELD_HEIGHT)
    }

    private fun isOptionHovered(mouseX: Double, mouseY: Double, optionIndex: Int): Boolean {
        return isHovered(mouseX, mouseY, getFieldX(), getOptionY(optionIndex), getFieldWidth(), OPTION_HEIGHT)
    }

    private fun getCollapsedHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT - 1.0f + FIELD_HEIGHT
    }

    private fun getExpandedTotalHeight(): Float {
        val hiddenCount = getHiddenModeCount()
        if (hiddenCount <= 0) return 0.0f
        return LIST_GAP_Y + getListHeight()
    }

    private fun getListHeight(): Float {
        val hiddenCount = getHiddenModeCount()
        if (hiddenCount <= 0) return 0.0f
        return LIST_PADDING_Y * 2.0f + hiddenCount * OPTION_HEIGHT + maxOf(0, hiddenCount - 1) * OPTION_GAP
    }

    private fun getHiddenModeCount(): Int {
        return maxOf(0, modes().size - 1)
    }

    private fun getHiddenModes(): Array<Enum<*>> {
        val selected: Enum<*> = setting.value
        val modes = modes()
        val hiddenModes = arrayOfNulls<Enum<*>>(getHiddenModeCount())
        var hiddenIndex = 0
        for (mode in modes) {
            if (mode !== selected) {
                hiddenModes[hiddenIndex++] = mode
            }
        }
        @Suppress("UNCHECKED_CAST")
        return hiddenModes as Array<Enum<*>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun modes(): Array<Enum<*>> {
        return setting.modes as Array<Enum<*>>
    }

    private fun shouldExpand(): Boolean {
        return expanded && getHiddenModeCount() > 0
    }

    private fun getFieldX(): Float {
        return absoluteX(DropdownTheme.SETTING_PADDING_X)
    }

    private fun getFieldY(): Float {
        return absoluteY(DropdownTheme.SETTING_HEIGHT - 1.0f)
    }

    private fun getLocalFieldX(): Float {
        return DropdownTheme.SETTING_PADDING_X
    }

    private fun getLocalFieldY(): Float {
        return DropdownTheme.SETTING_HEIGHT - 1.0f
    }

    private fun getFieldWidth(): Float {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f
    }

    private fun getListY(): Float {
        return getFieldY() + FIELD_HEIGHT + LIST_GAP_Y
    }

    private fun getLocalListY(): Float {
        return getLocalFieldY() + FIELD_HEIGHT + LIST_GAP_Y
    }

    private fun getOptionY(optionIndex: Int): Float {
        return getListY() + LIST_PADDING_Y + optionIndex * (OPTION_HEIGHT + OPTION_GAP)
    }

    private fun getLocalOptionY(optionIndex: Int): Float {
        return getLocalListY() + LIST_PADDING_Y + optionIndex * (OPTION_HEIGHT + OPTION_GAP)
    }

    companion object {
        private const val FIELD_HEIGHT = 14.0f
        private const val FIELD_RADIUS = 5.0f
        private const val FIELD_TEXT_SCALE = 0.6f
        private const val FIELD_TEXT_PADDING_X = 6.0f
        private const val FIELD_ARROW_SIZE = 3.0f
        private const val LIST_GAP_Y = 3.0f
        private const val LIST_PADDING_Y = 2.0f
        private const val OPTION_HEIGHT = 12.0f
        private const val OPTION_GAP = 1.0f
        private const val OPTION_TEXT_SCALE = 0.55f
    }

}
