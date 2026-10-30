package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.dropdown.widget.BoolWidget
import com.github.epsilon.gui.dropdown.widget.ButtonWidget
import com.github.epsilon.gui.dropdown.widget.ColorWidget
import com.github.epsilon.gui.dropdown.widget.DoubleSliderWidget
import com.github.epsilon.gui.dropdown.widget.EnumWidget
import com.github.epsilon.gui.dropdown.widget.IntSliderWidget
import com.github.epsilon.gui.dropdown.widget.KeybindWidget
import com.github.epsilon.gui.dropdown.widget.RegistryListSettingWidget
import com.github.epsilon.gui.dropdown.widget.SettingWidget
import com.github.epsilon.gui.dropdown.widget.StringListSettingWidget
import com.github.epsilon.gui.dropdown.widget.StringWidget
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.modules.Module
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
import net.minecraft.util.Mth
import java.awt.Color
import java.util.Locale

class ModuleButton(val module: Module) : Component() {

    private val sectionRenderer: SettingSectionRenderer

    private val expandAnim: Animation = Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_EXPAND)
    private val toggleAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE)
    private val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)
    private val keybindHoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)
    private var expanded = false
    private var listeningKeybind = false
    private var cachedHeightFrameId = Int.MIN_VALUE
    private var cachedHeight = 0.0f

    init {
        val ownerKey = "module:" + module.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
        sectionRenderer = SettingSectionRenderer.create(ownerKey, module.settings) { setting -> createWidget(setting) }
    }

    override fun getHeight(): Float {
        return computeHeight()
    }

    fun getHeightForFrame(frameId: Int): Float {
        if (frameId == Int.MIN_VALUE) {
            return computeHeight()
        }
        if (cachedHeightFrameId != frameId) {
            cachedHeight = computeHeight()
            cachedHeightFrameId = frameId
        }
        return cachedHeight
    }

    private fun computeHeight(): Float {
        expandAnim.run(if (expanded) 1.0f else 0.0f)
        val settingsHeight = computeSettingsHeight()
        return DropdownTheme.MODULE_HEIGHT + settingsHeight * expandAnim.value
    }

    private fun computeSettingsHeight(): Float {
        return DropdownTheme.SETTING_GAP + sectionRenderer.height()
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        expandAnim.run(if (expanded) 1.0f else 0.0f)
        toggleAnim.run(if (module.isEnabled) 1.0f else 0.0f)
        val headerHovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, DropdownTheme.MODULE_HEIGHT)
        hoverAnim.run(if (headerHovered) 1.0f else 0.0f)

        val hover = hoverAnim.value
        val toggle = toggleAnim.value

        val bg = MD3Theme.lerp(DropdownTheme.moduleDisabled(hover), DropdownTheme.moduleEnabled(hover), toggle)
        scope.rect(2.0f, 0.0f, width - 4.0f, DropdownTheme.MODULE_HEIGHT, bg)
        scope.rect(3.0f, DropdownTheme.MODULE_HEIGHT - 0.5f, width - 6.0f, 0.5f, DropdownTheme.moduleDivider())

        val textColor = MD3Theme.lerp(DropdownTheme.moduleTextDisabled(hover), DropdownTheme.moduleTextEnabled(), toggle)
        val textY = (DropdownTheme.MODULE_HEIGHT - textMetrics.textHeight(DropdownTheme.MODULE_TEXT_SCALE)) * 0.5f
        val leftX = DropdownTheme.MODULE_PADDING_X
        scope.text(module.translatedName, leftX, textY, DropdownTheme.MODULE_TEXT_SCALE, textColor)

        drawKeybindButton(scope, textMetrics, mouseX, mouseY, toggle)
        drawHiddenButton(scope, textMetrics, mouseX, mouseY)

        val expand = expandAnim.value

        if (expand > 0.5f) {
            sectionRenderer.draw(
                scope, textMetrics, mouseX, mouseY,
                0.0f, DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP, width, x, y
            )
        }
    }

    private fun drawKeybindButton(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int, toggle: Float) {
        val btnW = DropdownTheme.KEYBIND_WIDTH
        val btnH = DropdownTheme.KEYBIND_HEIGHT
        val btnX = width - DropdownTheme.MODULE_PADDING_X - btnW
        val btnY = (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f
        val radius = DropdownTheme.KEYBIND_RADIUS
        val btnHovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), absoluteX(btnX), absoluteY(btnY), btnW, btnH)
        keybindHoverAnim.run(if (btnHovered) 1.0f else 0.0f)
        val kbHover = keybindHoverAnim.value

        val keyText = if (listeningKeybind) "..." else formatCompactKeybind(module.keyBind)
        val textScale = if (keyText.length >= 3) 0.46f else 0.52f
        val textW = textMetrics.textWidth(keyText, textScale)
        val textH = textMetrics.textHeight(textScale)

        val surface: Color
        val outline: Color
        val text = DropdownTheme.keybindText(true)
        if (listeningKeybind) {
            surface = DropdownTheme.keybindSurface(true)
            outline = MD3Theme.withAlpha(MD3Theme.PRIMARY, 200)
        } else {
            val idleSurface = MD3Theme.SECONDARY_CONTAINER
            val activeSurface = MD3Theme.PRIMARY
            surface = MD3Theme.lerp(MD3Theme.lerp(idleSurface, activeSurface, toggle), MD3Theme.TEXT_PRIMARY, kbHover * 0.08f)
            var outline2 = MD3Theme.lerp(
                MD3Theme.withAlpha(MD3Theme.SECONDARY, 220),
                MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 235),
                toggle
            )
            outline2 = MD3Theme.lerp(outline2, MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 245), kbHover * 0.5f)
            outline = outline2
        }

        scope.roundRect(btnX, btnY, btnW, btnH, radius, surface)
        scope.outline(btnX, btnY, btnW, btnH, radius, 0.8f, outline)

        val textX = btnX + (btnW - textW) * 0.5f
        val textY = btnY + (btnH - textH) * 0.5f - 0.5f
        scope.text(keyText, textX, textY, textScale, text)
        if (module.bindMode == Module.BindMode.Hold && !listeningKeybind) {
            scope.rect(textX, textY + textH + 0.5f, textW, 0.75f, text)
        }
    }

    private fun formatCompactKeybind(keyCode: Int): String {
        if (keyCode == KeybindUtils.NONE) return "NONE"
        if (KeybindUtils.isMouseButton(keyCode)) return "M" + (KeybindUtils.decodeMouseButton(keyCode) + 1)
        val label = KeybindUtils.format(keyCode).trim()
        if (label.isEmpty()) return "?"

        val parts = label.split(Regex("[^A-Za-z0-9]+"))
        val initials = StringBuilder()
        for (part in parts) {
            if (part.isNotEmpty() && Character.isLetterOrDigit(part[0])) {
                initials.append(Character.toUpperCase(part[0]))
            }
            if (initials.length == 3) break
        }
        if (initials.length >= 2) return initials.toString()

        val compact = label.replace(Regex("[^A-Za-z0-9]"), "").uppercase(Locale.ROOT)
        if (compact.isNotEmpty()) return if (compact.length > 3) compact.substring(0, 3) else compact
        return if (label.length > 3) label.substring(0, 3) else label
    }

    private fun isKeybindButtonHovered(mouseX: Double, mouseY: Double): Boolean {
        val btnX = width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH
        val btnY = (DropdownTheme.MODULE_HEIGHT - DropdownTheme.KEYBIND_HEIGHT) * 0.5f
        return isHovered(
            mouseX, mouseY, absoluteX(btnX), absoluteY(btnY),
            DropdownTheme.KEYBIND_WIDTH, DropdownTheme.KEYBIND_HEIGHT
        )
    }

    private fun drawHiddenButton(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        val btnW = 18.0f
        val btnH = DropdownTheme.KEYBIND_HEIGHT
        val btnX = width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH - 4.0f - btnW
        val btnY = (DropdownTheme.MODULE_HEIGHT - btnH) / 2.0f
        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), absoluteX(btnX), absoluteY(btnY), btnW, btnH)
        if (!module.isHidden) {
            scope.roundRect(
                btnX, btnY, btnW, btnH, DropdownTheme.KEYBIND_RADIUS,
                MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, if (hovered) 0.12f else 0.0f)
            )
            val icon = IconChars.VISIBILITY
            val scale = 0.58f
            val iconW = textMetrics.textWidth(icon, scale, StaticFontLoader.ICONS)
            val iconH = textMetrics.textHeight(scale, StaticFontLoader.ICONS)
            scope.text(
                icon, btnX + (btnW - iconW) / 2.0f, btnY + (btnH - iconH) / 2.0f, scale,
                MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS
            )
        }
        if (hovered) {
            val hint = if (module.isHidden) {
                EpsilonTranslations.Module.HIDDEN.getTranslatedName()
            } else {
                EpsilonTranslations.Module.VISIBLE.getTranslatedName()
            }
            val hintScale = 0.42f
            val hintW = textMetrics.textWidth(hint, hintScale)
            val hintX = Mth.clamp(btnX + (btnW - hintW) * 0.5f, 2.0f, width - hintW - 2.0f)
            scope.text(hint, hintX, DropdownTheme.MODULE_HEIGHT + 1.0f, hintScale, MD3Theme.TEXT_MUTED)
        }
    }

    private fun isHiddenButtonHovered(mouseX: Double, mouseY: Double): Boolean {
        val btnW = 18.0f
        val btnH = DropdownTheme.KEYBIND_HEIGHT
        val btnX = width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH - 4.0f - btnW
        val btnY = (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f
        return isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), btnW, btnH)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (listeningKeybind) {
            module.keyBind = KeybindUtils.encodeMouseButton(button)
            listeningKeybind = false
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }

        if (isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT)) {
            if (isHiddenButtonHovered(mouseX, mouseY)) {
                module.isHidden = !module.isHidden
                DropdownScreen.react(ReisaDropdownCompanion.Action.MODULE_HIDDEN)
                return true
            }
            if (isKeybindButtonHovered(mouseX, mouseY)) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    listeningKeybind = true
                    DropdownScreen.react(ReisaDropdownCompanion.Action.KEY_BIND)
                    return true
                }
                if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                    module.bindMode = if (module.bindMode == Module.BindMode.Toggle) {
                        Module.BindMode.Hold
                    } else {
                        Module.BindMode.Toggle
                    }
                    DropdownScreen.react(ReisaDropdownCompanion.Action.KEY_BIND)
                    return true
                }
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                module.toggle()
                DropdownScreen.react(
                    if (module.isEnabled) ReisaDropdownCompanion.Action.TOGGLE_ON else ReisaDropdownCompanion.Action.TOGGLE_OFF
                )
                return true
            }
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (sectionRenderer.isEmpty()) {
                    return true
                }
                expanded = !expanded
                DropdownScreen.react(
                    if (expanded) ReisaDropdownCompanion.Action.PANEL_OPEN else ReisaDropdownCompanion.Action.PANEL_CLOSE
                )
                return true
            }
        }

        if (expanded && expandAnim.value > 0.5f) {
            if (sectionRenderer.mouseClicked(
                    mouseX, mouseY, button,
                    0.0f, DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP, width, x, y
                )
            ) {
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (expanded) {
            return sectionRenderer.mouseReleased(mouseX, mouseY, button)
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (listeningKeybind) {
            module.keyBind = if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_BACKSPACE) {
                KeybindUtils.NONE
            } else {
                keyCode
            }
            listeningKeybind = false
            DropdownScreen.react(
                if (keyCode == InputConstants.KEY_ESCAPE) ReisaDropdownCompanion.Action.CANCEL else ReisaDropdownCompanion.Action.CONFIRM
            )
            return true
        }

        if (expanded) {
            return sectionRenderer.keyPressed(keyCode, scanCode, modifiers)
        }
        return false
    }

    override fun charTyped(typedText: String): Boolean {
        if (expanded) {
            return sectionRenderer.charTyped(typedText)
        }
        return false
    }

    val isExpanded: Boolean
        get() = expanded

    fun hasListeningKeybind(): Boolean {
        return listeningKeybind || sectionRenderer.hasListeningKeybind()
    }

    fun hasFocusedInput(): Boolean {
        return sectionRenderer.hasFocusedInput()
    }

    private companion object {

        private fun createWidget(setting: Setting<*>): SettingWidget<*>? {
            if (setting is BoolSetting) return BoolWidget(setting)
            if (setting is IntSetting) return IntSliderWidget(setting)
            if (setting is DoubleSetting) return DoubleSliderWidget(setting)
            if (setting is EnumSetting<*>) return EnumWidget(setting)
            if (setting is ColorSetting) return ColorWidget(setting)
            if (setting is RegistryListSetting<*>) return RegistryListSettingWidget(setting)
            if (setting is KeybindSetting) return KeybindWidget(setting)
            if (setting is StringSetting) return StringWidget(setting)
            if (setting is ButtonSetting) return ButtonWidget(setting)
            if (setting is StringListSetting) return StringListSettingWidget(setting)
            return null
        }

    }

}
