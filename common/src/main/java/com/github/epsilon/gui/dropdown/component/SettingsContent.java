package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
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
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsContent {

    private final List<SettingSection> sections = new ArrayList<>();
    private final Map<String, Animation> sectionHoverAnimations = new HashMap<>();
    private final Map<String, Animation> sectionExpandAnimations = new HashMap<>();
    private int cachedContentHeightFrameId = Integer.MAX_VALUE;
    private float cachedContentHeight;
    private final List<Float> cachedSectionHeights = new ArrayList<>();

    public SettingsContent(List<Setting<?>> settings) {
        this("dropdown:" + System.identityHashCode(settings), settings);
    }

    public SettingsContent(String ownerKey, List<Setting<?>> settings) {
        List<SettingLayoutPlanner.Section> plannedSections = SettingLayoutPlanner.plan(ownerKey, settings);
        Map<Setting<?>, SettingWidget<?>> widgets = new HashMap<>();
        for (Setting<?> setting : settings) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                widgets.put(setting, widget);
            }
        }

        for (SettingLayoutPlanner.Section plannedSection : plannedSections) {
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

    public static SettingWidget<?> createWidget(Setting<?> setting) {
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

    public float computeContentHeight() {
        return computeContentHeightUncached();
    }

    public float computeContentHeight(int frameId) {
        if (frameId == Integer.MIN_VALUE) {
            return computeContentHeightUncached();
        }
        if (sections.isEmpty()) return DropdownTheme.MODULE_HEIGHT;
        if (cachedContentHeightFrameId == frameId) {
            return cachedContentHeight;
        }

        cachedSectionHeights.clear();
        float height = computeContentHeightInto(cachedSectionHeights);
        cachedContentHeightFrameId = frameId;
        cachedContentHeight = height;
        return cachedContentHeight;
    }

    private float computeContentHeightUncached() {
        return computeContentHeightInto(null);
    }

    private float computeContentHeightInto(List<Float> sectionHeights) {
        if (sections.isEmpty()) return DropdownTheme.MODULE_HEIGHT;
        float height = DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            float sectionHeight = getSectionHeight(section);
            if (sectionHeights != null) {
                sectionHeights.add(sectionHeight);
            }
            height += sectionHeight;
        }
        return height;
    }

    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float panelX, float contentY, float panelWidth) {
        draw(scope, textMetrics, mouseX, mouseY, panelX, contentY, panelWidth, Integer.MIN_VALUE);
    }

    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float panelX, float contentY, float panelWidth, int frameId) {
        if (sections.isEmpty()) {
            String label = EpsilonTranslations.Gui.ADDON_NO_SETTINGS.getTranslatedName();
            float labelScale = 0.58f;
            float textW = textMetrics.textWidth(label, labelScale);
            scope.text(label, panelX + (panelWidth - textW) * 0.5f, contentY + 8.0f, labelScale, MD3Theme.TEXT_MUTED);
            return;
        }

        if (frameId == Integer.MIN_VALUE) {
            cachedSectionHeights.clear();
            computeContentHeightInto(cachedSectionHeights);
        } else {
            computeContentHeight(frameId);
        }
        float currentY = contentY + DropdownTheme.SETTING_GAP;
        for (int index = 0; index < sections.size(); index++) {
            SettingSection section = sections.get(index);
            float sectionHeight = cachedSectionHeights.get(index);
            if (section.hasHeader()) {
                drawSection(scope, textMetrics, mouseX, mouseY, section, panelX, currentY, panelWidth);
            } else {
                var stack = scope.stack(new UiRect(
                        panelX + DropdownTheme.SETTING_INDENT,
                        currentY,
                        panelWidth - DropdownTheme.SETTING_INDENT * 2.0f,
                        sectionHeight
                ));
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP,
                            (bounds, itemScope) -> widget.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds));
                }
            }
            currentY += sectionHeight;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        float currentY = contentY + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.hasHeader()) {
                float headerX = panelX + DropdownTheme.SETTING_INDENT;
                float headerW = panelWidth - DropdownTheme.SETTING_INDENT * 2.0f;
                if (isHovered(mouseX, mouseY, headerX, currentY, headerW, DropdownTheme.GROUP_HEADER_HEIGHT)) {
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
                if (canInteractWith(section)) {
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
            currentY += getSectionHeight(section);
        }
        blurAllInputs();
        return false;
    }

    private void blurAllInputs() {
        for (SettingSection section : sections) {
            blurInputs(section);
        }
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

    public boolean mouseReleased(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        for (SettingSection section : sections) {
            if (section.hasHeader() && section.isCollapsed()) continue;
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                if (widget.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SettingSection section : sections) {
            if (section.hasHeader() && section.isCollapsed()) continue;
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean charTyped(String typedText) {
        for (SettingSection section : sections) {
            if (section.hasHeader() && section.isCollapsed()) continue;
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                if (widget.charTyped(typedText)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasActiveInput() {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget instanceof KeybindWidget kw && kw.isListening()) return true;
                if (widget instanceof StringWidget sw && sw.isFocused()) return true;
                if (widget instanceof IntSliderWidget iw && iw.isFocused()) return true;
                if (widget instanceof DoubleSliderWidget dw && dw.isFocused()) return true;
                if (widget instanceof ColorWidget cw && cw.hasFocusedInput()) return true;
            }
        }
        return false;
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
                ignored -> createGroupAnimation(section.isCollapsed() ? 0.0f : 1.0f));
    }

    private void drawSection(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, SettingSection section, float panelX, float sectionY, float panelWidth) {
        Animation hoverAnim = sectionHoverAnimations.computeIfAbsent(section.key(), ignored -> createGroupAnimation(0.0f));
        float headerW = panelWidth - DropdownTheme.SETTING_INDENT * 2.0f;
        float headerX = panelX + DropdownTheme.SETTING_INDENT;
        float headerH = DropdownTheme.GROUP_HEADER_HEIGHT;
        hoverAnim.run(isHovered(mouseX, mouseY, headerX, sectionY, headerW, headerH) ? 1.0f : 0.0f);

        float hoverProgress = hoverAnim.getValue();
        float expandProgress = scope.animate(getGroupExpandAnimation(section), !section.isCollapsed());
        scope.roundRect(headerX, sectionY, headerW, headerH, DropdownTheme.BUTTON_RADIUS,
                MD3Theme.lerp(DropdownTheme.groupBackground(), DropdownTheme.groupBackgroundHover(), hoverProgress));

        String label = trimToWidth(section.title(), DropdownTheme.GROUP_HEADER_TEXT_SCALE, headerW - 74.0f, textMetrics);
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

        scope.triangle(headerX + headerW - DropdownTheme.SETTING_PADDING_X - 2.5f,
                sectionY + headerH * 0.5f, 2.5f, expandProgress, DropdownTheme.groupChevron(hoverProgress));

        if (expandProgress > 0.001f) {
            float contentY = sectionY + headerH + DropdownTheme.SETTING_GAP;
            float contentHeight = getGroupContentHeight(section);
            float childY = contentY + DropdownTheme.GROUP_INSET;
            float childX = panelX + DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET;
            float childW = panelWidth - (DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET) * 2.0f;
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

    private Animation createGroupAnimation(float startValue) {
        Animation anim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_GROUP);
        anim.setStartValue(startValue);
        return anim;
    }

    private boolean canInteractWith(SettingSection section) {
        return !section.isCollapsed() && getGroupExpandProgress(section) >= 0.999f;
    }

    private boolean isHovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private String trimToWidth(String value, float scale, float maxWidth, UiTextMetrics textMetrics) {
        if (value == null || value.isEmpty()) return "";
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = textMetrics.textWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        for (int len = value.length() - 1; len >= 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
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
