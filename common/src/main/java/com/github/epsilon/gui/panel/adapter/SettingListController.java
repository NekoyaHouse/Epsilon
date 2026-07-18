package com.github.epsilon.gui.panel.adapter;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.panel.component.setting.*;
import com.github.epsilon.gui.panel.popup.*;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingListController implements AutoCloseable {

    private static final float GROUP_HEADER_HEIGHT = 30.0f;
    private static final float GROUP_ROW_INSET = 4.0f;
    private static final float GROUP_OUTLINE_INSET = 1.0f;
    private static final float GROUP_COUNT_CHIP_HEIGHT = 14.0f;

    private final PanelPopupHost popupHost;
    private final TextRenderer measureTextRenderer = TextRenderer.create();
    private final Map<Setting<?>, SettingRow<?>> rowCache = new HashMap<>();
    private final Map<String, Animation> sectionHoverAnimations = new HashMap<>();
    private final Map<String, Animation> sectionExpandAnimations = new HashMap<>();
    private final List<SettingEntry> settingEntries = new ArrayList<>();
    private final List<SectionEntry> sectionEntries = new ArrayList<>();

    private SettingEntry draggingSliderEntry;
    private EnumSettingRow activeEnumRow;

    public SettingListController(PanelPopupHost popupHost) {
        this.popupHost = popupHost;
    }

    public PanelPopupHost getPopupHost() {
        return popupHost;
    }

    public boolean isPopupHovered(int mouseX, int mouseY) {
        return popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
    }

    public void prepareLayout(List<Setting<?>> settings) {
        prepareLayout(null, settings);
    }

    public void prepareLayout(String ownerKey, List<Setting<?>> settings) {
        closeRowsNotIn(settings);
        List<String> visibleSections = buildSections(ownerKey, settings).stream()
                .filter(SettingLayoutPlanner.Section::hasHeader)
                .map(SettingLayoutPlanner.Section::key)
                .toList();
        sectionHoverAnimations.keySet().removeIf(key -> !visibleSections.contains(key));
        sectionExpandAnimations.keySet().removeIf(key -> !visibleSections.contains(key));
        settingEntries.clear();
        sectionEntries.clear();
    }

    public float getContentHeight(List<Setting<?>> settings) {
        return getContentHeight(null, settings);
    }

    public float getContentHeight(String ownerKey, List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return 0.0f;
        }

        float height = 0.0f;
        for (SettingLayoutPlanner.Section section : buildSections(ownerKey, settings)) {
            if (section.hasHeader()) {
                height += getSectionHeight(section) + MD3Theme.ROW_GAP;
            } else {
                for (Setting<?> setting : section.settings()) {
                    SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
                    if (row != null) {
                        height += row.getHeight() + MD3Theme.ROW_GAP;
                    }
                }
            }
        }
        return height;
    }

    public void layoutRows(List<Setting<?>> settings, UiRect viewport, float scroll, float rowWidth,
                           UiTree.Scope scope, TextRenderer textRenderer, int mouseX, int mouseY,
                           RowRenderCallback callback) {
        layoutRows(null, settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback);
    }

    public void layoutRows(String ownerKey, List<Setting<?>> settings, UiRect viewport, float scroll, float rowWidth,
                           UiTree.Scope scope, TextRenderer textRenderer, int mouseX, int mouseY,
                           RowRenderCallback callback) {
        prepareLayout(ownerKey, settings);

        if (activeEnumRow != null && popupHost.getActivePopup() == null) {
            activeEnumRow.setDropdownOpen(false);
            activeEnumRow = null;
        }

        appendRows(ownerKey, settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback);
    }

    public void appendRows(List<Setting<?>> settings, UiRect viewport, float scroll, float rowWidth,
                           UiTree.Scope scope, TextRenderer textRenderer, int mouseX, int mouseY,
                           RowRenderCallback callback) {
        appendRows(null, settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback);
    }

    public void appendRows(String ownerKey, List<Setting<?>> settings, UiRect viewport, float scroll, float rowWidth,
                           UiTree.Scope scope, TextRenderer textRenderer, int mouseX, int mouseY,
                           RowRenderCallback callback) {
        float rowY = viewport.y() - scroll;
        for (SettingLayoutPlanner.Section section : buildSections(ownerKey, settings)) {
            if (section.hasHeader()) {
                UiRect sectionBounds = new UiRect(viewport.x(), rowY, rowWidth, getSectionHeight(section));
                UiRect headerBounds = new UiRect(sectionBounds.x(), sectionBounds.y(), sectionBounds.width(), GROUP_HEADER_HEIGHT);
                sectionEntries.add(new SectionEntry(section, headerBounds));
                buildSectionCard(scope, textRenderer, section, sectionBounds, headerBounds, mouseX, mouseY);

                if (!section.isCollapsed()) {
                    float childY = sectionBounds.y() + GROUP_HEADER_HEIGHT + GROUP_ROW_INSET;
                    float childWidth = Math.max(0.0f, sectionBounds.width() - GROUP_ROW_INSET * 2.0f);
                    for (Setting<?> setting : section.settings()) {
                        SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
                        if (row == null) {
                            continue;
                        }

                        UiRect rowBounds = new UiRect(sectionBounds.x() + GROUP_ROW_INSET, childY, childWidth, row.getHeight());
                        settingEntries.add(new SettingEntry(row, rowBounds));
                        callback.render(setting, row, rowBounds);
                        childY += row.getHeight() + MD3Theme.ROW_GAP;
                    }
                }

                rowY += sectionBounds.height() + MD3Theme.ROW_GAP;
                continue;
            }

            for (Setting<?> setting : section.settings()) {
                SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
                if (row == null) {
                    continue;
                }
                UiRect rowBounds = new UiRect(viewport.x(), rowY, rowWidth, row.getHeight());
                settingEntries.add(new SettingEntry(row, rowBounds));
                callback.render(setting, row, rowBounds);
                rowY += row.getHeight() + MD3Theme.ROW_GAP;
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick, UiRect popupBounds) {
        return mouseClicked(event, isDoubleClick, popupBounds, null);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick, UiRect popupBounds, RowClickInterceptor interceptor) {
        if (event.button() != 0) {
            return false;
        }

        clearFocus();
        for (SectionEntry entry : sectionEntries) {
            if (entry.bounds().contains(event.x(), event.y())) {
                entry.section().toggleCollapsed();
                draggingSliderEntry = null;
                Managers.SOUND.playInUi(entry.section().isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
                return true;
            }
        }

        for (SettingEntry entry : settingEntries) {
            if (interceptor != null && interceptor.handle(entry.row, entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof IntSettingRow intRow && intRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = intRow.isDragging() ? entry : null;
                return true;
            }
            if (entry.row instanceof DoubleSettingRow doubleRow && doubleRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = doubleRow.isDragging() ? entry : null;
                return true;
            }
            if (entry.row instanceof EnumSettingRow enumRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createEnumPopup(enumRow, entry.bounds, popupBounds));
                enumRow.setDropdownOpen(true);
                if (activeEnumRow != null && activeEnumRow != enumRow) {
                    activeEnumRow.setDropdownOpen(false);
                }
                activeEnumRow = enumRow;
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof ColorSettingRow colorRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createColorPopup(colorRow, entry.bounds, popupBounds));
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof StringListSettingRow listRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                PanelPopupHost.Popup popup = createStringListSettingPopup(listRow, popupBounds);
                if (popup != null) popupHost.open(popup);
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof RegistryListSettingRow listRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createRegistryListSettingPopup(listRow, popupBounds));
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null;
                return true;
            }
        }

        draggingSliderEntry = null;
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSliderEntry == null) {
            return false;
        }

        draggingSliderEntry.row.mouseReleased(draggingSliderEntry.bounds, event);
        draggingSliderEntry = null;
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (draggingSliderEntry == null || event.button() != 0) {
            return false;
        }
        if (draggingSliderEntry.row instanceof IntSettingRow intRow) {
            intRow.updateFromMouse(draggingSliderEntry.bounds, event.x());
            return true;
        }
        if (draggingSliderEntry.row instanceof DoubleSettingRow doubleRow) {
            doubleRow.updateFromMouse(draggingSliderEntry.bounds, event.x());
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.charTyped(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.preeditUpdated(event)) {
                return true;
            }
        }
        return false;
    }

    public void clearFocus() {
        draggingSliderEntry = null;
        for (SettingRow<?> row : rowCache.values()) {
            row.setFocused(false);
        }
    }

    public void clearAll() {
        clearFocus();
        rowCache.values().forEach(SettingRow::close);
        settingEntries.clear();
        rowCache.clear();
        sectionHoverAnimations.clear();
        sectionExpandAnimations.clear();
        if (activeEnumRow != null) {
            activeEnumRow.setDropdownOpen(false);
            activeEnumRow = null;
        }
    }

    public void resetTransientState() {
        clearFocus();
        if (activeEnumRow != null) {
            activeEnumRow.setDropdownOpen(false);
            activeEnumRow = null;
        }
    }

    @Override
    public void close() {
        clearAll();
        measureTextRenderer.close();
    }

    public boolean hasActiveAnimations() {
        return sectionHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || sectionExpandAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
    }

    private void buildSectionCard(UiTree.Scope scope, TextRenderer textRenderer, SettingLayoutPlanner.Section section,
                                  UiRect groupBounds, UiRect headerBounds, int mouseX, int mouseY) {
        Animation hoverAnimation = sectionHoverAnimations.computeIfAbsent(section.key(), ignored -> createAnimation(120L, 0.0f));
        Animation expandAnimation = sectionExpandAnimations.computeIfAbsent(section.key(), ignored -> createAnimation(180L, section.isCollapsed() ? 0.0f : 1.0f));
        float hoverProgress = scope.animate(hoverAnimation, headerBounds.contains(mouseX, mouseY));
        float expandProgress = scope.animate(expandAnimation, !section.isCollapsed());

        scope.pushAbsolute(groupBounds, group -> {
            UiRect localHeader = headerBounds.relativeTo(groupBounds);
            group.roundRect(0.0f, 0.0f, groupBounds.width(), groupBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.OUTLINE_SOFT);
            group.roundRect(
                    GROUP_OUTLINE_INSET,
                    GROUP_OUTLINE_INSET,
                    groupBounds.width() - GROUP_OUTLINE_INSET * 2.0f,
                    groupBounds.height() - GROUP_OUTLINE_INSET * 2.0f,
                    Math.max(1.0f, MD3Theme.CARD_RADIUS - GROUP_OUTLINE_INSET),
                    MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_LOW, MD3Theme.SURFACE_CONTAINER, expandProgress)
            );
            if (hoverProgress > 0.01f) {
                group.roundRect(localHeader.x(), localHeader.y(), localHeader.width(), localHeader.height(), MD3Theme.CARD_RADIUS,
                        MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, MD3Theme.isLightTheme() ? 10 : 14));
            }

            float labelScale = 0.66f;
            String label = trimToWidth(section.title(), labelScale, headerBounds.width() - 74.0f, textRenderer);
            float labelY = localHeader.y() + (GROUP_HEADER_HEIGHT - textRenderer.getHeight(labelScale)) / 2.0f;
            group.text(label, localHeader.x() + MD3Theme.ROW_CONTENT_INSET + 2.0f, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

            String countLabel = Integer.toString(section.settings().size());
            float countScale = 0.46f;
            float countWidth = textRenderer.getWidth(countLabel, countScale) + 10.0f;
            float countX = localHeader.x() + localHeader.width() - MD3Theme.ROW_TRAILING_INSET - 20.0f - countWidth;
            float countY = localHeader.y() + (GROUP_HEADER_HEIGHT - GROUP_COUNT_CHIP_HEIGHT) / 2.0f;
            group.roundRect(countX, countY, countWidth, GROUP_COUNT_CHIP_HEIGHT, GROUP_COUNT_CHIP_HEIGHT / 2.0f,
                    MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210));
            group.text(countLabel,
                    countX + (countWidth - textRenderer.getWidth(countLabel, countScale)) / 2.0f,
                    countY + (GROUP_COUNT_CHIP_HEIGHT - textRenderer.getHeight(countScale)) / 2.0f,
                    countScale,
                    MD3Theme.ON_SECONDARY_CONTAINER);

            float chevronSize = 3.0f;
            float chevronCenterX = localHeader.x() + localHeader.width() - MD3Theme.ROW_TRAILING_INSET - chevronSize - 3.0f;
            float chevronCenterY = localHeader.y() + GROUP_HEADER_HEIGHT / 2.0f;
            group.triangle(chevronCenterX, chevronCenterY, chevronSize, expandProgress, MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress));
        });
    }

    private List<SettingLayoutPlanner.Section> buildSections(String ownerKey, List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        return ownerKey == null || ownerKey.isBlank()
                ? SettingLayoutPlanner.plan(settings)
                : SettingLayoutPlanner.plan(ownerKey, settings);
    }

    private float getSectionHeight(SettingLayoutPlanner.Section section) {
        if (!section.hasHeader()) {
            return 0.0f;
        }
        if (section.isCollapsed()) {
            return GROUP_HEADER_HEIGHT;
        }

        float height = GROUP_HEADER_HEIGHT + GROUP_ROW_INSET * 2.0f;
        for (Setting<?> setting : section.settings()) {
            SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
            if (row != null) {
                height += row.getHeight() + MD3Theme.ROW_GAP;
            }
        }
        return height;
    }

    private Animation createAnimation(long duration, float startValue) {
        Animation animation = new Animation(Easing.EASE_OUT_CUBIC, duration);
        animation.setStartValue(startValue);
        return animation;
    }

    private void closeRowsNotIn(List<Setting<?>> settings) {
        rowCache.entrySet().removeIf(entry -> {
            boolean remove = settings == null || !settings.contains(entry.getKey());
            if (remove) {
                entry.getValue().close();
            }
            return remove;
        });
    }

    private String trimToWidth(String value, float scale, float width, TextRenderer textRenderer) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (textRenderer.getWidth(value, scale) <= width) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = textRenderer.getWidth(ellipsis, scale);
        if (ellipsisWidth >= width) {
            return ellipsis;
        }
        for (int length = value.length() - 1; length >= 0; length--) {
            String candidate = value.substring(0, length) + ellipsis;
            if (textRenderer.getWidth(candidate, scale) <= width) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private EnumSelectPopup createEnumPopup(EnumSettingRow enumRow, UiRect rowBounds, UiRect popupBounds) {
        UiRect chipBounds = enumRow.getChipBounds(measureTextRenderer, rowBounds);
        int optionCount = enumRow.getSetting().getModes().length;
        int visibleCount = Math.min(optionCount, EnumSelectPopup.MAX_VISIBLE_ITEMS);
        float popupHeight = visibleCount * 24.0f + 12.0f;
        float popupWidth = Math.max(108.0f, chipBounds.width() + 24.0f);
        float popupX = Math.max(popupBounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, chipBounds.right() - popupWidth);
        float popupY = chipBounds.bottom() + 4.0f;
        float maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET;
        if (popupY + popupHeight > maxBottom) {
            popupY = chipBounds.y() - popupHeight - 4.0f;
        }
        return new EnumSelectPopup(new UiRect(popupX, popupY, popupWidth, popupHeight), enumRow.getSetting());
    }

    private ColorPickerPopup createColorPopup(ColorSettingRow colorRow, UiRect rowBounds, UiRect popupBounds) {
        UiRect swatchBounds = colorRow.getSwatchBounds(rowBounds);
        int channelCount = colorRow.getSetting().isAllowAlpha() ? 4 : 3;
        float popupWidth = 156.0f;
        float popupHeight = 58.0f + channelCount * 24.0f;
        float popupX = Math.max(popupBounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, swatchBounds.right() - popupWidth);
        float popupY = swatchBounds.bottom() + 4.0f;
        float maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET;
        if (popupY + popupHeight > maxBottom) {
            popupY = swatchBounds.y() - popupHeight - 4.0f;
        }
        return new ColorPickerPopup(new UiRect(popupX, popupY, popupWidth, popupHeight), swatchBounds, colorRow.getSetting());
    }

    // --- List setting popup factories ---

    private PanelPopupHost.Popup createStringListSettingPopup(StringListSettingRow row, UiRect popupBounds) {
        UiRect bounds = popupHost.getCenteredBounds(Math.min(300.0f, popupBounds.width() - 24.0f), Math.min(220.0f, popupBounds.height() - 24.0f));
        var setting = row.getSetting();
        return new StringListSelectPopup(bounds, setting, setting::add, setting::remove);
    }

    private PanelPopupHost.Popup createRegistryListSettingPopup(RegistryListSettingRow row, UiRect popupBounds) {
        UiRect bounds = popupHost.getCenteredBounds(Math.min(360.0f, popupBounds.width() - 24.0f), Math.min(246.0f, popupBounds.height() - 24.0f));
        return RegistryListSelectPopup.create(bounds, row.getSetting());
    }

    @FunctionalInterface
    public interface RowRenderCallback {
        void render(Setting<?> setting, SettingRow<?> row, UiRect rowBounds);
    }

    @FunctionalInterface
    public interface RowClickInterceptor {
        boolean handle(SettingRow<?> row, UiRect rowBounds, MouseButtonEvent event, boolean isDoubleClick);
    }

    private record SettingEntry(SettingRow<?> row, UiRect bounds) {
    }

    private record SectionEntry(SettingLayoutPlanner.Section section, UiRect bounds) {
    }

}
