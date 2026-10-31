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
import java.util.function.Supplier

class MessagePopup(
    private val bounds: UiRect,
    private val titleSupplier: Supplier<String>,
    private val messageSupplier: Supplier<String>,
    private val detail: String?,
    private val buttonLabelSupplier: Supplier<String>,
) : PanelPopupHost.Popup {

    constructor(bounds: UiRect, title: String, message: String, detail: String?, buttonLabel: String) : this(
        bounds, Supplier { title }, Supplier { message }, detail, Supplier { buttonLabel }
    )

    private val openAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)
    private val buttonHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)

    private var closeAfterClick = false
    private lateinit var buttonBounds: UiRect

    init {
        this.openAnimation.startValue = 0.0f
        this.buttonHoverAnimation.startValue = 0.0f
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
        val popupY = bounds.y - (1.0f - progress) * 6.0f
        updateLayout(popupY)
        buttonHoverAnimation.run(if (buttonBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
        val tree = UiTree.build { scope ->
            val popupBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
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
                val detailScale = 0.52f
                val title = titleSupplier.get()
                val message = messageSupplier.get()
                val buttonLabel = buttonLabelSupplier.get()
                popup.text(title, 12.0f, 10.0f, titleScale, MD3Theme.TEXT_PRIMARY)
                popup.text(message, 12.0f, 25.0f, messageScale, MD3Theme.TEXT_SECONDARY)
                if (detail != null && !detail.isBlank()) {
                    popup.text(detail, 12.0f, 38.0f, detailScale, MD3Theme.TEXT_MUTED)
                }

                val hover = buttonHoverAnimation.value
                val localButtonBounds = buttonBounds.relativeTo(popupBounds)
                popup.button(
                    localButtonBounds, localButtonBounds.height / 2.0f,
                    MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, hover * 0.35f),
                    buttonLabel, 0.56f, MD3Theme.ON_PRIMARY_CONTAINER
                )
            }
        }
        renderBatch.render(tree)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false
        }
        closeAfterClick = buttonBounds.contains(event.x(), event.y())
        return true
    }

    override fun shouldCloseAfterClick(): Boolean {
        return closeAfterClick
    }

    private fun updateLayout(popupY: Float) {
        val buttonWidth = 68.0f
        val buttonHeight = 24.0f
        buttonBounds = UiRect(
            bounds.x + bounds.width - buttonWidth - 12.0f,
            popupY + bounds.height - buttonHeight - 10.0f,
            buttonWidth,
            buttonHeight
        )
    }

}
