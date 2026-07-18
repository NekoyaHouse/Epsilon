package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class ButtonSettingRow extends SettingRow<ButtonSetting> {

    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);

    public ButtonSettingRow(ButtonSetting setting) {
        super(setting);
        hoverAnimation.setStartValue(0.0f);
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        hoverAnimation.run(hoverProgress);

        float animatedHover = hoverAnimation.getValue();

        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(animatedHover));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        if (!bounds.contains(event.x(), event.y()) || event.button() != 0) {
            return false;
        }
        setting.getValue().run();
        return true;
    }

    @Override
    public boolean hasActiveAnimation() {
        return !hoverAnimation.isFinished();
    }

}
