package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.UiInvalidationState
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.adapter.SettingListController
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.util.HashMap

class GeneralClientSettingTab(
    private val state: PanelState,
    private val textRenderer: TextRenderer,
    popupHost: PanelPopupHost,
) : ClientSettingTabView {

    private val settingListController = SettingListController(popupHost)
    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val contentState = UiInvalidationState()
    private val hoverAnimations = HashMap<Setting<*>, Animation>()
    private val scrollBarDrag = ScrollBarDragState()

    private var bounds: UiRect? = null
    private var lastScroll = Float.NaN
    private var lastVisibleSettings: List<String> = emptyList()
    private var lastListeningKey = ""
    private var lastContentSignature = Long.MIN_VALUE
    private var scrollVelocity = 0.0f

    override fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollClientSetting((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }
            markDirty()
        }

        val settings = ClientSetting.settings.filter { setting -> setting.isAvailable }
        val contentHeight = settingListController.getContentHeight(SETTING_OWNER_KEY, settings)
        state.setMaxClientSettingScroll(contentHeight - bounds.height)
        val maxScroll = Math.max(0.0f, contentHeight - bounds.height)
        val hasScrollBar = maxScroll > 0.0f
        val rowWidth = if (hasScrollBar) bounds.width - ScrollBarUtils.TOTAL_WIDTH else bounds.width
        val contentSignature = buildContentSignature(settings)
        val popupConsumesHover = settingListController.isPopupHovered(mouseX, mouseY)
        val effectiveMouseX = if (popupConsumesHover) Int.MIN_VALUE else mouseX
        val effectiveMouseY = if (popupConsumesHover) Int.MIN_VALUE else mouseY
        val rebuildContent = shouldRebuildContent(
            bounds, mouseX, mouseY, settings, guiGraphics.guiHeight(), contentSignature
        )

        if (rebuildContent) {
            contentBuffer.clear()
            contentState.beginRebuild()
        }

        val tree = UiTree.build { scope ->
            scope.viewport(
                contentBuffer, bounds, state.getClientSettingScroll(), maxScroll, contentHeight,
                effectiveMouseX, effectiveMouseY
            ) { content ->
                if (!rebuildContent) {
                    return@viewport
                }
                settingListController.layoutRows(
                    SETTING_OWNER_KEY, settings, bounds, state.getClientSettingScroll(), rowWidth,
                    content, textRenderer, effectiveMouseX, effectiveMouseY
                ) { setting, row, rowBounds ->
                    if (row is KeybindSettingRow) {
                        row.setListening(state.getListeningKeybindSetting() === row.getSetting())
                    }
                    val hoverAnimation = hoverAnimations.getOrPut(setting) {
                        val animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
                        animation.startValue = 0.0f
                        animation
                    }
                    hoverAnimation.run(
                        if (rowBounds.contains(effectiveMouseX.toDouble(), effectiveMouseY.toDouble())) 1.0f else 0.0f
                    )
                    content.pushAbsolute(rowBounds) { rowScope ->
                        row.buildUi(
                            rowScope, guiGraphics, textRenderer, rowBounds,
                            hoverAnimation.value, effectiveMouseX, effectiveMouseY, partialTick
                        )
                    }
                    contentState.noteAnimation(!hoverAnimation.isFinished || row.hasActiveAnimation())
                }
                contentState.noteAnimation(settingListController.hasActiveAnimations())
            }
        }
        renderBatch.render(tree)

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, settings, guiGraphics.guiHeight(), contentSignature)
        }
    }

    override fun flushContent() {
        contentBuffer.flush()
    }

    override fun markDirty() {
        contentState.markDirty()
    }

    override fun hasActiveAnimations(): Boolean {
        return contentState.hasActiveAnimations() || settingListController.hasActiveAnimations()
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val currentBounds = bounds
        if (currentBounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        scrollVelocity = 0.0f

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null)
            markDirty()
        }

        val maxScroll = state.getMaxClientSettingScroll()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), currentBounds, state.getClientSettingScroll(), maxScroll)) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), currentBounds, maxScroll)
            if (newScroll >= 0.0f) {
                state.setClientSettingScroll(newScroll)
            }
            markDirty()
            return true
        }

        val intercepted = settingListController.mouseClicked(event, isDoubleClick, currentBounds) { row, rowBounds, clickEvent, doubleClick ->
            if (row is KeybindSettingRow && row.mouseClicked(rowBounds, clickEvent, doubleClick)) {
                state.setListeningKeybindSetting(row.getSetting())
                true
            } else {
                false
            }
        }
        if (intercepted) {
            markDirty()
            return true
        }
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (scrollBarDrag.mouseReleased()) {
            markDirty()
            return true
        }
        if (settingListController.mouseReleased(event)) {
            markDirty()
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
            markDirty()
            return true
        }
        if (settingListController.mouseDragged(event, mouseX, mouseY)) {
            markDirty()
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentBounds = bounds
        if (currentBounds != null && currentBounds.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24.0f
            markDirty()
            return true
        }
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val listening = state.getListeningKeybindSetting()
        if (listening != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                state.setListeningKeybindSetting(null)
                markDirty()
                return true
            }
            if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
                listening.value = -1
                state.setListeningKeybindSetting(null)
                markDirty()
                return true
            }
            listening.value = event.key()
            state.setListeningKeybindSetting(null)
            markDirty()
            return true
        }
        if (settingListController.keyPressed(event)) {
            markDirty()
            return true
        }
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (settingListController.charTyped(event)) {
            markDirty()
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
        markDirty()
    }

    private fun shouldRebuildContent(
        bounds: UiRect, mouseX: Int, mouseY: Int, settings: List<Setting<*>>,
        currentGuiHeight: Int, contentSignature: Long,
    ): Boolean {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true
        }
        if (lastScroll.compareTo(state.getClientSettingScroll()) != 0) {
            return true
        }
        val listening = state.getListeningKeybindSetting()
        val listeningKey = listening?.name ?: ""
        if (lastListeningKey != listeningKey) {
            return true
        }
        val visibleSettings = settings.map { setting -> setting.name }
        if (lastVisibleSettings != visibleSettings) {
            return true
        }
        return lastContentSignature != contentSignature
    }

    private fun rememberSnapshot(
        bounds: UiRect, mouseX: Int, mouseY: Int, settings: List<Setting<*>>,
        currentGuiHeight: Int, contentSignature: Long,
    ) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)
        lastScroll = state.getClientSettingScroll()
        lastListeningKey = state.getListeningKeybindSetting()?.name ?: ""
        lastVisibleSettings = settings.map { setting -> setting.name }
        lastContentSignature = contentSignature
    }

    private fun buildContentSignature(settings: List<Setting<*>>): Long {
        var signature = 17L
        signature = signature * 31L + TranslationManager.getRevision()
        signature = signature * 31L + java.lang.Float.floatToIntBits(state.getClientSettingScroll())
        signature = signature * 31L + (state.getListeningKeybindSetting()?.name?.hashCode() ?: 0)
        for (setting in settings) {
            signature = signature * 31L + setting.name.hashCode()
            signature = signature * 31L + (if (setting.isAvailable) 1 else 0)
        }
        signature = signature * 31L + SettingLayoutPlanner.signature(SETTING_OWNER_KEY, settings)
        return signature
    }

    override fun close() {
        settingListController.close()
        contentBuffer.close()
        markDirty()
    }

    private companion object {

        private const val SETTING_OWNER_KEY = "client-settings:panel-general"

    }

}
