package com.github.epsilon.gui.panel;

import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.theme.MD3Theme;

public class PanelLayout {

    private PanelLayout() {
    }

    public static Layout compute(int screenWidth, int screenHeight, float railWidth) {
        float panelWidth = Math.min(screenWidth * 0.56f, 584.0f);
        float panelHeight = Math.min(screenHeight * 0.56f, 324.0f);
        panelWidth = Math.max(panelWidth, 528.0f);
        panelHeight = Math.max(panelHeight, 300.0f);

        float x = (screenWidth - panelWidth) / 2.0f;
        float y = (screenHeight - panelHeight) / 2.0f;

        float gap = MD3Theme.SECTION_GAP;
        float columnHeight = panelHeight - MD3Theme.OUTER_PADDING * 2.0f;
        float railX = x + MD3Theme.OUTER_PADDING;
        float modulesX = railX + railWidth + gap;
        float maxContentRight = x + panelWidth - MD3Theme.OUTER_PADDING;
        float moduleWidth = Math.min(164.0f, panelWidth * 0.292f);
        float detailX = modulesX + moduleWidth + gap;
        float detailWidth = maxContentRight - detailX;

        UiRect panel = new UiRect(x, y, panelWidth, panelHeight);
        UiRect rail = new UiRect(railX, y + MD3Theme.OUTER_PADDING, railWidth, columnHeight);
        UiRect modules = new UiRect(modulesX, y + MD3Theme.OUTER_PADDING, moduleWidth, columnHeight);
        UiRect detail = new UiRect(detailX, y + MD3Theme.OUTER_PADDING, detailWidth, columnHeight);

        return new Layout(panel, rail, modules, detail);
    }

    public record Layout(UiRect panel, UiRect rail, UiRect modules, UiRect detail) {
    }

}
