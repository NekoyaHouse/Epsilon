package com.github.epsilon.gui.troll

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.GuiSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.settings.impl.StringSetting
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.math.BigDecimal

class TrollSettingRow {

    enum class ModuleAction {
        TOGGLE,
        BIND,
        HIDDEN,
    }

    private val setting: Setting<*>?
    private val module: Module?
    private val action: ModuleAction?
    private val progress: Animation = Animation(Easing.EASE_OUT_QUART, 300L)
    private val hover: Animation = Animation(Easing.EASE_OUT_EXPO, 300L)
    private var bounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var input: DropdownTextField? = null
    private var originalText: String? = null
    private var listening: Boolean = false
    private var dragging: Boolean = false
    private var enumDragged: Boolean = false
    private var dragStartX: Double = 0.0
    private var pendingNumber: Double? = null
    private var pendingEnumIndex: Int = -1

    constructor(setting: Setting<*>) {
        this.setting = setting
        this.module = null
        this.action = null
        progress.startValue = target()
    }

    constructor(module: Module, action: ModuleAction) {
        this.setting = null
        this.module = module
        this.action = action
        progress.startValue = target()
    }

    fun layout(bounds: UiRect) {
        this.bounds = bounds
    }

    fun startOpenAnimation() {
        progress.value = 0.0f
        progress.reset()
        progress.run(target())
    }

    fun label(): String {
        if (setting != null) return setting.displayName
        return when (action) {
            ModuleAction.TOGGLE -> module!!.translatedName
            ModuleAction.BIND -> EpsilonTranslations.TrollGui.KEYBIND.getTranslatedName()
            ModuleAction.HIDDEN -> EpsilonTranslations.Module.HIDDEN.getTranslatedName()
            null -> ""
        }
    }

    private fun valueText(): String {
        return valueText(false)
    }

    private fun displayValueText(): String {
        return valueText(true)
    }

    private fun valueText(previewEnum: Boolean): String {
        if (listening) return EpsilonTranslations.TrollGui.LISTENING.getTranslatedName()
        val module = this.module
        if (action == ModuleAction.BIND && module != null) {
            val modeKey = if (module.bindMode == Module.BindMode.Hold) EpsilonTranslations.Keybind.HOLD else EpsilonTranslations.Keybind.TOGGLE
            return KeybindUtils.format(module.keyBind) + " (" + modeKey.getTranslatedName() + ")"
        }
        return when (val s = setting) {
            is KeybindSetting -> KeybindUtils.format(s.value)
            is IntSetting -> (numberValue()).roundToInt().toString()
            is DoubleSetting -> formatNumber(numberValue())
            is EnumSetting<*> ->
                if (previewEnum && pendingEnumIndex >= 0 && pendingEnumIndex < modesOf(s).size) {
                    s.getTranslatedValueByIndex(pendingEnumIndex)
                } else {
                    s.translatedValue
                }
            is StringSetting -> s.value
            is StringListSetting -> s.value.size.toString() + " " + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()
            is RegistryListSetting<*> -> s.value.size.toString() + " " + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()
            else -> ""
        }
    }

    fun minimumWidth(metrics: TextRenderer): Float {
        if (setting is ColorSetting) {
            return maxOf(120.0f, metrics.getWidth(label(), TrollTheme.TEXT_SCALE) + metrics.getHeight(TrollTheme.TEXT_SCALE) + 39.0f)
        }
        return maxOf(120.0f, metrics.getWidth(label(), TrollTheme.TEXT_SCALE) + metrics.getWidth(valueText(), TrollTheme.VALUE_SCALE) + 24.0f)
    }

    fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        val h = scope.animate(hover, bounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val p = scope.animate(progress, target())
        scope.rect(1, bounds.x, bounds.y, bounds.width, bounds.height, Color(0, 0, 0, 40))
        if (p > 0.001f) {
            scope.rect(1, bounds.x, bounds.y, bounds.width * p, bounds.height, TrollTheme.primary())
        }
        val hoverAlpha = GuiSetting.hoverAlpha
        scope.rect(2, bounds.x, bounds.y, bounds.width, bounds.height, Color(255, 255, 255, (hoverAlpha * h).toInt()))
        if (isEditing()) {
            if (setting is IntSetting || setting is DoubleSetting) {
                drawNumericEditing(scope, metrics)
            } else {
                val activeInput = input ?: return
                scope.layer(3) { content ->
                    activeInput.draw(
                        content, TrollTheme.metrics(metrics), bounds.x, bounds.y, bounds.width, bounds.height,
                        mouseX, mouseY, "", TrollTheme.TEXT_SCALE
                    )
                }
            }
            return
        }
        val value = displayValueText()
        val valueWidth = if (setting is ColorSetting) {
            metrics.getHeight(TrollTheme.TEXT_SCALE) + 9.0f
        } else {
            minOf(metrics.getWidth(value, TrollTheme.VALUE_SCALE), bounds.width * 0.48f)
        }
        val scale = TrollTheme.TEXT_SCALE * (1.0f + h * 0.05f - (if (dragging) 0.1f else 0.0f))
        val text = if (setting != null && !setting.isPlatformSupported()) TrollTheme.alpha(TrollTheme.text(), 110) else TrollTheme.text()
        scope.text(
            3, TrollTheme.trim(label(), metrics, scale, bounds.width - valueWidth - 10.5f),
            bounds.x + 3.0f + h * 3.0f, bounds.y + 2.25f, scale, text
        )
        if (setting is ColorSetting) {
            drawColorSwatch(scope, metrics, setting.value)
            return
        }
        scope.text(
            3, TrollTheme.trim(value, metrics, TrollTheme.VALUE_SCALE, valueWidth),
            bounds.right() - valueWidth - 3.0f, bounds.bottom() - metrics.getHeight(TrollTheme.VALUE_SCALE) - 3.0f,
            TrollTheme.VALUE_SCALE, text
        )
    }

    private fun drawNumericEditing(scope: UiTree.Scope, metrics: TextRenderer) {
        val activeInput = input ?: return
        val text = activeInput.text
        val labelScale = TrollTheme.TEXT_SCALE
        val valueScale = TrollTheme.VALUE_SCALE
        val textY = bounds.y + 2.25f
        val valueWidth = minOf(metrics.getWidth(text, valueScale), bounds.width * 0.48f)
        val valueX = bounds.right() - valueWidth - 3.0f

        scope.text(
            3, TrollTheme.trim(label(), metrics, labelScale, maxOf(1.0f, bounds.width - valueWidth - 10.5f)),
            bounds.x + 3.0f, textY, labelScale, TrollTheme.text()
        )
        scope.text(
            3, TrollTheme.trim(text, metrics, valueScale, valueWidth),
            valueX, bounds.bottom() - metrics.getHeight(valueScale) - 3.0f,
            valueScale, TrollTheme.text()
        )

        val cursor = activeInput.getCursorIndex()
        var caretX = valueX + metrics.getWidth(text.substring(0, cursor), valueScale)
        caretX = minOf(caretX, bounds.right() - 3.0f)
        val caretY = bounds.bottom() - metrics.getHeight(valueScale) - 3.0f
        if (System.currentTimeMillis() % 1000 > 500) {
            scope.rect(4, caretX, caretY, 0.8f, metrics.getHeight(valueScale), TrollTheme.text())
        }
        IMEFocusHelper.updateCursorPos(caretX, caretY)
    }

    private fun drawColorSwatch(scope: UiTree.Scope, metrics: TextRenderer, color: Color) {
        val size = metrics.getHeight(TrollTheme.TEXT_SCALE)
        val x = bounds.right() - size - 6.0f
        val y = bounds.y + (bounds.height - size) / 2.0f
        scope.rect(3, x - 1.5f, y - 1.5f, size + 3.0f, size + 3.0f, Color(0, 0, 0, 80))
        scope.rect(4, x, y, size, size, color)
    }

    fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (!bounds.contains(event.x(), event.y())) return false
        val setting = this.setting
        if (setting != null && !setting.isPlatformSupported()) {
            PlatformNoticeScreen.show(setting)
            return true
        }
        val module = this.module
        val button = event.button()
        if (action == ModuleAction.BIND || setting is KeybindSetting) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT && module != null) {
                module.bindMode = if (module.bindMode == Module.BindMode.Toggle) Module.BindMode.Hold else Module.BindMode.Toggle
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                listening = true
            }
        } else if (action == ModuleAction.HIDDEN && button == InputConstants.MOUSE_BUTTON_LEFT && module != null) {
            module.isHidden = !module.isHidden
        } else if (action == ModuleAction.TOGGLE && module != null) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT || module.category == null) {
                TrollGuiScreen.INSTANCE.openSettings(module, event.x(), event.y())
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                module.toggle()
            } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                TrollGuiScreen.INSTANCE.openSettings(module, event.x(), event.y()).listenForBind()
            }
        } else if (setting is BoolSetting && button == InputConstants.MOUSE_BUTTON_LEFT) {
            setting.value = !setting.value
        } else if (setting is IntSetting || setting is DoubleSetting) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) startEditing()
            else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                dragging = true
                dragStartX = event.x()
                updateNumber(event.x(), event.modifiers())
            }
        } else if (setting is EnumSetting<*> && button == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = true
            enumDragged = false
            dragStartX = event.x()
        } else if (setting is StringSetting && button == InputConstants.MOUSE_BUTTON_LEFT) {
            startEditing()
        } else if (setting is ColorSetting && button == InputConstants.MOUSE_BUTTON_RIGHT) {
            TrollGuiScreen.INSTANCE.openColorPicker(setting, event.x(), event.y())
        } else if (setting is ButtonSetting && button == InputConstants.MOUSE_BUTTON_LEFT) {
            setting.value.run()
        } else if (setting is StringListSetting && button == InputConstants.MOUSE_BUTTON_LEFT) {
            TrollGuiScreen.INSTANCE.openStringListSettingPopup(setting)
        } else if (setting is RegistryListSetting<*> && button == InputConstants.MOUSE_BUTTON_LEFT) {
            TrollGuiScreen.INSTANCE.openRegistryListSettingPopup(setting)
        }
        return true
    }

    fun mouseDragged(event: MouseButtonEvent): Boolean {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        val setting = this.setting
        if (setting is EnumSetting<*>) {
            enumDragged = enumDragged || abs(event.x() - dragStartX) > 2.0
            if (enumDragged) {
                val modeCount = modesOf(setting).size
                pendingEnumIndex = (((event.x() - bounds.x) / bounds.width * modeCount).toInt().toLong()).coerceIn(((0).toLong()).toLong(), ((modeCount - 1).toLong()).toLong()).toInt().toInt()
            }
        } else {
            updateNumber(event.x(), event.modifiers())
        }
        return true
    }

    fun mouseReleased(event: MouseButtonEvent) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !dragging) return
        dragging = false
        val pending = pendingNumber
        if (pending != null) {
            pendingNumber = null
            applyNumber(pending)
        }
        val setting = this.setting
        if (setting is EnumSetting<*>) {
            val pendingIndex = pendingEnumIndex
            pendingEnumIndex = -1
            if (enumDragged) {
                if (pendingIndex >= 0) setMode(setting, pendingIndex)
            } else if (bounds.contains(event.x(), event.y())) {
                setMode(setting, (setting.modeIndex + 1) % modesOf(setting).size)
            }
        }
    }

    fun keyPressed(event: KeyEvent): Boolean {
        if (listening) {
            listening = false
            if (!event.isEscape()) {
                setBind(
                    if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) KeybindUtils.NONE else event.key()
                )
            }
            return true
        }
        if (!isEditing()) return false
        val activeInput = input ?: return false
        if (event.isEscape() || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            finishEditing(!event.isEscape())
        } else if (activeInput.keyPressed(event)) {
            previewText()
        }
        return true
    }

    fun charTyped(event: CharacterEvent): Boolean {
        if (!isEditing()) return false
        val activeInput = input ?: return false
        if (activeInput.charTyped(event)) previewText()
        return true
    }

    fun captureMouseBind(event: MouseButtonEvent): Boolean {
        if (!listening) return false
        listening = false
        setBind(KeybindUtils.encodeMouseButton(event.button()))
        return true
    }

    fun listenForBind() {
        listening = true
    }

    fun resetInput() {
        listening = false
        dragging = false
        pendingEnumIndex = -1
        val pending = pendingNumber
        if (pending != null) {
            pendingNumber = null
            applyNumber(pending)
        }
        finishEditing(true)
    }

    private fun isEditing(): Boolean {
        return input?.isFocused == true
    }

    private fun startEditing() {
        if (isEditing()) return
        val original = if (setting is StringSetting) (setting as StringSetting).value else valueText()
        originalText = original
        val created = DropdownTextField(maxOf(256, original.length))
        created.text = original
        created.focus()
        input = created
    }

    private fun finishEditing(commit: Boolean) {
        if (!isEditing()) return
        val activeInput = input ?: return
        val text = activeInput.text
        activeInput.blur()
        val setting = this.setting
        if (setting is StringSetting) {
            val next = if (commit) text else (originalText ?: text)
            if (setting.value != next) setting.value = next
        } else if (commit) {
            try {
                val value = text.toDouble()
                if (value.isFinite()) applyNumber((value).coerceIn(minimum(), maximum()))
            } catch (exception: NumberFormatException) {
                Constants.LOGGER.debug("Troll GUI 数值输入无效: {} = {}", setting?.name, text, exception)
            }
        }
    }

    private fun previewText() {
        val activeInput = input ?: return
        val setting = this.setting
        if (setting is StringSetting && !setting.isApplyWhenRelease && setting.value != activeInput.text) {
            setting.value = activeInput.text
        }
    }

    private fun setBind(bind: Int) {
        val module = this.module
        if (module != null) {
            module.keyBind = bind
        } else {
            val setting = this.setting
            if (setting is KeybindSetting) setting.value = bind
        }
    }

    private fun setMode(mode: EnumSetting<*>, index: Int) {
        val next = modesOf(mode)[index]
        if (!mode.isModeSupportedUnchecked(next)) {
            PlatformNoticeScreen.showOption(mode, next)
        } else if (mode.value !== next) {
            mode.setMode(next.name)
        }
    }

    private fun numberValue(): Double {
        val pending = pendingNumber
        if (pending != null) return pending
        return (setting!!.value as Number).toDouble()
    }

    private fun formatNumber(value: Double): String {
        return if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else value.toString()
    }

    private fun minimum(): Double {
        return when (val s = setting) {
            is IntSetting -> s.min.toDouble()
            is DoubleSetting -> s.min
            else -> 0.0
        }
    }

    private fun maximum(): Double {
        return when (val s = setting) {
            is IntSetting -> s.max.toDouble()
            is DoubleSetting -> s.max
            else -> 0.0
        }
    }

    private fun updateNumber(mouseX: Double, modifiers: Int) {
        val setting = this.setting
        var step = if (setting is IntSetting) setting.step.toDouble() else (setting as DoubleSetting).step
        var fraction = ((mouseX - bounds.x) / maxOf(1.0f, bounds.width).toDouble()).coerceIn(0.0, 1.0)
        if ((modifiers and InputConstants.MOD_ALT) != 0) {
            fraction = ((numberValue() - minimum() + (mouseX - dragStartX) * (maximum() - minimum()) / bounds.width * 0.1) / maxOf(0.000001, maximum() - minimum())).coerceIn(0.0, 1.0)
            if (setting is DoubleSetting) step *= 0.1
        }
        dragStartX = mouseX
        var value = minimum() + (fraction * (maximum() - minimum()) / maxOf(0.000001, step)).roundToInt() * step
        value = (value).coerceIn(minimum(), maximum())
        if (setting!!.isApplyWhenRelease) pendingNumber = value else applyNumber(value)
    }

    private fun applyNumber(value: Double) {
        val setting = this.setting
        if (setting is IntSetting && setting.value != (value).roundToInt()) {
            setting.value = (value).roundToInt()
        } else if (setting is DoubleSetting && setting.value.compareTo(value) != 0) {
            setting.value = value
        }
    }

    private fun target(): Float {
        if (action != null) {
            val module = this.module
            return when (action) {
                ModuleAction.TOGGLE -> if (module != null && module.isEnabled) 1.0f else 0.0f
                ModuleAction.BIND -> if (listening) 1.0f else 0.0f
                ModuleAction.HIDDEN -> if (module != null && module.isHidden) 1.0f else 0.0f
                null -> 0.0f
            }
        }
        return when (val s = setting) {
            is BoolSetting -> if (s.value) 1.0f else 0.0f
            is IntSetting -> ((numberValue() - minimum()) / maxOf(1.0, maximum() - minimum())).coerceIn(0.0, 1.0).toFloat()
            is DoubleSetting -> ((numberValue() - minimum()) / maxOf(0.000001, maximum() - minimum())).coerceIn(0.0, 1.0).toFloat()
            is EnumSetting<*> ->
                (if (pendingEnumIndex >= 0) pendingEnumIndex else s.modeIndex) / maxOf(1, modesOf(s).size - 1).toFloat()
            is StringSetting -> if (isEditing()) 0.0f else 1.0f
            is ColorSetting -> 0.0f
            else -> 0.0f
        }
    }

    companion object {

        @Suppress("UNCHECKED_CAST")
        private fun modesOf(setting: EnumSetting<*>): Array<Enum<*>> {
            return setting.modes as Array<Enum<*>>
        }
    }

}
