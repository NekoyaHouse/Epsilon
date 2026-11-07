package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.gui.lib.LuminUIScene
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.Viewport
import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.adapter.SettingListController
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.abs

class GeneralClientSettingTab(
    private val state: PanelState,
    popupHost: PanelPopupHost,
) : ClientSettingTabView {

    private val settingListController = SettingListController(popupHost)
    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val hoverAnimations = HashMap<Setting<*>, Animation>()
    private val scrollBarDrag = ScrollBarDragState()

    private var bounds: UiRect? = null
    private var lastListeningKey = ""
    private var scrollVelocity = 0.0f

    private val luminView = com.github.epsilon.gui.lib.state.LuminView()

    override fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        luminView.render(mouseX, mouseY) { renderContent(guiGraphics, renderBatch, bounds, mouseX, mouseY, partialTick) }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds

        if (abs(scrollVelocity) > 0.01f) {
            state.scrollClientSetting((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }

        }

        val settings = ClientSetting.settings.filter { setting -> setting.isVisible }
        val contentHeight = settingListController.getContentHeight(SETTING_OWNER_KEY, settings)
        state.setMaxClientSettingScroll(contentHeight - bounds.height)
        val maxScroll = maxOf(0.0f, contentHeight - bounds.height)
        val hasScrollBar = maxScroll > 0.0f
        val rowWidth = if (hasScrollBar) bounds.width - ScrollBarUtils.TOTAL_WIDTH else bounds.width

        val popupConsumesHover = settingListController.isPopupHovered(mouseX, mouseY)
        val effectiveMouseX = if (popupConsumesHover) Int.MIN_VALUE else mouseX
        val effectiveMouseY = if (popupConsumesHover) Int.MIN_VALUE else mouseY

        contentBuffer.clear()

        renderBatch.LuminUIScene(bounds, key = this to "pass-1") {
            Viewport(
                contentBuffer, size, state.getClientSettingScroll(), maxScroll, contentHeight,
                effectiveMouseX, effectiveMouseY
            ) {
                settingListController.layoutRows(
                    SETTING_OWNER_KEY, settings, bounds, state.getClientSettingScroll(), rowWidth,
                    this, effectiveMouseX, effectiveMouseY
                ) { setting, row, rowBounds ->
                    LuminUIItem(setting) {
                        if (row is KeybindSettingRow) {
                            val listening = state.getListeningKeybindSetting() === row.getSetting()
                            com.github.epsilon.gui.lib.state.afterLuminCommit { row.setListening(listening) }
                        }
                        val hoverAnimation = hoverAnimations.getOrPut(setting) {
                            val animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
                            animation.startValue = 0.0f
                            animation
                        }
                        hoverAnimation.run(
                            if (rowBounds.contains(effectiveMouseX.toDouble(), effectiveMouseY.toDouble())) 1.0f else 0.0f
                        )

                        // 逐行缓存：只有值或动画变化的行才重新捕获节点。

                        SettingRow(row, rowBounds, hoverAnimation.value, effectiveMouseX, effectiveMouseY)
                    }

                }

            }
        }

    }

    override fun flushContent() {
        contentBuffer.flush()
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val currentBounds = bounds
        if (currentBounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        scrollVelocity = 0.0f

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null)

        }

        val maxScroll = state.getMaxClientSettingScroll()
        if (scrollBarDrag.mouseClicked(
                event.x(),
                event.y(),
                currentBounds,
                state.getClientSettingScroll(),
                maxScroll
            )
        ) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), currentBounds, maxScroll)
            if (newScroll >= 0.0f) {
                state.setClientSettingScroll(newScroll)
            }

            return true
        }

        val intercepted = settingListController.mouseClicked(
            event,
            isDoubleClick,
            currentBounds
        ) { row, rowBounds, clickEvent, doubleClick ->
            if (row is KeybindSettingRow && row.mouseClicked(rowBounds, clickEvent, doubleClick)) {
                state.setListeningKeybindSetting(row.getSetting())
                true
            } else {
                false
            }
        }
        if (intercepted) {

            return true
        }
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (scrollBarDrag.mouseReleased()) {

            return true
        }
        if (settingListController.mouseReleased(event)) {

            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val currentBounds = bounds
        if (scrollBarDrag.isDragging()) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), currentBounds!!, state.getMaxClientSettingScroll())
            if (newScroll >= 0.0f) {
                state.setClientSettingScroll(newScroll)
            }

            return true
        }
        if (settingListController.mouseDragged(event, mouseX, mouseY)) {

            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentBounds = bounds
        if (currentBounds != null && currentBounds.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24.0f

            return true
        }
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val listening = state.getListeningKeybindSetting()
        if (listening != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                state.setListeningKeybindSetting(null)

                return true
            }
            if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
                listening.value = -1
                state.setListeningKeybindSetting(null)

                return true
            }
            listening.value = event.key()
            state.setListeningKeybindSetting(null)

            return true
        }
        if (settingListController.keyPressed(event)) {

            return true
        }
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (settingListController.charTyped(event)) {

            return true
        }
        return false
    }

    override fun consumesHover(mouseX: Int, mouseY: Int): Boolean {
        return settingListController.isPopupHovered(mouseX, mouseY)
    }

    override fun onDeactivated() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        settingListController.clearFocus()
        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null)
        }

    }

    override fun close() {
        settingListController.close()
        contentBuffer.close()

    }

    private companion object {

        private const val SETTING_OWNER_KEY = "client-settings:panel-general"

    }

}
