package com.github.epsilon.gui.panel.view

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.panel.PanelState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

open class TopBarPanel(protected val state: PanelState) {

    open fun render(guiGraphics: GuiGraphicsExtractor, bounds: UiRect, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    open fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        return false
    }

}
