package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.render.UiRenderBatch
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

interface ClientSettingTabView : AutoCloseable {

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    )

    fun flushContent()

    fun markDirty()

    fun hasActiveAnimations(): Boolean

    fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean

    fun mouseReleased(event: MouseButtonEvent): Boolean

    fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean

    fun keyPressed(event: KeyEvent): Boolean

    fun charTyped(event: CharacterEvent): Boolean

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
