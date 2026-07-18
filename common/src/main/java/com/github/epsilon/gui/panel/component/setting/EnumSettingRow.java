package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class EnumSettingRow extends SettingRow<EnumSetting<?>> {

    private final Animation dropdownAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180);
    private boolean dropdownOpen = false;

    public EnumSettingRow(EnumSetting<?> setting) {
        super(setting);
        dropdownAnimation.setStartValue(0.0f);
    }

    public void setDropdownOpen(boolean open) {
        this.dropdownOpen = open;
    }

    @Override
    public boolean hasActiveAnimation() {
        return !dropdownAnimation.isFinished();
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        float chipTextScale = 0.60f;
        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);
        UiRect chipBounds = getChipBounds(textRenderer, bounds).relativeTo(bounds);
        scope.chip(chipBounds, setting.getTranslatedValue(), chipTextScale, MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER, null, 0.58f, null);

        float chevronProgress = scope.animate(dropdownAnimation, dropdownOpen);
        float chevronSize = 3.0f;
        float chevronCenterX = chipBounds.right() - 7.5f;
        float chevronCenterY = chipBounds.y() + chipBounds.height() / 2.0f;
        scope.triangle(chevronCenterX, chevronCenterY, chevronSize, chevronProgress, MD3Theme.ON_SECONDARY_CONTAINER);
    }

    public UiRect getChipBounds(TextRenderer textRenderer, UiRect bounds) {
        return PanelElements.measureAssistChipBounds(textRenderer, bounds, setting.getTranslatedValue(), 0.60f, 8.0f, 10.0f, 96.0f);
    }

    @Override
    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return bounds.contains(event.x(), event.y()) && event.button() == 0;
    }

}
