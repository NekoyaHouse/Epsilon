package com.github.epsilon.gui.panel.view;

import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.panel.PanelState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class TopBarPanel {

    protected final PanelState state;

    public TopBarPanel(PanelState state) {
        this.state = state;
    }

    public void render(GuiGraphicsExtractor guiGraphics, UiRect bounds, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        return false;
    }
}
