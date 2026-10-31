package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.Mth
import java.awt.Color
import kotlin.math.roundToInt

class ColorWidget(setting: ColorSetting) : SettingWidget<ColorSetting>(setting) {

    private val openAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_EXPAND)

    private val redField: DropdownTextField = DropdownTextField(3, { value -> value.matches(Regex("[0-9]")) })
    private val greenField: DropdownTextField = DropdownTextField(3, { value -> value.matches(Regex("[0-9]")) })
    private val blueField: DropdownTextField = DropdownTextField(3, { value -> value.matches(Regex("[0-9]")) })
    private val alphaField: DropdownTextField = DropdownTextField(3, { value -> value.matches(Regex("[0-9]")) })

    private var opened = false
    private var pickingSB = false
    private var pickingHue = false
    private var pickingChannel: Channel? = null
    private var pendingValue: Color? = null

    override fun getHeight(): Float {
        openAnim.run(if (opened) 1.0f else 0.0f)
        val channelCount = channels.size
        val expandedHeight = PICKER_HEIGHT + PICKER_TO_HUE_GAP + HUE_HEIGHT + HUE_TO_CHANNEL_GAP +
                channelCount * CHANNEL_ROW_HEIGHT +
                maxOf(0, channelCount - 1) * CHANNEL_GAP +
                BOTTOM_PADDING
        return DropdownTheme.SETTING_HEIGHT + expandedHeight * openAnim.value
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        syncAlphaAvailability()
        openAnim.run(if (opened) 1.0f else 0.0f)
        val t = openAnim.value

        scope.text(
            setting.displayName, DropdownTheme.SETTING_PADDING_X,
            (DropdownTheme.SETTING_HEIGHT - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
            DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel()
        )

        val previewX = width - DropdownTheme.SETTING_PADDING_X - DropdownTheme.COLOR_PREVIEW_SIZE
        val previewY = (DropdownTheme.SETTING_HEIGHT - DropdownTheme.COLOR_PREVIEW_SIZE) * 0.5f
        scope.roundRect(
            previewX, previewY, DropdownTheme.COLOR_PREVIEW_SIZE, DropdownTheme.COLOR_PREVIEW_SIZE, 2.0f,
            visibleColor
        )

        if (t < 0.01f) return

        var color = visibleColor
        var hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)

        val padX = DropdownTheme.SETTING_PADDING_X
        val gradX = padX
        val gradY = DropdownTheme.SETTING_HEIGHT + 2.0f
        val gradW = width - padX * 2.0f
        val gradH = PICKER_HEIGHT * t

        val hueColor = Color.getHSBColor(hsb[0], 1.0f, 1.0f)
        drawSaturationBrightnessPalette(scope, gradX, gradY, gradW, gradH, hueColor)

        val hueY = gradY + gradH + PICKER_TO_HUE_GAP
        val hueH = HUE_HEIGHT * t
        for (i in 0 until gradW.toInt()) {
            val c = Color.getHSBColor(i / gradW, 1.0f, 1.0f)
            scope.rect(gradX + i, hueY, 1.0f, hueH, c)
        }
        drawSliderPicker(scope, gradX + gradW * hsb[0], hueY, hueH)

        if (pickingSB) {
            DropdownScreen.react(ReisaDropdownCompanion.Action.COLOR_PICK)
            val newSat = Mth.clamp((mouseX - absoluteX(gradX)) / gradW, 0.0f, 1.0f)
            val newBri = 1.0f - Mth.clamp((mouseY - absoluteY(gradY)) / (PICKER_HEIGHT * t), 0.0f, 1.0f)
            var newColor = Color.getHSBColor(hsb[0], newSat, newBri)
            newColor = Color(newColor.red, newColor.green, newColor.blue, color.alpha)
            previewColor(newColor)
        }

        if (pickingHue) {
            DropdownScreen.react(ReisaDropdownCompanion.Action.COLOR_PICK)
            val newHue = Mth.clamp((mouseX - absoluteX(gradX)) / gradW, 0.0f, 1.0f)
            var newColor = Color.getHSBColor(newHue, hsb[1], hsb[2])
            newColor = Color(newColor.red, newColor.green, newColor.blue, color.alpha)
            previewColor(newColor)
        }

        val channel = pickingChannel
        if (channel != null) {
            DropdownScreen.react(ReisaDropdownCompanion.Action.COLOR_PICK)
            updateChannelFromMouse(channel, mouseX.toDouble())
        }

        color = visibleColor
        hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)

        val pickerCx = gradX + gradW * hsb[1]
        val pickerCy = gradY + gradH * (1.0f - hsb[2])
        scope.rect(pickerCx - 3.0f, pickerCy - 3.0f, 6.0f, 6.0f, Color(0, 0, 0, 135))
        scope.rect(pickerCx - 2.0f, pickerCy - 2.0f, 4.0f, 4.0f, Color.WHITE)

        drawChannelRows(scope, textMetrics, mouseX, mouseY, hueY + hueH + HUE_TO_CHANNEL_GAP, t)
    }

    private fun drawSaturationBrightnessPalette(
        scope: UiTree.Scope,
        localX: Float,
        localY: Float,
        width: Float,
        height: Float,
        hueColor: Color,
    ) {
        scope.rect(localX - 1.0f, localY - 1.0f, width + 2.0f, height + 2.0f, Color(255, 255, 255, 45))
        scope.rectGradient(localX, localY, width, height, Color.WHITE, Color.WHITE, hueColor, hueColor)
        scope.rectGradient(
            localX, localY, width, height,
            Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, Color(0, 0, 0, 0)
        )
    }

    private fun drawSliderPicker(scope: UiTree.Scope, centerX: Float, localY: Float, height: Float) {
        val pickerW = 3.0f
        scope.rect(centerX - pickerW * 0.5f - 1.0f, localY - 2.0f, pickerW + 2.0f, height + 4.0f, Color(0, 0, 0, 145))
        scope.rect(centerX - pickerW * 0.5f, localY - 1.0f, pickerW, height + 2.0f, Color.WHITE)
    }

    private fun drawChannelRows(
        scope: UiTree.Scope,
        textMetrics: UiTextMetrics,
        mouseX: Int,
        mouseY: Int,
        startY: Float,
        alphaProgress: Float,
    ) {
        syncFieldsFromColor()
        val channels = channels
        for (i in channels.indices) {
            val channel = channels[i]
            val rowY = startY + i * (CHANNEL_ROW_HEIGHT + CHANNEL_GAP) * alphaProgress
            drawChannelRow(scope, textMetrics, mouseX, mouseY, channel, rowY, alphaProgress)
        }
    }

    private fun drawChannelRow(
        scope: UiTree.Scope,
        textMetrics: UiTextMetrics,
        mouseX: Int,
        mouseY: Int,
        channel: Channel,
        rowY: Float,
        alphaProgress: Float,
    ) {
        val value = getChannelValue(channel)
        val textY = rowY + (CHANNEL_ROW_HEIGHT - textMetrics.textHeight(CHANNEL_TEXT_SCALE)) * 0.5f
        val textColor = MD3Theme.withAlpha(
            DropdownTheme.settingLabel(),
            (DropdownTheme.settingLabel().alpha * alphaProgress).toInt()
        )
        scope.text(channel.label, DropdownTheme.SETTING_PADDING_X, textY, CHANNEL_TEXT_SCALE, textColor)

        val boxX = localFieldX
        val boxY = rowY + (CHANNEL_ROW_HEIGHT - CHANNEL_BOX_HEIGHT) * 0.5f
        val trackX = localTrackX
        val trackY = rowY + (CHANNEL_ROW_HEIGHT - CHANNEL_TRACK_HEIGHT) * 0.5f
        val trackW = localTrackWidth
        drawChannelTrack(scope, channel, trackX, trackY, trackW, CHANNEL_TRACK_HEIGHT, alphaProgress)

        val knobX = trackX + trackW * (value / 255.0f)
        val knobR = 2.75f
        scope.roundRect(
            knobX - knobR, trackY + CHANNEL_TRACK_HEIGHT * 0.5f - knobR, knobR * 2.0f, knobR * 2.0f, knobR,
            DropdownTheme.sliderKnob()
        )

        val field = getField(channel)
        field.drawCentered(
            scope, textMetrics, boxX, boxY, CHANNEL_BOX_WIDTH, CHANNEL_BOX_HEIGHT, mouseX, mouseY,
            value.toString(), CHANNEL_TEXT_SCALE
        )
    }

    private fun drawChannelTrack(
        scope: UiTree.Scope,
        channel: Channel,
        trackX: Float,
        trackY: Float,
        trackW: Float,
        trackH: Float,
        alphaProgress: Float,
    ) {
        val current = visibleColor
        val start = when (channel) {
            Channel.RED -> Color(0, current.green, current.blue)
            Channel.GREEN -> Color(current.red, 0, current.blue)
            Channel.BLUE -> Color(current.red, current.green, 0)
            Channel.ALPHA -> Color(current.red, current.green, current.blue, 0)
        }
        val end = when (channel) {
            Channel.RED -> Color(255, current.green, current.blue)
            Channel.GREEN -> Color(current.red, 255, current.blue)
            Channel.BLUE -> Color(current.red, current.green, 255)
            Channel.ALPHA -> Color(current.red, current.green, current.blue, 255)
        }
        scope.roundRectHorizontalGradient(
            trackX, trackY, trackW, trackH, trackH * 0.5f,
            MD3Theme.withAlpha(start, (start.alpha * alphaProgress).toInt()),
            MD3Theme.withAlpha(end, (end.alpha * alphaProgress).toInt())
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val previewX = absoluteX(width - DropdownTheme.SETTING_PADDING_X - DropdownTheme.COLOR_PREVIEW_SIZE)
        val previewY = absoluteY((DropdownTheme.SETTING_HEIGHT - DropdownTheme.COLOR_PREVIEW_SIZE) * 0.5f)
        if ((button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) &&
            isHovered(
                mouseX, mouseY, previewX - 2, previewY - 2,
                DropdownTheme.COLOR_PREVIEW_SIZE + 4, DropdownTheme.COLOR_PREVIEW_SIZE + 4
            )
        ) {
            if (hasFocusedInput()) {
                commitFocusedInput()
                blurFields()
            }
            opened = !opened
            DropdownScreen.react(
                if (opened) ReisaDropdownCompanion.Action.COLOR_PICK else ReisaDropdownCompanion.Action.PANEL_CLOSE
            )
            return true
        }

        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false

        if (!opened || openAnim.value < 0.5f) return false

        val padX = DropdownTheme.SETTING_PADDING_X
        val gradX = absoluteX(padX)
        val gradY = absoluteY(DropdownTheme.SETTING_HEIGHT + 2.0f)
        val gradW = width - padX * 2.0f
        val gradH = PICKER_HEIGHT * openAnim.value
        val hueY = gradY + gradH + PICKER_TO_HUE_GAP
        val hueH = HUE_HEIGHT * openAnim.value

        for (channel in channels) {
            val fieldY = getChannelY(channel) + (CHANNEL_ROW_HEIGHT - CHANNEL_BOX_HEIGHT) * 0.5f
            if (isHovered(mouseX, mouseY, fieldX, fieldY, CHANNEL_BOX_WIDTH, CHANNEL_BOX_HEIGHT)) {
                blurFields()
                val field = getField(channel)
                field.text = getChannelValue(channel).toString()
                field.focusIfContainsCentered(mouseX, mouseY, fieldX, fieldY, CHANNEL_BOX_WIDTH, CHANNEL_BOX_HEIGHT)
                DropdownScreen.react(ReisaDropdownCompanion.Action.TYPING)
                return true
            }
        }
        if (hasFocusedInput()) {
            commitFocusedInput()
            blurFields()
        }

        if (isHovered(mouseX, mouseY, gradX, gradY, gradW, gradH)) {
            pickingSB = true
            DropdownScreen.react(ReisaDropdownCompanion.Action.COLOR_PICK)
            return true
        }
        if (isHovered(mouseX, mouseY, gradX, hueY, gradW, hueH)) {
            pickingHue = true
            DropdownScreen.react(ReisaDropdownCompanion.Action.COLOR_PICK)
            return true
        }
        for (channel in channels) {
            val trackX = trackX
            val trackY = getChannelY(channel) + (CHANNEL_ROW_HEIGHT - CHANNEL_TRACK_HEIGHT) * 0.5f
            val trackW = trackWidth
            if (isHovered(mouseX, mouseY, trackX, trackY - 3.0f, trackW, CHANNEL_TRACK_HEIGHT + 6.0f)) {
                pickingChannel = channel
                updateChannelFromMouse(channel, mouseX)
                DropdownScreen.react(ReisaDropdownCompanion.Action.COLOR_PICK)
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && (pickingSB || pickingHue || pickingChannel != null)) {
            commitPendingColor()
            pickingSB = false
            pickingHue = false
            pickingChannel = null
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedField ?: return false
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            commitFocusedInput()
            focused.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            clearPendingColor()
            syncFieldsFromColor(true)
            focused.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CANCEL)
            return true
        }
        if (focused.keyPressed(keyCode)) {
            syncFocusedInput()
            return true
        }
        return false
    }

    override fun charTyped(typedText: String): Boolean {
        val focused = focusedField ?: return false
        if (focused.charTyped(typedText)) {
            syncFocusedInput()
            return true
        }
        return false
    }

    fun hasFocusedInput(): Boolean {
        return focusedField != null
    }

    fun blurAllInputs() {
        val focused = focusedField
        if (focused != null) {
            syncFocusedInput()
            focused.blur()
        }
    }

    private fun getChannelY(channel: Channel): Float {
        val expanded = openAnim.value
        val baseY = absoluteY(
            DropdownTheme.SETTING_HEIGHT + 2.0f +
                    PICKER_HEIGHT * expanded +
                    PICKER_TO_HUE_GAP +
                    HUE_HEIGHT * expanded +
                    HUE_TO_CHANNEL_GAP
        )
        return baseY + getChannelIndex(channel) * (CHANNEL_ROW_HEIGHT + CHANNEL_GAP) * expanded
    }

    private val fieldX: Float
        get() = absoluteX(width - DropdownTheme.SETTING_PADDING_X - CHANNEL_BOX_WIDTH)

    private val localFieldX: Float
        get() = width - DropdownTheme.SETTING_PADDING_X - CHANNEL_BOX_WIDTH

    private val trackX: Float
        get() = absoluteX(DropdownTheme.SETTING_PADDING_X + CHANNEL_LABEL_WIDTH + 4.0f)

    private val localTrackX: Float
        get() = DropdownTheme.SETTING_PADDING_X + CHANNEL_LABEL_WIDTH + 4.0f

    private val trackWidth: Float
        get() = fieldX - trackX - 5.0f

    private val localTrackWidth: Float
        get() = localFieldX - localTrackX - 5.0f

    private fun updateChannelFromMouse(channel: Channel, mouseX: Double) {
        val trackX = trackX
        val trackW = trackWidth
        val value = Mth.clamp((Mth.clamp(((mouseX - trackX) / trackW).toFloat(), 0.0f, 1.0f) * 255.0f).roundToInt(), 0, 255)
        setChannelValue(channel, value, true)
    }

    private fun getChannelValue(channel: Channel): Int {
        val color = visibleColor
        return when (channel) {
            Channel.RED -> color.red
            Channel.GREEN -> color.green
            Channel.BLUE -> color.blue
            Channel.ALPHA -> color.alpha
        }
    }

    private fun setChannelValue(channel: Channel, value: Int, defer: Boolean) {
        val current = visibleColor
        val clamped = Mth.clamp(value, 0, 255)
        val updated = when (channel) {
            Channel.RED -> Color(clamped, current.green, current.blue, current.alpha)
            Channel.GREEN -> Color(current.red, clamped, current.blue, current.alpha)
            Channel.BLUE -> Color(current.red, current.green, clamped, current.alpha)
            Channel.ALPHA -> Color(current.red, current.green, current.blue, clamped)
        }
        if (defer) {
            previewColor(updated)
        } else {
            applyColorNow(updated)
        }
        syncFieldsFromColor()
    }

    private fun getField(channel: Channel): DropdownTextField {
        return when (channel) {
            Channel.RED -> redField
            Channel.GREEN -> greenField
            Channel.BLUE -> blueField
            Channel.ALPHA -> alphaField
        }
    }

    private val focusedField: DropdownTextField?
        get() {
            for (channel in channels) {
                val field = getField(channel)
                if (field.isFocused) return field
            }
            return null
        }

    private val focusedChannel: Channel?
        get() {
            for (channel in channels) {
                if (getField(channel).isFocused) return channel
            }
            return null
        }

    private fun syncFocusedInput() {
        val channel = focusedChannel ?: return
        val value = getField(channel).text
        if (value.isEmpty()) return
        setChannelValue(channel, parseChannel(value), true)
    }

    private fun commitFocusedInput() {
        val channel = focusedChannel ?: return
        val field = getField(channel)
        val value = if (field.text.isEmpty()) getChannelValue(channel) else parseChannel(field.text)
        setChannelValue(channel, value, false)
        field.text = getChannelValue(channel).toString()
        field.setCursorToEnd()
    }

    private fun parseChannel(value: String): Int {
        return try {
            Mth.clamp(value.toInt(), 0, 255)
        } catch (ignored: NumberFormatException) {
            0
        }
    }

    private fun syncFieldsFromColor() {
        syncFieldsFromColor(false)
    }

    private fun syncFieldsFromColor(force: Boolean) {
        for (channel in channels) {
            val field = getField(channel)
            if (!force && field.isFocused) continue
            val value = getChannelValue(channel).toString()
            if (value != field.text) {
                field.text = value
                field.setCursorToEnd()
            }
        }
    }

    private fun blurFields() {
        for (channel in RGBA_CHANNELS) {
            getField(channel).blur()
        }
    }

    private val channels: Array<Channel>
        get() = if (setting.isAllowAlpha()) RGBA_CHANNELS else RGB_CHANNELS

    private fun getChannelIndex(channel: Channel): Int {
        val channels = channels
        for (i in channels.indices) {
            if (channels[i] == channel) return i
        }
        return 0
    }

    private fun syncAlphaAvailability() {
        if (setting.isAllowAlpha()) return
        if (pickingChannel == Channel.ALPHA) {
            pickingChannel = null
        }
        alphaField.blur()
    }

    private val visibleColor: Color
        get() = pendingValue ?: setting.value

    private fun previewColor(color: Color) {
        if (setting.isApplyWhenRelease) {
            pendingValue = color
        } else {
            setting.value = color
        }
    }

    private fun applyColorNow(color: Color) {
        pendingValue = null
        setting.value = color
    }

    private fun commitPendingColor() {
        val color = pendingValue ?: return
        applyColorNow(color)
    }

    private fun clearPendingColor() {
        pendingValue = null
    }

    private enum class Channel(val label: String) {
        RED("R"),
        GREEN("G"),
        BLUE("B"),
        ALPHA("A"),
    }

    private companion object {

        private const val CHANNEL_ROW_HEIGHT = 15.0f
        private const val CHANNEL_LABEL_WIDTH = 8.0f
        private const val CHANNEL_BOX_WIDTH = 24.0f
        private const val CHANNEL_BOX_HEIGHT = 12.0f
        private const val CHANNEL_TRACK_HEIGHT = 4.0f
        private const val CHANNEL_GAP = 2.0f
        private const val CHANNEL_TEXT_SCALE = 0.44f
        private const val PICKER_HEIGHT = 48.0f
        private const val HUE_HEIGHT = 6.0f
        private const val PICKER_TO_HUE_GAP = 3.0f
        private const val HUE_TO_CHANNEL_GAP = 6.0f
        private const val BOTTOM_PADDING = 2.0f

        private val RGB_CHANNELS = arrayOf(Channel.RED, Channel.GREEN, Channel.BLUE)
        private val RGBA_CHANNELS = arrayOf(Channel.RED, Channel.GREEN, Channel.BLUE, Channel.ALPHA)

    }

}
