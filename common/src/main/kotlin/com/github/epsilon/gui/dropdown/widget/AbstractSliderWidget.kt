package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.Setting
import com.mojang.blaze3d.platform.InputConstants
import java.util.function.Predicate

abstract class AbstractSliderWidget<S : Setting<T>, T : Number>(
    setting: S,
    maxInputLength: Int,
    inputFilter: Predicate<String>,
) : SettingWidget<S>(setting) {

    protected val inputField: DropdownTextField = DropdownTextField(maxInputLength, inputFilter)
    protected var dragging: Boolean = false
    protected var pendingValue: T? = null
    private var sessionId: Int = -1

    override fun getHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT + 15.0f
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        syncSessionState()

        val ratio = getRatio()
        val sliderRatio = ratio.coerceIn(0.0f, 1.0f)

        scope.text(
            setting.displayName,
            DropdownTheme.SETTING_PADDING_X,
            1.0f,
            DropdownTheme.SETTING_TEXT_SCALE,
            DropdownTheme.settingLabel()
        )

        val trackX = getLocalTrackX()
        val trackY = getLocalTrackY()
        val trackW = getTrackWidth()
        val trackH = DropdownTheme.SLIDER_HEIGHT

        val editing = inputField.isFocused
        if (editing) {
            inputField.draw(
                scope, textMetrics, getLocalEditorX(), getLocalEditorY(), getEditorWidth(), getEditorHeight(),
                mouseX, mouseY, formatPlainValue(), DropdownTheme.SETTING_TEXT_SCALE
            )
        } else {
            scope.roundRect(trackX, trackY, trackW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderTrack())

            val activeW = trackW * sliderRatio
            if (activeW > 0.5f) {
                scope.roundRect(
                    trackX,
                    trackY,
                    activeW,
                    trackH,
                    DropdownTheme.SLIDER_RADIUS,
                    DropdownTheme.sliderActive()
                )
            }

            val knobX = trackX + trackW * sliderRatio
            val knobY = trackY + trackH * 0.5f
            val kr = DropdownTheme.SLIDER_KNOB_RADIUS
            scope.roundRect(knobX - kr, knobY - kr, kr * 2.0f, kr * 2.0f, kr, DropdownTheme.sliderKnob())

            if (dragging && mouseX >= 0) {
                DropdownScreen.react(ReisaDropdownCompanion.Action.SLIDER_ADJUST)
                val rawRatio = ((mouseX - getTrackX()) / trackW).coerceIn(0.0f, 1.0f)
                updateValueFromRatio(rawRatio)
            }
        }

        if (!editing) {
            drawValueLabels(scope, textMetrics, trackX, trackY, trackW)
        }
    }

    protected abstract fun getRatio(): Float

    protected abstract fun updateValueFromRatio(rawRatio: Float)

    protected abstract fun formatValue(value: T): String

    protected abstract fun formatPlainValue(): String

    protected abstract fun commitInput()

    protected abstract fun syncInputValue()

    protected abstract fun applyValue(value: T)

    protected fun getVisibleValue(): T {
        return pendingValue ?: setting.value
    }

    protected fun previewValue(value: T) {
        pendingValue = value
    }

    protected fun applyValueNow(value: T) {
        pendingValue = null
        applyValue(value)
    }

    protected fun commitPendingValue() {
        val value = pendingValue ?: return
        applyValueNow(value)
    }

    protected fun clearPendingValue() {
        pendingValue = null
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        syncSessionState()
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                inputField.text = formatPlainValue()
                inputField.focusIfContains(
                    mouseX,
                    mouseY,
                    getEditorX(),
                    getEditorY(),
                    getEditorWidth(),
                    getEditorHeight()
                )
                dragging = false
                DropdownScreen.react(ReisaDropdownCompanion.Action.TYPING)
                return true
            }
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (inputField.isFocused) {
                if (isEditorBoundsHovered(mouseX, mouseY)) {
                    inputField.focusIfContains(
                        mouseX,
                        mouseY,
                        getEditorX(),
                        getEditorY(),
                        getEditorWidth(),
                        getEditorHeight()
                    )
                    DropdownScreen.react(ReisaDropdownCompanion.Action.TYPING)
                    return true
                }
                commitInput()
                inputField.blur()
            }
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                dragging = true
                DropdownScreen.react(ReisaDropdownCompanion.Action.SLIDER_ADJUST)
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        syncSessionState()
        if (button == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            commitPendingValue()
            dragging = false
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        if (inputField.isFocused) {
            if (isEditorBoundsHovered(mouseX, mouseY)) {
                return true
            }
            commitInput()
            inputField.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        syncSessionState()
        if (!inputField.isFocused) return false
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            commitInput()
            inputField.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            clearPendingValue()
            inputField.text = formatPlainValue()
            inputField.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CANCEL)
            return true
        }
        if (inputField.keyPressed(keyCode)) {
            syncInputValue()
            return true
        }
        return false
    }

    override fun charTyped(typedText: String): Boolean {
        syncSessionState()
        if (inputField.charTyped(typedText)) {
            syncInputValue()
            return true
        }
        return false
    }

    fun isFocused(): Boolean {
        return inputField.isFocused
    }

    fun blurInput() {
        commitInput()
        inputField.blur()
    }

    private fun syncSessionState() {
        val currentSessionId = DropdownScreen.getSessionId()
        if (sessionId == currentSessionId) {
            return
        }
        sessionId = currentSessionId
        dragging = false
        clearPendingValue()
        inputField.blur()
    }

    protected fun drawValueLabels(
        scope: UiTree.Scope,
        textMetrics: UiTextMetrics,
        trackX: Float,
        trackY: Float,
        trackW: Float
    ) {
        val minValue = formatValue(getMin())
        val currentValue = formatValue(getVisibleValue())
        val maxValue = formatValue(getMax())
        val textY = trackY + DropdownTheme.SLIDER_HEIGHT + VALUE_TEXT_Y_OFFSET

        scope.text(minValue, trackX, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted())

        val currentWidth = textMetrics.textWidth(currentValue, VALUE_TEXT_SCALE)
        scope.text(
            currentValue,
            trackX + (trackW - currentWidth) * 0.5f,
            textY,
            VALUE_TEXT_SCALE,
            DropdownTheme.settingLabel()
        )

        val maxWidth = textMetrics.textWidth(maxValue, VALUE_TEXT_SCALE)
        scope.text(maxValue, trackX + trackW - maxWidth, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted())
    }

    protected abstract fun getMin(): T

    protected abstract fun getMax(): T

    protected fun getTrackX(): Float {
        return absoluteX(DropdownTheme.SETTING_PADDING_X)
    }

    protected fun getTrackY(): Float {
        return absoluteY(DropdownTheme.SETTING_HEIGHT)
    }

    protected fun getLocalTrackX(): Float {
        return DropdownTheme.SETTING_PADDING_X
    }

    protected fun getLocalTrackY(): Float {
        return DropdownTheme.SETTING_HEIGHT
    }

    protected fun getTrackWidth(): Float {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f
    }

    protected fun getEditorX(): Float {
        return getTrackX()
    }

    protected fun getEditorY(): Float {
        return getTrackY() - EDITOR_Y_OFFSET
    }

    protected fun getLocalEditorX(): Float {
        return getLocalTrackX()
    }

    protected fun getLocalEditorY(): Float {
        return getLocalTrackY() - EDITOR_Y_OFFSET
    }

    protected fun getEditorWidth(): Float {
        return getTrackWidth()
    }

    protected fun getEditorHeight(): Float {
        return DropdownTheme.INPUT_HEIGHT
    }

    protected fun isEditorBoundsHovered(mouseX: Double, mouseY: Double): Boolean {
        return isHovered(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight())
    }

    protected fun isEditorHitboxHovered(mouseX: Double, mouseY: Double): Boolean {
        return isHovered(mouseX, mouseY, getTrackX(), getEditorY(), getTrackWidth(), DropdownTheme.INPUT_HEIGHT)
    }

    companion object {
        protected const val VALUE_TEXT_SCALE = 0.46f
        protected const val VALUE_TEXT_Y_OFFSET = 3.0f
        protected const val EDITOR_Y_OFFSET = 5.0f
    }

}
