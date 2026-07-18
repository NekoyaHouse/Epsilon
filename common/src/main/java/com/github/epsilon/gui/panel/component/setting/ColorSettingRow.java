package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ColorSettingRow extends SettingRow<ColorSetting> {

    public ColorSettingRow(ColorSetting setting) {
        super(setting);
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);
        UiRect swatchBounds = getSwatchBounds(bounds).relativeTo(bounds);
        scope.roundRect(swatchBounds.x(), swatchBounds.y(), swatchBounds.width(), swatchBounds.height(), 5.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST);
        scope.roundRect(swatchBounds.x(), swatchBounds.y(), swatchBounds.width(), swatchBounds.height(), 5.0f, setting.getValue());
        scope.roundRect(swatchBounds.x(), swatchBounds.y(), swatchBounds.width(), swatchBounds.height(), 5.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, 58));
    }

    public UiRect getSwatchBounds(UiRect bounds) {
        float swatchX = bounds.right() - MD3Theme.ROW_TRAILING_INSET - 12.0f;
        float swatchY = bounds.y() + (bounds.height() - 12.0f) / 2.0f;
        return new UiRect(swatchX, swatchY, 12.0f, 12.0f);
    }

    @Override
    public boolean mouseClicked(UiRect bounds, net.minecraft.client.input.MouseButtonEvent event, boolean isDoubleClick) {
        return event.button() == 0 && bounds.contains(event.x(), event.y());
    }

}
