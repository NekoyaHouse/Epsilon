package com.github.epsilon.gui.panel.popup

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

/**
 * 面板弹窗宿主。
 *
 * 它负责管理当前活动弹窗、提供相对面板的居中布局能力，并把弹窗的 extract/flush
 * 生命周期接入主屏幕统一的渲染批次提交流程。
 */
class PanelPopupHost {

    var activePopup: Popup? = null
        private set

    private var overlayBounds: UiRect? = null
    private var pendingBatch: UiRenderBatch? = null

    /**
     * 打开一个新的活动弹窗。
     *
     * @param popup 需要显示的弹窗实例
     */
    fun open(popup: Popup) {
        close()
        this.activePopup = popup
        IMEFocusHelper.activate()
    }

    /**
     * 关闭当前活动弹窗。
     */
    fun close() {
        val popup = activePopup
        if (popup != null) {
            popup.close()
        }
        activePopup = null
        pendingBatch = null
        IMEFocusHelper.deactivate()
    }

    fun setOverlayBounds(overlayBounds: UiRect) {
        this.overlayBounds = overlayBounds
    }

    /**
     * 根据宿主覆盖区域计算一个居中的弹窗矩形。
     */
    fun getCenteredBounds(width: Float, height: Float): UiRect {
        val baseBounds = overlayBounds ?: UiRect(0.0f, 0.0f, width, height)
        val popupWidth = Math.min(width, baseBounds.width)
        val popupHeight = Math.min(height, baseBounds.height)
        return UiRect(
            baseBounds.x + (baseBounds.width - popupWidth) / 2.0f,
            baseBounds.y + (baseBounds.height - popupHeight) / 2.0f,
            popupWidth,
            popupHeight
        )
    }

    /**
     * 让当前弹窗提取本帧 UI，并写入宿主内部批次。
     *
     * 该阶段只做 extract，不直接 flush；真正的输出由主屏幕统一调度。
     */
    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val popup = activePopup
        if (popup == null) {
            pendingBatch = null
            return
        }
        popup.extractGui(guiGraphics, renderBatch, mouseX, mouseY, partialTick)
        pendingBatch = renderBatch
    }

    /**
     * 输出当前活动弹窗的批次内容。
     */
    fun flush() {
        val batch = pendingBatch
        val popup = activePopup
        if (batch == null || popup == null) {
            return
        }
        popup.flush(batch)
        pendingBatch = null
    }

    fun extractOverlay(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        activePopup?.extractOverlay(guiGraphics, mouseX, mouseY, partialTick)
    }

    /**
     * 处理鼠标点击事件。
     *
     * 当点击发生在弹窗外部时，宿主会直接关闭当前弹窗。
     */
    fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val popup = activePopup ?: return false
        if (!popup.getBounds().contains(event.x(), event.y())) {
            close()
            return true
        }
        val handled = popup.mouseClicked(event, isDoubleClick)
        if (handled && popup.shouldCloseAfterClick()) {
            close()
        }
        return handled
    }

    fun keyPressed(event: KeyEvent): Boolean {
        val popup = activePopup ?: return false
        if (event.key() == InputConstants.KEY_ESCAPE) {
            close()
            return true
        }
        val handled = popup.keyPressed(event)
        if (handled && popup.shouldCloseAfterClick()) {
            close()
        }
        return handled
    }

    fun charTyped(event: CharacterEvent): Boolean {
        return activePopup?.charTyped(event) ?: false
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        val popup = activePopup ?: return false
        popup.mouseReleased(event)
        return true
    }

    fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val popup = activePopup ?: return false
        popup.mouseDragged(event, mouseX, mouseY)
        return true
    }

    /**
     * 仅当滚轮事件位于弹窗内部时才转发给活动弹窗。
     */
    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val popup = activePopup ?: return false
        if (popup.getBounds().contains(mouseX, mouseY)) {
            popup.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            return true
        }
        return false
    }

    /**
     * 面板弹窗协议。
     *
     * 弹窗需要实现几何区域、UI 提取以及输入事件处理；普通图元进入主 scene，
     * 私有视口缓冲或原版物品预览等额外输出可以在 [flush] 中补充。
     */
    interface Popup : AutoCloseable {

        /**
         * 返回当前弹窗的命中与布局区域。
         */
        fun getBounds(): UiRect

        /**
         * 将当前弹窗的 UI 内容提取到给定批次中。
         */
        fun extractGui(
            guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
            mouseX: Int, mouseY: Int, partialTick: Float,
        )

        /**
         * 输出弹窗的私有附加缓冲。
         */
        fun flush(renderBatch: UiRenderBatch) {
        }

        fun extractOverlay(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        }

        /**
         * 处理鼠标点击事件。
         *
         * @return 是否已消费该事件
         */
        fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean

        /**
         * 指示宿主在本次点击处理后是否需要关闭弹窗。
         */
        fun shouldCloseAfterClick(): Boolean {
            return false
        }

        fun keyPressed(event: KeyEvent): Boolean {
            return false
        }

        fun charTyped(event: CharacterEvent): Boolean {
            return false
        }

        fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            return false
        }

        fun mouseReleased(event: MouseButtonEvent): Boolean {
            return false
        }

        fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
            return false
        }

        override fun close() {
        }
    }

}
