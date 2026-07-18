package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

public class KeybindSettingRow extends SettingRow<KeybindSetting> {

    private final Animation chipHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation focusAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150L);

    private boolean listening;

    public KeybindSettingRow(KeybindSetting setting) {
        super(setting);
        chipHoverAnimation.setStartValue(0.0f);
        focusAnimation.setStartValue(0.0f);
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;

        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        UiRect absoluteChipBounds = getChipBounds(bounds);
        UiRect chipBounds = absoluteChipBounds.relativeTo(bounds);
        chipHoverAnimation.run(absoluteChipBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
        focusAnimation.run(listening ? 1.0f : 0.0f);
        float chipHover = chipHoverAnimation.getValue();
        float focusProgress = focusAnimation.getValue();

        float radius = 8.0f;

        if (focusProgress > 0.01f) {
            float haloInset = 1.5f * focusProgress;
            scope.roundRect(chipBounds.x() - haloInset, chipBounds.y() - haloInset, chipBounds.width() + haloInset * 2.0f, chipBounds.height() + haloInset * 2.0f, radius + haloInset, MD3Theme.withAlpha(MD3Theme.PRIMARY, (int) (28 * focusProgress)));
        }

        Color background = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, focusProgress);
        Color foreground = MD3Theme.lerp(MD3Theme.ON_SECONDARY_CONTAINER, MD3Theme.ON_PRIMARY_CONTAINER, focusProgress);
        scope.roundRect(chipBounds.x(), chipBounds.y(), chipBounds.width(), chipBounds.height(), radius, background);

        if (chipHover > 0.01f) {
            int hoverAlpha = listening ? 18 : 12;
            scope.roundRect(chipBounds.x(), chipBounds.y(), chipBounds.width(), chipBounds.height(), radius, MD3Theme.withAlpha(foreground, (int) (hoverAlpha * chipHover)));
        }

        String label = listening ? "..." : formatKeybind(setting.getValue());
        float chipTextScale = 0.52f;
        float textWidth = textRenderer.getWidth(label, chipTextScale);
        float textHeight = textRenderer.getHeight(chipTextScale);
        float textX = chipBounds.x() + (chipBounds.width() - textWidth) / 2.0f;
        float textY = chipBounds.y() + (chipBounds.height() - textHeight) / 2.0f;
        scope.text(label, textX, textY, chipTextScale, foreground);
    }

    public UiRect getChipBounds(UiRect bounds) {
        float chipWidth = 56.0f;
        float chipHeight = 18.0f;
        float chipX = bounds.right() - MD3Theme.ROW_TRAILING_INSET - chipWidth;
        float chipY = bounds.y() + (bounds.height() - chipHeight) / 2.0f;
        return new UiRect(chipX, chipY, chipWidth, chipHeight);
    }

    @Override
    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        if (!bounds.contains(event.x(), event.y()) || event.button() != 0) {
            return false;
        }
        UiRect chipBounds = getChipBounds(bounds);
        return chipBounds.contains(event.x(), event.y());
    }

    @Override
    public boolean hasActiveAnimation() {
        return !chipHoverAnimation.isFinished() || !focusAnimation.isFinished();
    }

    private String formatKeybind(int keyCode) {
        if (keyCode == KeybindUtils.NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName();
        }
        return KeybindUtils.format(keyCode);
    }

}
