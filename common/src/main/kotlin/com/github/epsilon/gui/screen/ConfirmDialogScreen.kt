package com.github.epsilon.gui.screen

import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 通用二次确认对话框。
 */
class ConfirmDialogScreen(
    parent: Screen?,
    private val titleValue: String,
    lines: List<String>,
    private val confirmLabel: String,
    private val cancelLabel: String,
    private val onConfirm: Runnable,
) : EpsilonDialogScreen(parent, Component.literal(titleValue)) {

    private val lines: List<String> = lines.toList()

    override fun titleText(): String {
        return titleValue
    }

    override fun cardWidth(): Float {
        return MAX_WIDTH
    }

    override fun cardHeight(): Float {
        return CARD_PADDING * 2.0f + 36.0f + ROW_HEIGHT * maxOf(1, lines.size) + 24.0f
    }

    override fun buildBody(scope: UiTree.Scope, contentTop: Float, mouseX: Int, mouseY: Int) {
        var y = contentTop
        for (line in lines) {
            scope.text(line, CARD_PADDING, y, BODY_SCALE, MD3Theme.TEXT_SECONDARY)
            y += ROW_HEIGHT
        }
    }

    override fun buttons(): List<DialogButton> {
        return listOf(
            DialogButton(cancelLabel, false, Runnable { onClose() }),
            DialogButton(
                confirmLabel, true, Runnable {
                    onConfirm.run()
                    onClose()
                }
            )
        )
    }

    private companion object {

        private const val ROW_HEIGHT = 16.0f
        private const val MAX_WIDTH = 360.0f

    }

}
