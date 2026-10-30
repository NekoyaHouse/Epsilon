package com.github.epsilon.gui.panel

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.theme.MD3Theme

object PanelLayout {

    fun compute(screenWidth: Int, screenHeight: Int, railWidth: Float): Layout {
        var panelWidth = Math.min(screenWidth * 0.56f, 584.0f)
        var panelHeight = Math.min(screenHeight * 0.56f, 324.0f)
        panelWidth = Math.max(panelWidth, 528.0f)
        panelHeight = Math.max(panelHeight, 300.0f)

        val x = (screenWidth - panelWidth) / 2.0f
        val y = (screenHeight - panelHeight) / 2.0f

        val gap = MD3Theme.SECTION_GAP
        val columnHeight = panelHeight - MD3Theme.OUTER_PADDING * 2.0f
        val railX = x + MD3Theme.OUTER_PADDING
        val modulesX = railX + railWidth + gap
        val maxContentRight = x + panelWidth - MD3Theme.OUTER_PADDING
        val moduleWidth = Math.min(164.0f, panelWidth * 0.292f)
        val detailX = modulesX + moduleWidth + gap
        val detailWidth = maxContentRight - detailX

        val panel = UiRect(x, y, panelWidth, panelHeight)
        val rail = UiRect(railX, y + MD3Theme.OUTER_PADDING, railWidth, columnHeight)
        val modules = UiRect(modulesX, y + MD3Theme.OUTER_PADDING, moduleWidth, columnHeight)
        val detail = UiRect(detailX, y + MD3Theme.OUTER_PADDING, detailWidth, columnHeight)

        return Layout(panel, rail, modules, detail)
    }

    data class Layout(val panel: UiRect, val rail: UiRect, val modules: UiRect, val detail: UiRect)

}
