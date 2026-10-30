package com.github.epsilon.gui.panel.view

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.UiInvalidationState
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.adapter.SettingListController
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.util.HashMap
import java.util.Locale

class ModuleDetailPanel(
    protected val state: PanelState,
    private val textRenderer: TextRenderer,
    popupHost: PanelPopupHost,
) : AutoCloseable {

    private val settingListController = SettingListController(popupHost)
    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val contentState = UiInvalidationState()
    private var bounds: UiRect? = null
    private var guiHeight = 0
    private var headerBounds: UiRect? = null
    private val hoverAnimations = HashMap<Setting<*>, Animation>()
    private var lastDetailScroll = Float.NaN
    private var lastModuleKey = ""
    private var lastVisibleSettings: List<String> = emptyList()
    private val scrollBarDrag = ScrollBarDragState()
    private var scrollVelocity = 0.0f
    private val bindModeAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)
    private val bindModeHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val keybindHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val keybindFocusAnimation = Animation(Easing.EASE_OUT_CUBIC, 150L)
    private val hiddenAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)
    private val hiddenHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private var lastContentSignature = Long.MIN_VALUE

    init {
        this.bindModeAnimation.startValue = 0.0f
        this.bindModeHoverAnimation.startValue = 0.0f
        this.keybindHoverAnimation.startValue = 0.0f
        this.keybindFocusAnimation.startValue = 0.0f
        this.hiddenAnimation.startValue = 0.0f
        this.hiddenHoverAnimation.startValue = 0.0f
    }

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds
        this.guiHeight = guiGraphics.guiHeight()

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollDetail((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }
            markDirty()
        }

        val popupConsumesHover = settingListController.isPopupHovered(mouseX, mouseY)
        val effectiveMouseX = if (popupConsumesHover) Int.MIN_VALUE else mouseX
        val effectiveMouseY = if (popupConsumesHover) Int.MIN_VALUE else mouseY

        val module = state.getSelectedModule()
        val detailTitle = module?.translatedName ?: EpsilonTranslations.Gui.NO_MODULE.getTranslatedName()
        val titleScale = 0.78f
        val titleHeight = textRenderer.getHeight(titleScale)
        val titleY = 10.0f + (MD3Theme.CONTROL_HEIGHT - titleHeight) / 2.0f
        val headerTree = UiTree.build { scope ->
            scope.pushAbsolute(bounds) { panel ->
                panel.text(detailTitle, MD3Theme.PANEL_TITLE_INSET, titleY, titleScale, MD3Theme.TEXT_PRIMARY)
            }
        }
        renderBatch.render(headerTree)

        if (module == null) {
            return
        }

        headerBounds = UiRect(
            bounds.x + MD3Theme.PANEL_VIEWPORT_INSET, bounds.y + 34.0f,
            bounds.width - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f, 36.0f
        )
        val header = headerBounds!!
        val controlTree = UiTree.build { scope ->
            scope.pushAbsolute(header) { headerScope ->
                headerScope.roundRect(
                    0.0f, 0.0f, header.width, header.height, MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.SURFACE_CONTAINER
                )
            }
            buildKeybindControl(scope, module, mouseX, mouseY)
            buildBindModeControl(scope, module, mouseX, mouseY)
            buildHiddenControl(scope, module, mouseX, mouseY)
        }
        renderBatch.render(controlTree)

        val viewport = getViewport()
        val settings = module.settings.filter { setting -> setting.isAvailable }
        val settingOwnerKey = getSettingOwnerKey(module)
        val contentHeight = settingListController.getContentHeight(settingOwnerKey, settings)
        state.setMaxDetailScroll(contentHeight - viewport.height)
        val maxDetailScroll = Math.max(0.0f, contentHeight - viewport.height)
        val hasScrollBar = maxDetailScroll > 0
        val rowWidth = if (hasScrollBar) viewport.width - ScrollBarUtils.TOTAL_WIDTH else viewport.width
        val contentSignature = buildContentSignature(module, settings, settingOwnerKey)
        val rebuildContent = shouldRebuildContent(
            bounds, mouseX, mouseY, module, settings, guiGraphics.guiHeight(), contentSignature
        )

        if (rebuildContent) {
            contentBuffer.clear()
            contentState.beginRebuild()
        }

        val contentTree = UiTree.build { scope ->
            scope.viewport(
                contentBuffer, viewport, state.getDetailScroll(), maxDetailScroll, contentHeight,
                effectiveMouseX, effectiveMouseY
            ) { content ->
                if (!rebuildContent) {
                    return@viewport
                }
                settingListController.layoutRows(
                    settingOwnerKey, settings, viewport, state.getDetailScroll(), rowWidth,
                    content, textRenderer, effectiveMouseX, effectiveMouseY
                ) { setting, row, rowBounds ->
                    if (row is KeybindSettingRow) {
                        row.setListening(state.getListeningKeybindSetting() === row.getSetting())
                    }
                    val hoverAnimation =
                        hoverAnimations.getOrPut(setting) { Animation(Easing.EASE_OUT_CUBIC, 120L) }
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
        renderBatch.render(contentTree)

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, module, settings, guiGraphics.guiHeight(), contentSignature)
        }
    }

    fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val currentBounds = bounds ?: return false
        scrollVelocity = 0.0f
        val module = state.getSelectedModule()
        if (module == null || headerBounds == null) {
            return false
        }

        if (state.getListeningKeyBindModule() === module) {
            val keybindBounds = getKeybindBounds()
            if (keybindBounds.contains(event.x(), event.y())) {
                module.keyBind = KeybindUtils.encodeMouseButton(event.button())
                state.setListeningKeyBindModule(null)
                markDirty()
                return true
            }
        }

        val listeningKeybindSetting = state.getListeningKeybindSetting()
        if (listeningKeybindSetting != null) {
            listeningKeybindSetting.value = KeybindUtils.encodeMouseButton(event.button())
            state.setListeningKeybindSetting(null)
            markDirty()
            return true
        }

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        // Scrollbar drag
        val viewport = getViewport()
        val maxScroll = state.getMaxDetailScroll()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, state.getDetailScroll(), maxScroll)) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, maxScroll)
            if (newScroll >= 0) {
                state.setDetailScroll(newScroll)
            }
            markDirty()
            return true
        }

        val keybindBounds = getKeybindBounds()
        if (keybindBounds.contains(event.x(), event.y())) {
            state.setListeningKeyBindModule(module)
            markDirty()
            return true
        } else if (state.getListeningKeyBindModule() === module) {
            state.setListeningKeyBindModule(null)
            markDirty()
        }

        val bindModeBounds = getBindModeBounds()
        if (bindModeBounds.contains(event.x(), event.y())) {
            val midpoint = bindModeBounds.centerX()
            module.bindMode = if (event.x() < midpoint) Module.BindMode.Toggle else Module.BindMode.Hold
            markDirty()
            return true
        }

        val hiddenBounds = getHiddenBounds()
        if (hiddenBounds.contains(event.x(), event.y())) {
            val midpoint = hiddenBounds.centerX()
            module.isHidden = event.x() >= midpoint
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

    fun mouseReleased(event: MouseButtonEvent): Boolean {
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

    fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (scrollBarDrag.isDragging()) {
            val viewport = getViewport()
            val newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, state.getMaxDetailScroll())
            if (newScroll >= 0) {
                state.setDetailScroll(newScroll)
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

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val viewport = getViewport()
        if (bounds != null && viewport.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24f
            markDirty()
            return true
        }
        return false
    }

    fun keyPressed(event: KeyEvent): Boolean {
        val listeningSetting = state.getListeningKeybindSetting()
        if (listeningSetting != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                state.setListeningKeybindSetting(null)
                markDirty()
                return true
            }
            if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
                listeningSetting.value = -1
                state.setListeningKeybindSetting(null)
                markDirty()
                return true
            }
            listeningSetting.value = event.key()
            state.setListeningKeybindSetting(null)
            markDirty()
            return true
        }
        val module = state.getSelectedModule()
        if (module != null && state.getListeningKeyBindModule() === module) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                state.setListeningKeyBindModule(null)
                markDirty()
                return true
            }
            if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
                module.keyBind = -1
                state.setListeningKeyBindModule(null)
                markDirty()
                return true
            }
            module.keyBind = event.key()
            state.setListeningKeyBindModule(null)
            markDirty()
            return true
        }
        if (settingListController.keyPressed(event)) {
            markDirty()
            return true
        }
        return false
    }

    fun charTyped(event: CharacterEvent): Boolean {
        if (settingListController.charTyped(event)) {
            markDirty()
            return true
        }
        return false
    }

    private fun getViewport(): UiRect {
        val currentBounds = bounds
        if (currentBounds == null) {
            return UiRect(0.0f, 0.0f, 0.0f, 0.0f)
        }
        val header = headerBounds ?: return UiRect(
            currentBounds.x, currentBounds.y, currentBounds.width, currentBounds.height
        )
        return UiRect(
            currentBounds.x + MD3Theme.PANEL_VIEWPORT_INSET, header.bottom() + 6.0f,
            currentBounds.width - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
            currentBounds.bottom() - header.bottom() - 10.0f
        )
    }

    private fun getBindModeBounds(): UiRect {
        return UiRect(
            getHeaderControlGroupLeftX() + getKeybindControlSize() + getHeaderControlGap(),
            getHeaderControlsY(), getBindModeControlWidth(), getHeaderControlHeight()
        )
    }

    private fun getKeybindBounds(): UiRect {
        return UiRect(
            getHeaderControlGroupLeftX(), getHeaderControlsY(),
            getKeybindControlSize(), getKeybindControlSize()
        )
    }

    private fun getHeaderControlGroupLeftX(): Float {
        return headerBounds!!.x + getHeaderContentInset()
    }

    private fun getHeaderControlGroupRightX(): Float {
        return headerBounds!!.right() - getHeaderContentInset() - getHeaderControlGroupWidth()
    }

    private fun getHeaderControlGroupWidth(): Float {
        return getKeybindControlSize() + getHeaderControlGap() + getBindModeControlWidth() +
                getHeaderControlGap() + getHiddenControlWidth()
    }

    private fun getHeaderContentInset(): Float {
        return MD3Theme.PANEL_TITLE_INSET + 2.0f
    }

    private fun getHeaderContentInsetX(): Float {
        return headerBounds!!.x + getHeaderContentInset()
    }

    private fun getHeaderControlHeight(): Float {
        return MD3Theme.CONTROL_HEIGHT
    }

    private fun getKeybindControlSize(): Float {
        return getHeaderControlHeight()
    }

    private fun getBindModeControlWidth(): Float {
        return 72.0f
    }

    private fun getHiddenControlWidth(): Float {
        return 72.0f
    }

    private fun getHiddenBounds(): UiRect {
        return UiRect(
            getHeaderControlGroupRightX() + getKeybindControlSize() + getHeaderControlGap() +
                    getBindModeControlWidth() + getHeaderControlGap(),
            getHeaderControlsY(), getHiddenControlWidth(), getHeaderControlHeight()
        )
    }

    private fun getHeaderControlGap(): Float {
        return 6.0f
    }

    private fun getHeaderControlRadius(): Float {
        return MD3Theme.CONTROL_RADIUS
    }

    private fun getKeybindControlRadius(): Float {
        return 8.0f
    }

    private fun getHeaderControlsY(): Float {
        return headerBounds!!.y + (headerBounds!!.height - getHeaderControlHeight()) / 2.0f
    }

    private fun buildBindModeControl(scope: UiTree.Scope, module: Module, mouseX: Int, mouseY: Int) {
        val bindModeBounds = getBindModeBounds()
        val bindProgress = scope.animate(bindModeAnimation, module.bindMode == Module.BindMode.Hold)
        val hoverProgress = scope.animate(bindModeHoverAnimation, bindModeBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        PanelElements.buildSegmentedControl(
            scope, textRenderer, bindModeBounds,
            EpsilonTranslations.Keybind.TOGGLE.getTranslatedName(),
            EpsilonTranslations.Keybind.HOLD.getTranslatedName(),
            bindProgress, hoverProgress
        )
    }

    private fun buildKeybindControl(scope: UiTree.Scope, module: Module, mouseX: Int, mouseY: Int) {
        val keybindBounds = getKeybindBounds()
        val listening = state.getListeningKeyBindModule() === module
        val hoverProgress = scope.animate(keybindHoverAnimation, keybindBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val focusProgress = scope.animate(keybindFocusAnimation, listening)
        val radius = getKeybindControlRadius()
        scope.pushAbsolute(keybindBounds) { keybind ->
            val haloInset = 1.5f * focusProgress
            if (haloInset > 0.01f) {
                keybind.roundRect(
                    -haloInset, -haloInset, keybindBounds.width + haloInset * 2.0f, keybindBounds.height + haloInset * 2.0f,
                    radius + haloInset, MD3Theme.withAlpha(MD3Theme.PRIMARY, (28 * focusProgress).toInt())
                )
            }

            val background: Color = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, focusProgress)
            val foreground: Color = MD3Theme.lerp(MD3Theme.ON_SECONDARY_CONTAINER, MD3Theme.ON_PRIMARY_CONTAINER, focusProgress)
            keybind.roundRect(0.0f, 0.0f, keybindBounds.width, keybindBounds.height, radius, background)
            if (hoverProgress > 0.01f) {
                keybind.roundRect(
                    0.0f, 0.0f, keybindBounds.width, keybindBounds.height, radius,
                    MD3Theme.stateLayer(foreground, hoverProgress, if (listening) 18 else 12)
                )
            }

            val label = if (listening) "..." else formatCompactKeybind(module.keyBind)
            val scale = if (label.length >= 3) 0.42f else 0.5f
            val textWidth = textRenderer.getWidth(label, scale)
            val textHeight = textRenderer.getHeight(scale)
            val textX = (keybindBounds.width - textWidth) / 2.0f
            val textY = (keybindBounds.height - textHeight) / 2.0f
            keybind.text(label, textX, textY, scale, foreground)
        }
    }

    private fun buildHiddenControl(scope: UiTree.Scope, module: Module, mouseX: Int, mouseY: Int) {
        val hiddenBounds = getHiddenBounds()
        val hiddenProgress = scope.animate(hiddenAnimation, module.isHidden)
        val hoverProgress = scope.animate(hiddenHoverAnimation, hiddenBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        PanelElements.buildSegmentedControl(
            scope, textRenderer, hiddenBounds,
            EpsilonTranslations.Module.VISIBLE.getTranslatedName(),
            EpsilonTranslations.Module.HIDDEN.getTranslatedName(),
            hiddenProgress, hoverProgress
        )
    }

    private fun formatCompactKeybind(keyCode: Int): String {
        if (keyCode == KeybindUtils.NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName()
        }
        if (KeybindUtils.isMouseButton(keyCode)) {
            return "M" + (KeybindUtils.decodeMouseButton(keyCode) + 1)
        }
        val label = formatKeybind(keyCode).trim()
        if (label.isEmpty()) {
            return "?"
        }

        val parts = label.split("[^A-Za-z0-9]+".toRegex())
        val initials = StringBuilder()
        for (part in parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part[0])) {
                initials.append(Character.toUpperCase(part[0]))
            }
            if (initials.length == 3) {
                break
            }
        }
        if (initials.length >= 2) {
            return initials.toString()
        }

        val compact = label.replace("[^A-Za-z0-9]".toRegex(), "").uppercase(Locale.ROOT)
        if (!compact.isEmpty()) {
            return if (compact.length > 3) compact.substring(0, 3) else compact
        }
        // Symbol-only keys (e.g. ";", "[", "/"): fall back to the raw label so we never show "?".
        return if (label.length > 3) label.substring(0, 3) else label
    }

    private fun formatKeybind(keyCode: Int): String {
        return KeybindUtils.format(keyCode)
    }

    fun flushContent() {
        contentBuffer.flush()
    }

    fun markDirty() {
        contentState.markDirty()
    }

    fun hasActiveAnimations(): Boolean {
        return contentState.hasActiveAnimations() ||
                settingListController.hasActiveAnimations() ||
                !keybindHoverAnimation.isFinished ||
                !keybindFocusAnimation.isFinished ||
                !bindModeAnimation.isFinished ||
                !bindModeHoverAnimation.isFinished ||
                !hiddenAnimation.isFinished ||
                !hiddenHoverAnimation.isFinished
    }

    fun resetTransientState() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        settingListController.resetTransientState()
        if (state.getListeningKeyBindModule() != null) {
            state.setListeningKeyBindModule(null)
        }
        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null)
        }
        markDirty()
    }

    private fun shouldRebuildContent(
        bounds: UiRect, mouseX: Int, mouseY: Int, module: Module,
        settings: List<Setting<*>>, currentGuiHeight: Int, contentSignature: Long,
    ): Boolean {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true
        }
        if (lastDetailScroll.compareTo(state.getDetailScroll()) != 0) {
            return true
        }
        if (lastModuleKey !=
            module.name + ":" + module.bindMode + ":" + module.keyBind + ":" + module.isHidden
        ) {
            return true
        }
        val visibleSettings = settings.map { setting -> setting.name }
        if (lastVisibleSettings != visibleSettings) {
            return true
        }
        return lastContentSignature != contentSignature
    }

    private fun rememberSnapshot(
        bounds: UiRect, mouseX: Int, mouseY: Int, module: Module,
        settings: List<Setting<*>>, currentGuiHeight: Int, contentSignature: Long,
    ) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)
        lastDetailScroll = state.getDetailScroll()
        lastModuleKey =
            module.name + ":" + module.bindMode + ":" + module.keyBind + ":" + module.isHidden
        lastVisibleSettings = settings.map { setting -> setting.name }
        lastContentSignature = contentSignature
    }

    private fun getSettingOwnerKey(module: Module): String {
        return "panel-module:" + module.name.lowercase(Locale.ROOT).replace("[^a-z0-9]+".toRegex(), "_")
    }

    private fun buildContentSignature(
        module: Module, settings: List<Setting<*>>, settingOwnerKey: String,
    ): Long {
        var signature = 17L
        signature = signature * 31L + TranslationManager.getRevision()
        signature = signature * 31L + module.name.hashCode()
        signature = signature * 31L + module.bindMode.ordinal
        signature = signature * 31L + module.keyBind
        signature = signature * 31L + (if (module.isHidden) 1 else 0)
        signature = signature * 31L + java.lang.Float.floatToIntBits(state.getDetailScroll())
        val listening = state.getListeningKeybindSetting()
        signature = signature * 31L + (listening?.name?.hashCode() ?: 0)
        for (setting in settings) {
            signature = signature * 31L + setting.name.hashCode()
            signature = signature * 31L + (if (setting.isAvailable) 1 else 0)
        }
        signature = signature * 31L + SettingLayoutPlanner.signature(settingOwnerKey, settings)
        return signature
    }

    override fun close() {
        settingListController.close()
        contentBuffer.close()
        markDirty()
    }

}