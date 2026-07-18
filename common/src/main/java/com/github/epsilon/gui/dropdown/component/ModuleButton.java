package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
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
    private final List<SettingSection> sections = new ArrayList<>();
    private final Map<String, Animation> sectionHoverAnimations = new HashMap<>();
    private final Map<String, Animation> sectionExpandAnimations = new HashMap<>();
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
        Map<Setting<?>, SettingWidget<?>> widgets = new HashMap<>();
        for (Setting<?> setting : module.getSettings()) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                widgets.put(setting, widget);
            }
        }

        String ownerKey = "module:" + module.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        for (SettingLayoutPlanner.Section plannedSection : SettingLayoutPlanner.plan(ownerKey, module.getSettings())) {
            List<SettingWidget<?>> sectionWidgets = new ArrayList<>();
            for (Setting<?> setting : plannedSection.settings()) {
                SettingWidget<?> widget = widgets.get(setting);
                if (widget != null) {
                    sectionWidgets.add(widget);
                }
            }
            if (!sectionWidgets.isEmpty()) {
                sections.add(new SettingSection(plannedSection, sectionWidgets));
            }
        }
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
        float height = DropdownTheme.SETTING_GAP + DropdownTheme.MODULE_ADDON_INFO_HEIGHT + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.hasHeader()) {
                height += DropdownTheme.GROUP_HEADER_HEIGHT;
                height += getGroupContentHeight(section) * getGroupExpandProgress(section);
                height += DropdownTheme.SETTING_GAP;
            } else {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (widget.isVisible()) {
                        height += widget.getHeight() + DropdownTheme.SETTING_GAP;
                    }
                }
            }
        }
        return height;
    }

    private float getSectionHeight(SettingSection section) {
        if (!section.hasHeader()) {
            float h = 0.0f;
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget.isVisible()) {
                    h += widget.getHeight() + DropdownTheme.SETTING_GAP;
                }
            }
            return h;
        }

        return DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP
                + getGroupContentHeight(section) * getGroupExpandProgress(section);
    }

    private float getGroupContentHeight(SettingSection section) {
        float h = DropdownTheme.GROUP_INSET;
        for (SettingWidget<?> widget : section.widgets()) {
            if (widget.isVisible()) {
                h += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
        return h;
    }

    private float getGroupExpandProgress(SettingSection section) {
        Animation animation = getGroupExpandAnimation(section);
        animation.run(section.isCollapsed() ? 0.0f : 1.0f);
        return animation.getValue();
    }

    private Animation getGroupExpandAnimation(SettingSection section) {
        return sectionExpandAnimations.computeIfAbsent(section.key(),
                k -> createGroupAnimation(DropdownTheme.ANIM_GROUP, section.isCollapsed() ? 0.0f : 1.0f));
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

        if (expand > 0.01f) {
            float settingY = DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP;
            if (expand > 0.5f) {
                drawAddonInfo(scope, textMetrics, settingY);
            }
            settingY += DropdownTheme.MODULE_ADDON_INFO_HEIGHT + DropdownTheme.SETTING_GAP;
            for (SettingSection section : sections) {
                float sectionH = getSectionHeight(section);
                if (section.hasHeader()) {
                    if (expand > 0.5f) {
                        drawSection(scope, textMetrics, mouseX, mouseY, section, settingY);
                    }
                } else {
                    var stack = scope.stack(new UiRect(
                            DropdownTheme.SETTING_INDENT,
                            settingY,
                            width - DropdownTheme.SETTING_INDENT * 2.0f,
                            sectionH
                    ));
                    for (SettingWidget<?> widget : section.widgets()) {
                        if (!widget.isVisible()) continue;
                        if (expand > 0.5f) {
                            stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP,
                                    (bounds, itemScope) -> widget.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds));
                        } else {
                            stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP);
                        }
                    }
                }
                settingY += sectionH;
            }
        }
    }

    private void drawAddonInfo(UiTree.Scope scope, UiTextMetrics textMetrics, float infoY) {
        float infoX = DropdownTheme.SETTING_INDENT;
        float infoH = DropdownTheme.MODULE_ADDON_INFO_HEIGHT;

        float scale = DropdownTheme.MODULE_ADDON_INFO_TEXT_SCALE;
        String addonLabel = EpsilonTranslations.Module.FROM.getTranslatedName() + " " + getAddonLabel();
        float textY = infoY + (infoH - textMetrics.textHeight(scale)) * 0.5f - 0.5f;
        scope.text(addonLabel, infoX + DropdownTheme.SETTING_PADDING_X, textY, scale, DropdownTheme.moduleAddonInfoText());
    }

    private void drawSection(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, SettingSection section, float sectionY) {
        float headerW = width - DropdownTheme.SETTING_INDENT * 2.0f;
        float headerX = DropdownTheme.SETTING_INDENT;
        float headerH = DropdownTheme.GROUP_HEADER_HEIGHT;

        Animation hoverAnim = sectionHoverAnimations.computeIfAbsent(section.key(), k -> createGroupAnimation(120L, 0.0f));
        hoverAnim.run(isHovered(mouseX, mouseY, absoluteX(headerX), absoluteY(sectionY), headerW, headerH) ? 1.0f : 0.0f);
        float hoverProgress = hoverAnim.getValue();

        float expandProgress = scope.animate(getGroupExpandAnimation(section), !section.isCollapsed());

        Color headerBg = MD3Theme.lerp(DropdownTheme.groupBackground(), DropdownTheme.groupBackgroundHover(), hoverProgress);
        float headerRadius = DropdownTheme.BUTTON_RADIUS;
        scope.roundRect(headerX, sectionY, headerW, headerH, headerRadius, headerBg);

        String label = section.title();
        float labelY = sectionY + (headerH - textMetrics.textHeight(DropdownTheme.GROUP_HEADER_TEXT_SCALE)) * 0.5f;
        scope.text(label, headerX + DropdownTheme.SETTING_PADDING_X, labelY, DropdownTheme.GROUP_HEADER_TEXT_SCALE, DropdownTheme.groupText());

        String countLabel = Integer.toString(section.widgets().size());
        float countWidth = textMetrics.textWidth(countLabel, DropdownTheme.GROUP_COUNT_TEXT_SCALE) + DropdownTheme.GROUP_COUNT_CHIP_PADDING * 2.0f;
        float countX = headerX + headerW - DropdownTheme.SETTING_PADDING_X - countWidth - 12.0f;
        float chipH = DropdownTheme.GROUP_COUNT_CHIP_HEIGHT;
        float countY = sectionY + (headerH - chipH) * 0.5f;
        scope.roundRect(countX, countY, countWidth, chipH, chipH / 2.0f, DropdownTheme.groupCountChip());
        float countTextY = countY + (chipH - textMetrics.textHeight(DropdownTheme.GROUP_COUNT_TEXT_SCALE)) * 0.5f;
        scope.text(countLabel, countX + DropdownTheme.GROUP_COUNT_CHIP_PADDING, countTextY, DropdownTheme.GROUP_COUNT_TEXT_SCALE, DropdownTheme.groupCountText());

        float chevronSize = 2.5f;
        float chevronCenterX = headerX + headerW - DropdownTheme.SETTING_PADDING_X - chevronSize;
        float chevronCenterY = sectionY + headerH * 0.5f;
        scope.triangle(chevronCenterX, chevronCenterY, chevronSize, expandProgress, DropdownTheme.groupChevron(hoverProgress));

        if (expandProgress > 0.001f) {
            float contentY = sectionY + headerH + DropdownTheme.SETTING_GAP;
            float contentHeight = getGroupContentHeight(section);
            float childY = contentY + DropdownTheme.GROUP_INSET;
            float childX = DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET;
            float childW = width - (DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET) * 2.0f;
            scope.scissorIf(expandProgress < 1.0f,
                    childX, contentY, childW, contentHeight * expandProgress, clippedScope -> {
                        var stack = clippedScope.stack(new UiRect(childX, childY, childW, contentHeight - DropdownTheme.GROUP_INSET));
                        for (SettingWidget<?> widget : section.widgets()) {
                            if (!widget.isVisible()) continue;
                            stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP,
                                    (bounds, itemScope) -> widget.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds));
                        }
                    });
        }
    }

    private Animation createGroupAnimation(long duration, float startValue) {
        Animation anim = new Animation(Easing.EASE_OUT_CUBIC, duration);
        anim.setStartValue(startValue);
        return anim;
    }

    private String getAddonLabel() {
        String addonId = module.getAddonId();
        return addonId == null || addonId.isBlank() ? "unknown" : addonId;
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
        float btnY = (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        boolean hovered = isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), btnW, btnH);
        if (!module.isHidden()) {
            scope.roundRect(btnX, btnY, btnW, btnH, DropdownTheme.KEYBIND_RADIUS, MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, hovered ? 0.12f : 0.0f));
            String icon = IconChars.VISIBILITY;
            float scale = 0.58f;
            float iconW = textMetrics.textWidth(icon, scale, StaticFontLoader.ICONS);
            float iconH = textMetrics.textHeight(scale, StaticFontLoader.ICONS);
            scope.text(icon, btnX + (btnW - iconW) * 0.5f, btnY + (btnH - iconH) * 0.5f - 1.0f, scale, MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS);
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

    private boolean isGroupHeaderHovered(double mouseX, double mouseY, float headerX, float headerY) {
        float headerW = width - DropdownTheme.SETTING_INDENT * 2.0f;
        return isHovered(mouseX, mouseY, headerX, headerY, headerW, DropdownTheme.GROUP_HEADER_HEIGHT);
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
                expanded = !expanded;
                DropdownScreen.INSTANCE.react(expanded
                        ? ReisaDropdownCompanion.Action.PANEL_OPEN
                        : ReisaDropdownCompanion.Action.PANEL_CLOSE);
                return true;
            }
        }

        if (expanded && expandAnim.getValue() > 0.5f) {
            float settingY = absoluteY(DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP);
            settingY += DropdownTheme.MODULE_ADDON_INFO_HEIGHT + DropdownTheme.SETTING_GAP;
            for (SettingSection section : sections) {
                if (section.hasHeader()) {
                    float headerX = absoluteX(DropdownTheme.SETTING_INDENT);
                    if (isGroupHeaderHovered(mouseX, mouseY, headerX, settingY)) {
                        section.toggleCollapsed();
                        if (section.isCollapsed()) {
                            blurInputs(section);
                        }
                        Managers.SOUND.playInUi(section.isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
                        DropdownScreen.INSTANCE.react(section.isCollapsed()
                                ? ReisaDropdownCompanion.Action.PANEL_CLOSE
                                : ReisaDropdownCompanion.Action.PANEL_OPEN);
                        return true;
                    }
                    if (!section.isCollapsed() && getGroupExpandProgress(section) >= 0.999f) {
                        for (SettingWidget<?> widget : section.widgets()) {
                            if (!widget.isVisible()) continue;
                            if (widget.mouseClicked(mouseX, mouseY, button)) {
                                return true;
                            }
                        }
                    }
                } else {
                    for (SettingWidget<?> widget : section.widgets()) {
                        if (!widget.isVisible()) continue;
                        if (widget.mouseClicked(mouseX, mouseY, button)) {
                            return true;
                        }
                    }
                }
                settingY += getSectionHeight(section);
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            for (SettingSection section : sections) {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.mouseReleased(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
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
            for (SettingSection section : sections) {
                if (section.hasHeader() && section.isCollapsed()) continue;
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (expanded) {
            for (SettingSection section : sections) {
                if (section.hasHeader() && section.isCollapsed()) continue;
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.charTyped(typedText)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    private void blurInputs(SettingSection section) {
        for (SettingWidget<?> widget : section.widgets()) {
            if (widget instanceof StringWidget sw && sw.isFocused()) {
                sw.blurInput();
            } else if (widget instanceof IntSliderWidget iw && iw.isFocused()) {
                iw.blurInput();
            } else if (widget instanceof DoubleSliderWidget dw && dw.isFocused()) {
                dw.blurInput();
            } else if (widget instanceof ColorWidget cw && cw.hasFocusedInput()) {
                cw.blurAllInputs();
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean hasListeningKeybind() {
        if (listeningKeybind) return true;
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget instanceof KeybindWidget kw && kw.isListening()) return true;
            }
        }
        return false;
    }

    public boolean hasFocusedInput() {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget instanceof StringWidget sw && sw.isFocused()) return true;
                if (widget instanceof IntSliderWidget iw && iw.isFocused()) return true;
                if (widget instanceof DoubleSliderWidget dw && dw.isFocused()) return true;
                if (widget instanceof ColorWidget cw && cw.hasFocusedInput()) return true;
            }
        }
        return false;
    }

    private record SettingSection(SettingLayoutPlanner.Section model, List<SettingWidget<?>> widgets) {
        private String key() {
            return model.key();
        }

        private String title() {
            return model.title();
        }

        private boolean hasHeader() {
            return model.hasHeader();
        }

        private boolean isCollapsed() {
            return model.isCollapsed();
        }

        private void toggleCollapsed() {
            model.toggleCollapsed();
        }
    }

}
