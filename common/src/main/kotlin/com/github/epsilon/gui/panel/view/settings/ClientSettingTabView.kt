package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.render.UiRenderBatch
import net.minecraft.client.gui.GuiGraphicsExtractor

interface ClientSettingTabView : AutoCloseable {

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    )

    fun flushContent()

    fun consumesHover(mouseX: Int, mouseY: Int): Boolean {
        return false
    }

    fun onActivated() {
    }

    fun onDeactivated() {
    }

    override fun close() {
    }

}
