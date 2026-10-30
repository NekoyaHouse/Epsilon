package com.github.epsilon.gui.panel.popup

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.util.function.Supplier

class ConfirmActionPopup(
    private val bounds: UiRect,
    private val titleSupplier: Supplier<String>,
    private val messageSupplier: Supplier<String>,
    private val detail: String?,
    private val confirmLabelSupplier: Supplier<String>,
    private val cancelLabelSupplier: Supplier<String>,
    private val onConfirm: Runnable,
) : PanelPopupHost.Popup {

    constructor(
        bounds: UiRect, title: String, message: String, detail: String?,
        confirmLabel: String, cancelLabel: String, onConfirm: Runnable,
    ) : this(
        bounds, Supplier { title }, Supplier { message }, detail,
        Supplier { confirmLabel }, Supplier { cancelLabel }, onConfirm
    )

    private val openAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)
    private val confirmHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val cancelHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)

    private var closeAfterClick = false
    private var animatedY = 0.0f
    private lateinit var confirmButtonBounds: UiRect
    private lateinit var cancelButtonBounds: UiRect

    init {
        this.openAnimation.startValue = 0.0f
        this.confirmHoverAnimation.startValue = 0.0f
        this.cancelHoverAnimation.startValue = 0.0f
        updateLayout(bounds.y)
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        openAnimation.run(1.0f)
        val progress = openAnimation.value
        animatedY = bounds.y - (1.0f - progress) * 6.0f
        updateLayout(animatedY)

        confirmHoverAnimation.run(if (confirmButtonBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
        cancelHoverAnimation.run(if (cancelButtonBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
        val tree = UiTree.build { scope ->
            val popupBounds = UiRect(bounds.x, animatedY, bounds.width, bounds.height)
            scope.pushAbsolute(popupBounds) { popup ->
                popup.popupCard(
                    popupBounds.atOrigin(),
                    MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.POPUP_SHADOW_BLUR,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
                )

                val titleScale = 0.66f
                val messageScale = 0.56f
                val detailScale = 0.60f
                val title = titleSupplier.get()
                val message = messageSupplier.get()
                val cancelLabel = cancelLabelSupplier.get()
                val confirmLabel = confirmLabelSupplier.get()
                popup.text(title, 12.0f, 10.0f, titleScale, MD3Theme.TEXT_PRIMARY)
                popup.text(message, 12.0f, 24.0f, messageScale, MD3Theme.TEXT_SECONDARY)
                if (detail != null && !detail.isBlank()) {
                    popup.text(detail, 12.0f, 37.0f, detailScale, MD3Theme.PRIMARY)
                }

                buildButton(
                    popup, cancelButtonBounds.relativeTo(popupBounds), cancelLabel, false, cancelHoverAnimation.value
                )
                buildButton(
                    popup, confirmButtonBounds.relativeTo(popupBounds), confirmLabel, true, confirmHoverAnimation.value
                )
            }
        }
        renderBatch.render(tree)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false
        }
        closeAfterClick = false
        if (confirmButtonBounds.contains(event.x(), event.y())) {
            onConfirm.run()
            closeAfterClick = true
            return true
        }
        if (cancelButtonBounds.contains(event.x(), event.y())) {
            closeAfterClick = true
            return true
        }
        return true
    }

    override fun shouldCloseAfterClick(): Boolean {
        return closeAfterClick
    }

    private fun buildButton(scope: UiTree.Scope, buttonBounds: UiRect, label: String, destructive: Boolean, hover: Float) {
        val baseColor: Color = if (destructive) MD3Theme.ERROR else MD3Theme.SURFACE_CONTAINER_HIGH
        val hoverColor: Color = if (destructive) {
            MD3Theme.withAlpha(MD3Theme.ERROR, 220)
        } else {
            MD3Theme.SURFACE_CONTAINER_HIGHEST
        }
        val textColor: Color = if (destructive) MD3Theme.ON_PRIMARY else MD3Theme.TEXT_PRIMARY
        scope.button(
            buttonBounds, buttonBounds.height / 2.0f,
            MD3Theme.withAlpha(MD3Theme.lerp(baseColor, hoverColor, hover * 0.35f), 255),
            label, 0.56f, textColor
        )
    }

    private fun updateLayout(popupY: Float) {
        val buttonWidth = 68.0f
        val buttonHeight = 24.0f
        val gap = 6.0f
        val cancelX = bounds.right() - buttonWidth * 2.0f - gap - 12.0f
        val confirmX = bounds.right() - buttonWidth - 12.0f
        val buttonY = popupY + bounds.height - buttonHeight - 10.0f
        cancelButtonBounds = UiRect(cancelX, buttonY, buttonWidth, buttonHeight)
        confirmButtonBounds = UiRect(confirmX, buttonY, buttonWidth, buttonHeight)
    }

}
