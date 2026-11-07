package com.github.epsilon.gui.panel.popup

import com.github.epsilon.gui.lib.Interaction
import com.github.epsilon.kernel.state.*
import com.github.epsilon.gui.lib.Button
import com.github.epsilon.gui.lib.LuminUIScene
import com.github.epsilon.gui.lib.PopupCard
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.render.animation.Easing
import net.minecraft.client.gui.GuiGraphicsExtractor
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

    private val openAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 160L)
    private val buttonHoverAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 120L)

    private var closeAfterClick by mutableStateOf(false)
    private lateinit var buttonBounds: UiRect

    init {
        updateLayout(bounds.y)
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override val inputView = com.github.epsilon.gui.lib.state.LuminView()

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
        val popupY = bounds.y - (1.0f - openAnimation.value) * 6.0f
        updateLayout(popupY)
        buttonHoverAnimation.target = if (buttonBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f
        openAnimation.target = 1.0f

        val title = titleSupplier.get()
        val message = messageSupplier.get()
        val buttonLabel = buttonLabelSupplier.get()
        val popupBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
        // 弹窗矩形随开启动画逐帧变化，bounds 变化会触发整层重建；供应商文本与主题并入 key。

        renderBatch.LuminUIScene(popupBounds, key = this) {

            val progress = openAnimation.value
            val hover = buttonHoverAnimation.value
            PopupCard(
                size,
                MD3Theme.CARD_RADIUS.toFloat(),
                MD3Theme.POPUP_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
            )

            val titleScale = 0.66f
            val messageScale = 0.56f
            val detailScale = 0.52f
            Text(title, 12.0f, 10.0f, titleScale, MD3Theme.TEXT_PRIMARY)
            Text(message, 12.0f, 25.0f, messageScale, MD3Theme.TEXT_SECONDARY)
            if (detail != null && !detail.isBlank()) {
                Text(detail, 12.0f, 38.0f, detailScale, MD3Theme.TEXT_MUTED)
            }

            val localButtonBounds = buttonBounds.relativeTo(popupBounds)
            Interaction("close", localButtonBounds) { onClick { closeAfterClick = true; true } }
            Button(
                localButtonBounds, localButtonBounds.height / 2.0f,
                MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, hover * 0.35f),
                buttonLabel, 0.56f, MD3Theme.ON_PRIMARY_CONTAINER
            )
        }
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

        override fun close() { inputView.close() }
}
