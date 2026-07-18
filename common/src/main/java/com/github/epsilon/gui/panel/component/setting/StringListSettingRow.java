package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.StringListSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class StringListSettingRow extends SettingRow<StringListSetting> {

    public StringListSettingRow(StringListSetting setting) {
        super(setting);
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer,
                        UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        String summary = setting.size() + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName();
        float chipTextScale = 0.58f;

        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        UiRect chipBounds = PanelElements.measureAssistChipBounds(textRenderer, bounds, summary, chipTextScale, 8.0f, 12.0f, 94.0f).relativeTo(bounds);
        scope.chip(chipBounds, summary, chipTextScale, MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER,
                IconChars.ADD, 0.58f, StaticFontLoader.ICONS);
    }

    @Override
    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return bounds.contains(event.x(), event.y()) && event.button() == 0;
    }

}
