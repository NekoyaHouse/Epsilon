package com.github.epsilon.gui.panel.popup

import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import net.minecraft.client.gui.GuiGraphicsExtractor
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

    private val openAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 160L)
    private val confirmHoverAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 120L)
    private val cancelHoverAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 120L)

    private var closeAfterClick by mutableStateOf(false)
    private var animatedY = 0.0f
    private lateinit var confirmButtonBounds: UiRect
    private lateinit var cancelButtonBounds: UiRect

    init {
        updateLayout(bounds.y)
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override val inputView = LuminView()

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        inputView.render(mouseX, mouseY) { renderContent(guiGraphics, renderBatch, mouseX, mouseY, partialTick) }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        // 推式动画：目标在命令阶段写入，绘制块内只读取 value，避免 recompose 块内调用 animate。
        animatedY = bounds.y - (1.0f - openAnimation.value) * 6.0f
        updateLayout(animatedY)
        confirmHoverAnimation.target =
            if (confirmButtonBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f
        cancelHoverAnimation.target =
            if (cancelButtonBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f
        openAnimation.target = 1.0f

        val title = titleSupplier.get()
        val message = messageSupplier.get()
        val cancelLabel = cancelLabelSupplier.get()
        val confirmLabel = confirmLabelSupplier.get()
        val popupBounds = UiRect(bounds.x, animatedY, bounds.width, bounds.height)
        // 弹窗矩形随开启动画逐帧变化，bounds 变化会触发整层重建；供应商文本与主题并入 key。

        renderBatch.LuminUIScene(popupBounds, key = this) {
            Interaction(key = "surface") {
                onClick { closeAfterClick = false; true }
            }
            val progress = openAnimation.value
            PopupCard(
                size,
                MD3Theme.CARD_RADIUS.toFloat(),
                MD3Theme.POPUP_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
            )

            val titleScale = 0.66f
            val messageScale = 0.56f
            val detailScale = 0.60f
            Text(title, 12.0f, 10.0f, titleScale, MD3Theme.TEXT_PRIMARY)
            Text(message, 12.0f, 24.0f, messageScale, MD3Theme.TEXT_SECONDARY)
            if (detail != null && !detail.isBlank()) {
                Text(detail, 12.0f, 37.0f, detailScale, MD3Theme.PRIMARY)
            }

            buildButton(cancelButtonBounds.relativeTo(popupBounds), cancelLabel, false, cancelHoverAnimation.value)
            buildButton(confirmButtonBounds.relativeTo(popupBounds), confirmLabel, true, confirmHoverAnimation.value)
        }
    }


    override fun shouldCloseAfterClick(): Boolean {
        return closeAfterClick
    }

    private fun UiScope.buildButton(
        buttonBounds: UiRect,
        label: String,
        destructive: Boolean,
        hover: Float
    ) {
        Interaction(key = destructive, bounds = buttonBounds) {
            onClick {
                if (destructive) onConfirm.run()
                closeAfterClick = true
                true
            }
        }
        val baseColor: Color = if (destructive) MD3Theme.ERROR else MD3Theme.SURFACE_CONTAINER_HIGH
        val hoverColor: Color = if (destructive) {
            MD3Theme.withAlpha(MD3Theme.ERROR, 220)
        } else {
            MD3Theme.SURFACE_CONTAINER_HIGHEST
        }
        val textColor: Color = if (destructive) MD3Theme.ON_PRIMARY else MD3Theme.TEXT_PRIMARY
        Button(
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

    override fun close() {
        inputView.close()
    }

}
