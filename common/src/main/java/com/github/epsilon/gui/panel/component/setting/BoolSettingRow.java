package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class BoolSettingRow extends SettingRow<BoolSetting> {

    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final Animation toggleAnimation = new Animation(Easing.EASE_OUT_ELASTIC, 620L);

    public BoolSettingRow(BoolSetting setting) {
        super(setting);
        hoverAnimation.setStartValue(0.0f);
        toggleAnimation.setStartValue(setting.getValue() ? 1.0f : 0.0f);
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        float animatedHover = scope.animate(hoverAnimation, hoverProgress);
        float toggleProgress = scope.animate(toggleAnimation, setting.getValue());

        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(animatedHover));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);
        scope.toggle(getSwitchBounds(bounds).relativeTo(bounds), toggleProgress, animatedHover);
    }

    private UiRect getSwitchBounds(UiRect bounds) {
        return PanelElements.switchBounds(bounds);
    }

    @Override
    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        if (!bounds.contains(event.x(), event.y()) || event.button() != 0) {
            return false;
        }
        setting.setValue(!setting.getValue());
        Managers.SOUND.playInUi(setting.getValue() ? SoundKey.SETTINGS_OPEN : SoundKey.SETTINGS_CLOSE);
        return true;
    }

    @Override
    public boolean hasActiveAnimation() {
        return !hoverAnimation.isFinished() || !toggleAnimation.isFinished();
    }

}
