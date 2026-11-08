package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.adapter.SettingListController
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.abs
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

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

    private val luminView = LuminView()

    override fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        luminView.render(mouseX, mouseY) {
            renderContent(
                guiGraphics,
                renderBatch,
                bounds,
                mouseX,
                mouseY,
                partialTick
            )
        }
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
            InputRegion(
                key = this@GeneralClientSettingTab to "input",
                onPress = ::mouseClicked,
                onDrag = ::mouseDragged,
                onRelease = ::mouseReleased,
                onScroll = ::mouseScrolled,
                onBlur = {},
                onCancel = { scrollBarDrag.reset() })

            Viewport(
                contentBuffer, size, state.getClientSettingScroll(), maxScroll, contentHeight,
                effectiveMouseX, effectiveMouseY
            ) {
                settingListController.layoutRows(
                    SETTING_OWNER_KEY, settings, bounds, state.getClientSettingScroll(), rowWidth,
                    this, effectiveMouseX, effectiveMouseY
                ) { setting, row, rowBounds ->
                    LuminUIItem(setting) {
                        val hoverAnimation = hoverAnimations.getOrPut(setting) {
                            val animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
                            animation.startValue = 0.0f
                            animation
                        }
                        hoverAnimation.run(
                            if (rowBounds.contains(
                                    effectiveMouseX.toDouble(),
                                    effectiveMouseY.toDouble()
                                )
                            ) 1.0f else 0.0f
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

    private fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val currentBounds = bounds
        if (currentBounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        scrollVelocity = 0.0f


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

        return false
    }

    private fun mouseReleased(event: MouseButtonEvent): Boolean {
        return scrollBarDrag.mouseReleased()
    }

    private fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val currentBounds = bounds
        if (scrollBarDrag.isDragging()) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), currentBounds!!, state.getMaxClientSettingScroll())
            if (newScroll >= 0.0f) {
                state.setClientSettingScroll(newScroll)
            }

            return true
        }
        return false
    }

    private fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentBounds = bounds
        if (currentBounds != null && currentBounds.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24.0f

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

    }

    override fun close() {
        luminView.close()

        settingListController.close()
        contentBuffer.close()

    }

    private companion object {

        private const val SETTING_OWNER_KEY = "client-settings:panel-general"

    }

}
