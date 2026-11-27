package com.github.epsilon.gui.screen

import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.screen.DialogCard.BODY_SCALE
import com.github.epsilon.gui.screen.DialogCard.CARD_PADDING
import com.github.epsilon.gui.screen.DialogCard.DialogButton
import com.github.epsilon.gui.theme.MD3Theme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * 通用二次确认对话框。
 */
object ConfirmDialogScreen : Screen(Component.empty()) {

    private const val ROW_HEIGHT = 16.0f
    private const val MAX_WIDTH = 360.0f

    fun show(
        parent: Screen?,
        title: String,
        lines: List<String>,
        confirmLabel: String,
        cancelLabel: String,
        onConfirm: Runnable,
    ) {
        DialogCard.show(this, parent, Confirmation(title, lines.toList(), confirmLabel, cancelLabel, onConfirm))
    }

    override fun added() = DialogCard.added(this)

    override fun removed() = DialogCard.removed()

    override fun getTitle(): Component = DialogCard.title(this) ?: super.getTitle()

    override fun isPauseScreen(): Boolean = false

    override fun onClose() = DialogCard.close(this)

    override fun extractRenderState(graphics: GuiGraphicsExtractor) =
        DialogCard.extractRenderState(this, graphics, graphics.mouseX(), graphics.mouseY())

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        DialogCard.mouseClicked(event, doubleClick)

    override fun mouseReleased(event: MouseButtonEvent): Boolean = true

    private class Confirmation(
        private val titleValue: String,
        private val lines: List<String>,
        private val confirmLabel: String,
        private val cancelLabel: String,
        private val onConfirm: Runnable,
    ) : DialogCard.Content(Component.literal(titleValue)) {

        override fun titleText(): String {
            return titleValue
        }

        override fun cardWidth(): Float {
            return MAX_WIDTH
        }

        override fun cardHeight(): Float {
            return CARD_PADDING * 2.0f + 36.0f + ROW_HEIGHT * maxOf(1, lines.size) + 24.0f
        }

        override fun buildBody(scope: UiTree.Scope, contentTop: Float) {
            var y = contentTop
            for (line in lines) {
                scope.text(line, CARD_PADDING, y, BODY_SCALE, MD3Theme.TEXT_SECONDARY)
                y += ROW_HEIGHT
            }
        }

        override fun buttons(): List<DialogButton> {
            return listOf(
                DialogButton(cancelLabel, false) { ConfirmDialogScreen.onClose() },
                DialogButton(confirmLabel, true) { DialogCard.close(ConfirmDialogScreen, onConfirm) }
            )
        }
    }

}
