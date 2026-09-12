package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ModuleButton extends Component {

    private final Module module;
    private final SettingSectionRenderer sectionRenderer;
    private final Animation expandAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_EXPAND);
    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private final Animation keybindHoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean expanded;
    private boolean listeningKeybind;
    private int cachedHeightFrameId = Integer.MIN_VALUE;
    private float cachedHeight;

    public ModuleButton(Module module) {
        this.module = module;
        String ownerKey = "module:" + module.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        this.sectionRenderer = SettingSectionRenderer.create(ownerKey, module.getSettings(), ModuleButton::createWidget);
    }

    private static SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof BoolSetting s) return new BoolWidget(s);
        if (setting instanceof IntSetting s) return new IntSliderWidget(s);
        if (setting instanceof DoubleSetting s) return new DoubleSliderWidget(s);
        if (setting instanceof EnumSetting<?> s) return new EnumWidget(s);
        if (setting instanceof ColorSetting s) return new ColorWidget(s);
        if (setting instanceof RegistryListSetting<?> s) return new RegistryListSettingWidget(s);
        if (setting instanceof KeybindSetting s) return new KeybindWidget(s);
        if (setting instanceof StringSetting s) return new StringWidget(s);
        if (setting instanceof ButtonSetting s) return new ButtonWidget(s);
        if (setting instanceof StringListSetting s) return new StringListSettingWidget(s);
        return null;
    }

    @Override
    public float getHeight() {
        return computeHeight();
    }

    public float getHeightForFrame(int frameId) {
        if (frameId == Integer.MIN_VALUE) {
            return computeHeight();
        }
        if (cachedHeightFrameId != frameId) {
            cachedHeight = computeHeight();
            cachedHeightFrameId = frameId;
        }
        return cachedHeight;
    }

    private float computeHeight() {
        expandAnim.run(expanded ? 1.0f : 0.0f);
        float settingsHeight = computeSettingsHeight();
        return DropdownTheme.MODULE_HEIGHT + settingsHeight * expandAnim.getValue();
    }

    private float computeSettingsHeight() {
        return DropdownTheme.SETTING_GAP + sectionRenderer.height();
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        expandAnim.run(expanded ? 1.0f : 0.0f);
        toggleAnim.run(module.isEnabled() ? 1.0f : 0.0f);
        boolean headerHovered = isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT);
        hoverAnim.run(headerHovered ? 1.0f : 0.0f);

        float hover = hoverAnim.getValue();
        float toggle = toggleAnim.getValue();

        Color bg = MD3Theme.lerp(DropdownTheme.moduleDisabled(hover), DropdownTheme.moduleEnabled(hover), toggle);
        scope.rect(2.0f, 0.0f, width - 4.0f, DropdownTheme.MODULE_HEIGHT, bg);
        scope.rect(3.0f, DropdownTheme.MODULE_HEIGHT - 0.5f, width - 6.0f, 0.5f, DropdownTheme.moduleDivider());

        Color textColor = MD3Theme.lerp(DropdownTheme.moduleTextDisabled(hover), DropdownTheme.moduleTextEnabled(), toggle);
        float textY = (DropdownTheme.MODULE_HEIGHT - textMetrics.textHeight(DropdownTheme.MODULE_TEXT_SCALE)) * 0.5f;
        float leftX = DropdownTheme.MODULE_PADDING_X;
        scope.text(module.getTranslatedName(), leftX, textY, DropdownTheme.MODULE_TEXT_SCALE, textColor);

        drawKeybindButton(scope, textMetrics, mouseX, mouseY, toggle);
        drawHiddenButton(scope, textMetrics, mouseX, mouseY);

        float expand = expandAnim.getValue();

        if (expand > 0.5f) {
            sectionRenderer.draw(scope, textMetrics, mouseX, mouseY,
                    0.0f, DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP, width, x, y);
        }
    }

    private void drawKeybindButton(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float toggle) {
        float btnW = DropdownTheme.KEYBIND_WIDTH;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = width - DropdownTheme.MODULE_PADDING_X - btnW;
        float btnY = (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        float radius = DropdownTheme.KEYBIND_RADIUS;
        boolean btnHovered = isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), btnW, btnH);
        keybindHoverAnim.run(btnHovered ? 1.0f : 0.0f);
        float kbHover = keybindHoverAnim.getValue();

        String keyText = listeningKeybind ? "..." : formatCompactKeybind(module.getKeyBind());
        float textScale = keyText.length() >= 3 ? 0.46f : 0.52f;
        float textW = textMetrics.textWidth(keyText, textScale);
        float textH = textMetrics.textHeight(textScale);

        Color surface;
        Color outline;
        Color text = DropdownTheme.keybindText(true);
        if (listeningKeybind) {
            surface = DropdownTheme.keybindSurface(true);
            outline = MD3Theme.withAlpha(MD3Theme.PRIMARY, 200);
        } else {
            Color idleSurface = MD3Theme.SECONDARY_CONTAINER;
            Color activeSurface = MD3Theme.PRIMARY;
            surface = MD3Theme.lerp(idleSurface, activeSurface, toggle);
            surface = MD3Theme.lerp(surface, MD3Theme.TEXT_PRIMARY, kbHover * 0.08f);
            outline = MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.SECONDARY, 220), MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 235), toggle);
            outline = MD3Theme.lerp(outline, MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 245), kbHover * 0.5f);
        }

        scope.roundRect(btnX, btnY, btnW, btnH, radius, surface);
        scope.outline(btnX, btnY, btnW, btnH, radius, 0.8f, outline);

        float textX = btnX + (btnW - textW) * 0.5f;
        float textY = btnY + (btnH - textH) * 0.5f - 0.5f;
        scope.text(keyText, textX, textY, textScale, text);
        if (module.getBindMode() == Module.BindMode.Hold && !listeningKeybind) {
            scope.rect(textX, textY + textH + 0.5f, textW, 0.75f, text);
        }
    }

    private String formatCompactKeybind(int keyCode) {
        if (keyCode == KeybindUtils.NONE) return "NONE";
        if (KeybindUtils.isMouseButton(keyCode)) return "M" + (KeybindUtils.decodeMouseButton(keyCode) + 1);
        String label = KeybindUtils.format(keyCode).trim();
        if (label.isEmpty()) return "?";

        String[] parts = label.split("[^A-Za-z0-9]+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 3) break;
        }
        if (initials.length() >= 2) return initials.toString();

        String compact = label.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!compact.isEmpty()) return compact.length() > 3 ? compact.substring(0, 3) : compact;
        return label.length() > 3 ? label.substring(0, 3) : label;
    }

    private boolean isKeybindButtonHovered(double mouseX, double mouseY) {
        float btnX = width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH;
        float btnY = (DropdownTheme.MODULE_HEIGHT - DropdownTheme.KEYBIND_HEIGHT) * 0.5f;
        return isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), DropdownTheme.KEYBIND_WIDTH, DropdownTheme.KEYBIND_HEIGHT);
    }

    private void drawHiddenButton(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        float btnW = 18.0f;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH - 4.0f - btnW;
        float btnY = (DropdownTheme.MODULE_HEIGHT - btnH) / 2.0f;
        boolean hovered = isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), btnW, btnH);
        if (!module.isHidden()) {
            scope.roundRect(btnX, btnY, btnW, btnH, DropdownTheme.KEYBIND_RADIUS, MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, hovered ? 0.12f : 0.0f));
            String icon = IconChars.VISIBILITY;
            float scale = 0.58f;
            float iconW = textMetrics.textWidth(icon, scale, StaticFontLoader.ICONS);
            float iconH = textMetrics.textHeight(scale, StaticFontLoader.ICONS);
            scope.text(icon, btnX + (btnW - iconW) / 2.0f, btnY + (btnH - iconH) / 2.0f, scale, MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS);
        }
        if (hovered) {
            String hint = module.isHidden() ? EpsilonTranslations.Module.HIDDEN.getTranslatedName() : EpsilonTranslations.Module.VISIBLE.getTranslatedName();
            float hintScale = 0.42f;
            float hintW = textMetrics.textWidth(hint, hintScale);
            float hintX = Mth.clamp(btnX + (btnW - hintW) * 0.5f, 2.0f, width - hintW - 2.0f);
            scope.text(hint, hintX, DropdownTheme.MODULE_HEIGHT + 1.0f, hintScale, MD3Theme.TEXT_MUTED);
        }
    }

    private boolean isHiddenButtonHovered(double mouseX, double mouseY) {
        float btnW = 18.0f;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH - 4.0f - btnW;
        float btnY = (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        return isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), btnW, btnH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listeningKeybind) {
            module.setKeyBind(KeybindUtils.encodeMouseButton(button));
            listeningKeybind = false;
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }

        if (isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT)) {
            if (isHiddenButtonHovered(mouseX, mouseY)) {
                module.setHidden(!module.isHidden());
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.MODULE_HIDDEN);
                return true;
            }
            if (isKeybindButtonHovered(mouseX, mouseY)) {
                if (button == 0) {
                    listeningKeybind = true;
                    DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.KEY_BIND);
                    return true;
                }
                if (button == 2) {
                    module.setBindMode(module.getBindMode() == Module.BindMode.Toggle ? Module.BindMode.Hold : Module.BindMode.Toggle);
                    DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.KEY_BIND);
                    return true;
                }
            }
            if (button == 0) {
                module.toggle();
                DropdownScreen.INSTANCE.react(module.isEnabled()
                        ? ReisaDropdownCompanion.Action.TOGGLE_ON
                        : ReisaDropdownCompanion.Action.TOGGLE_OFF);
                return true;
            }
            if (button == 1) {
                if (sectionRenderer.isEmpty()) {
                    return true;
                }
                expanded = !expanded;
                DropdownScreen.INSTANCE.react(expanded
                        ? ReisaDropdownCompanion.Action.PANEL_OPEN
                        : ReisaDropdownCompanion.Action.PANEL_CLOSE);
                return true;
            }
        }

        if (expanded && expandAnim.getValue() > 0.5f) {
            if (sectionRenderer.mouseClicked(mouseX, mouseY, button,
                    0.0f, DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP, width, x, y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            return sectionRenderer.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningKeybind) {
            module.setKeyBind(keyCode == 256 || keyCode == 259 ? KeybindUtils.NONE : keyCode);
            listeningKeybind = false;
            DropdownScreen.INSTANCE.react(keyCode == 256
                    ? ReisaDropdownCompanion.Action.CANCEL
                    : ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }

        if (expanded) {
            return sectionRenderer.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (expanded) {
            return sectionRenderer.charTyped(typedText);
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean hasListeningKeybind() {
        return listeningKeybind || sectionRenderer.hasListeningKeybind();
    }

    public boolean hasFocusedInput() {
        return sectionRenderer.hasFocusedInput();
    }

}
