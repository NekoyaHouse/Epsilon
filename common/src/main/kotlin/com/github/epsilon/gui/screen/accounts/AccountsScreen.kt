package com.github.epsilon.gui.screen.accounts

import com.github.epsilon.Constants.mc
import com.github.epsilon.accounts.Account
import com.github.epsilon.accounts.AccountType
import com.github.epsilon.accounts.MicrosoftLogin
import com.github.epsilon.accounts.types.CrackedAccount
import com.github.epsilon.accounts.types.MicrosoftAccount
import com.github.epsilon.accounts.types.SessionAccount
import com.github.epsilon.accounts.types.TheAlteningAccount
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.gui.lib.control.UiTextField
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.screen.MainMenuScreen
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.AccountManager
import com.github.epsilon.managers.ExecutorManager
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.IMEPreeditOverlay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import java.awt.Color
import java.util.concurrent.atomic.AtomicReference

object AccountsScreen : Screen(Component.literal("Accounts")) {

    private val scene = UiScene(EpsilonUiTheme)
    private val textRenderer: TextRenderer = TextRenderer.create()
    private val listBuffer = UiContentBuffer(EpsilonUiTheme)

    private val rows: MutableList<AccountRow> = ArrayList()
    private val visible: MutableList<AccountRow> = ArrayList()

    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var preeditOverlay: IMEPreeditOverlay? = null

    private var panelX: Float = 0.0f
    private var panelY: Float = 0.0f
    private var panelW: Float = 0.0f
    private var panelH: Float = 0.0f
    private var headerBackBtn: UiRect? = null
    private var searchBounds: UiRect? = null
    private var sortBounds: UiRect? = null
    private var listViewport: UiRect? = null
    private var addBarY: Float = 0.0f
    private var rowWidth: Float = 0.0f
    private val addButtons: Array<UiRect?> = arrayOfNulls(4)
    private var formTitleY: Float = 0.0f
    private var formFieldBounds: UiRect? = null
    private var formAddBtn: UiRect? = null
    private var formCancelBtn: UiRect? = null
    private var formButtonY: Float = 0.0f

    private var scroll: Float = 0.0f
    private var maxScroll: Float = 0.0f
    private var scrollVelocity: Float = 0.0f

    private val searchField = UiTextField(32).leadingIcon(IconChars.SEARCH, 1.0f)
    private val sortButton = AccountSortButton()
    private var sortMode = AccountSortMode.ADDED
    private var focusedIndex: Int = -1

    private var addMode = AddMode.NONE
    private val field = UiTextField(8192)
    private var microsoftWaiting: Boolean = false
    private var microsoftError: Boolean = false
    private var microsoftSession: Int = 0

    private val authenticating = AtomicReference<Account<*>?>(null)

    private enum class AddMode {
        NONE,
        CRACKED,
        ALTENING,
        SESSION,
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun init() {
        super.init()
        for (account in AccountManager.getAccounts()) {
            account.cache.loadHead()
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val minecraft = this.minecraft
        val window = minecraft.window
        var currentTarget = this.renderTarget
        if (currentTarget == null) {
            currentTarget = LuminRenderSystem.LuminRenderTarget.create("accounts-screen", window.width, window.height)
            this.renderTarget = currentTarget
        }
        val activeTarget = currentTarget
        activeTarget.resize(window.width, window.height)
        activeTarget.clear()
        LuminRenderSystem.setActiveTarget(activeTarget)

        val ex = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val ey = LuminRenderSystem.toEpsilonMouseY(mouseY)
        val w = LuminRenderSystem.getScaledWidthInt()
        val h = LuminRenderSystem.getScaledHeightInt()

        layout(w, h)
        scroll = Mth.clamp(scroll + scrollVelocity * partialTick, 0.0f, maxScroll)
        scrollVelocity *= 0.86f

        scene.beginFrame()
        listBuffer.clear()

        val tree = UiTree.build { scope ->
            scope.rect(0.0f, 0.0f, w.toFloat(), h.toFloat(), SCRIM)
            scope.layer(0) { panel ->
                panel.shadow(
                    panelX, panelY, panelW, panelH,
                    MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.PANEL_SHADOW_BLUR, MD3Theme.SHADOW,
                )
                panel.roundRect(panelX, panelY, panelW, panelH, MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.SURFACE)
            }
            scope.layer(10) { content ->
                drawHeader(content, ex, ey)
                if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) {
                    drawAddForm(content, ex, ey)
                } else {
                    drawToolbar(content, ex, ey)
                    drawAccountList(content, ex, ey)
                }
            }
        }
        scene.submit(UiLayer.CONTENT, tree)

        scene.flush()
        listBuffer.flush()
        scene.clear()

        LuminRenderSystem.setActiveTarget(null)

        val overlay = this.preeditOverlay
        if (overlay != null) {
            overlay.updateInputPosition(IMEFocusHelper.activeCursorX.toInt(), IMEFocusHelper.activeCursorY.toInt())
            graphics.setPreeditOverlay(overlay)
        }
        graphics.blit(
            activeTarget.getIdentifier(), 0, 0,
            window.guiScaledWidth, window.guiScaledHeight,
            0.0f, 1.0f, 1.0f, 0.0f,
        )
    }

    private fun layout(w: Int, h: Int) {
        panelW = minOf(460, w - 24).toFloat()
        panelH = minOf(560, h - 24).toFloat()
        panelX = (w - panelW) / 2.0f
        panelY = (h - panelH) / 2.0f

        headerBackBtn = UiRect(
            panelX + 10.0f,
            panelY + (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2.0f,
            BACK_BUTTON_SIZE,
            BACK_BUTTON_SIZE
        )

        val contentTop = panelY + HEADER_HEIGHT
        val contentBottom = panelY + panelH - PAD

        addBarY = contentBottom - ADD_BAR_HEIGHT

        val sortWidth = sortButton.measureWidth(textRenderer, sortMode)
        val toolbarY = contentTop + 4.0f
        sortBounds = UiRect(panelX + panelW - PAD - sortWidth, toolbarY, sortWidth, TOOLBAR_HEIGHT)
        searchBounds = UiRect(panelX + PAD, toolbarY, panelW - PAD * 2.0f - sortWidth - 6.0f, TOOLBAR_HEIGHT)

        val listTop = toolbarY + TOOLBAR_HEIGHT + 8.0f
        listViewport = UiRect(panelX + PAD, listTop, panelW - PAD * 2.0f, maxOf(0.0f, addBarY - listTop - 10.0f))

        formTitleY = contentTop + 12.0f
        formFieldBounds = UiRect(panelX + PAD, contentTop + 52.0f, panelW - PAD * 2.0f, 40.0f)
        formButtonY = contentBottom - 30.0f

        syncRows()
        rebuildVisible()

        val contentHeight = visible.size * (AccountRow.HEIGHT + AccountRow.GAP)
        maxScroll = maxOf(0.0f, contentHeight - (listViewport?.height ?: 0.0f))
        rowWidth =
            if (maxScroll > 0.0f) (listViewport?.width ?: 0.0f) - UiScrollBar.TOTAL_WIDTH else (listViewport?.width
                ?: 0.0f)
    }

    private fun syncRows() {
        rows.removeAll { row -> !AccountManager.exists(row.getAccount()) }
        for (account in AccountManager.getAccounts()) {
            if (rows.none { row -> row.getAccount() === account }) {
                rows.add(AccountRow(account))
            }
        }
    }

    private fun rebuildVisible() {
        val previouslyFocused = focusedAccount()

        visible.clear()
        val query = searchField.getText().trim().lowercase()
        for (row in rows) {
            if (query.isEmpty() || matches(row.getAccount(), query)) {
                visible.add(row)
            }
        }

        val comparator = sortMode.getComparator()
        if (comparator != null) {
            visible.sortWith(Comparator { a, b -> comparator.compare(a.getAccount(), b.getAccount()) })
        }

        if (previouslyFocused == null) {
            focusedIndex = if (visible.isEmpty()) -1 else minOf(focusedIndex, visible.size - 1)
            return
        }

        focusedIndex = -1
        for (i in visible.indices) {
            if (visible[i].getAccount() === previouslyFocused) {
                focusedIndex = i
                break
            }
        }
    }

    private fun matches(account: Account<*>, query: String): Boolean {
        return account.username.lowercase().contains(query) ||
                account.type.name.lowercase().contains(query)
    }

    private fun focusedAccount(): Account<*>? {
        if (focusedIndex < 0 || focusedIndex >= visible.size) return null
        return visible[focusedIndex].getAccount()
    }

    private fun drawHeader(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val backBtn = headerBackBtn ?: return
        val backHovered = backBtn.contains(mouseX.toDouble(), mouseY.toDouble())
        drawIconGlyph(
            scope,
            backBtn,
            IconChars.ARROW_BACK,
            1.0f,
            MD3Theme.TEXT_PRIMARY,
            if (backHovered) 0.9f else 0.0f
        )

        val title = EpsilonTranslations.Gui.ACCOUNTS_TITLE.getTranslatedName()
        val titleScale = 1.2f
        val titleHeight = textRenderer.getHeight(titleScale, StaticFontLoader.JURA_LIGHT)
        scope.text(
            title, panelX + 50.0f, panelY + (HEADER_HEIGHT - titleHeight) / 2.0f,
            titleScale, MD3Theme.TEXT_PRIMARY, StaticFontLoader.JURA_LIGHT,
        )

        val currentName = mc.user.name
        if (currentName.isNotBlank()) {
            val scale = 0.6f
            val labelWidth = textRenderer.getWidth(currentName, scale)
            val labelY = panelY + (HEADER_HEIGHT - textRenderer.getHeight(scale)) / 2.0f
            scope.text(currentName, panelX + panelW - PAD - labelWidth, labelY, scale, MD3Theme.TEXT_MUTED)
        }
    }

    private fun drawToolbar(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val searchBounds = this.searchBounds ?: return
        val sortBounds = this.sortBounds ?: return
        searchField.buildUi(
            scope, searchBounds, mouseX.toDouble(), mouseY.toDouble(), textRenderer,
            EpsilonTranslations.Gui.ACCOUNTS_SEARCH_PLACEHOLDER.getTranslatedName(), 1.0f,
        )
        sortButton.draw(scope, sortBounds, mouseX.toDouble(), mouseY.toDouble(), textRenderer, sortMode)
    }

    private fun drawAccountList(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val viewport = listViewport ?: return
        if (visible.isEmpty()) {
            drawEmptyState(scope)
            drawAddBar(scope, mouseX, mouseY)
            return
        }

        val contentHeight = visible.size * (AccountRow.HEIGHT + AccountRow.GAP)
        scope.viewport(listBuffer, viewport, scroll, maxScroll, contentHeight, mouseX, mouseY) { content ->
            var y = viewport.y - scroll
            for (i in visible.indices) {
                val row = visible[i]
                val account = row.getAccount()
                val bounds = UiRect(viewport.x, y, rowWidth, AccountRow.HEIGHT)
                val state = AccountRow.State(
                    isCurrent(account),
                    authenticating.get() === account,
                    authenticating.get() != null,
                    i == focusedIndex,
                    mouseX.toDouble(), mouseY.toDouble(),
                )
                row.draw(content, bounds, mouseX.toDouble(), mouseY.toDouble(), textRenderer, state)
                y += AccountRow.HEIGHT + AccountRow.GAP
            }
        }

        drawAddBar(scope, mouseX, mouseY)
    }

    private fun drawEmptyState(scope: UiTree.Scope) {
        val viewport = listViewport ?: return
        val filtered = rows.isNotEmpty()
        val glyph = if (filtered) IconChars.SEARCH_OFF else IconChars.NO_ACCOUNTS
        val message = if (filtered) {
            EpsilonTranslations.Gui.ACCOUNTS_SEARCH_EMPTY.getTranslatedName()
        } else {
            EpsilonTranslations.Gui.ACCOUNTS_EMPTY.getTranslatedName()
        }

        val glyphScale = 5.0f
        val glyphWidth = textRenderer.getWidth(glyph, glyphScale, StaticFontLoader.ICONS)
        val glyphHeight = textRenderer.getHeight(glyphScale, StaticFontLoader.ICONS)

        val msgScale = 1.25f
        val msgWidth = textRenderer.getWidth(message, msgScale)
        val msgHeight = textRenderer.getHeight(msgScale)

        val hint = if (filtered) null else EpsilonTranslations.Gui.ACCOUNTS_EMPTY_HINT.getTranslatedName()
        val hintScale = 1.0f
        val hintHeight = if (hint == null) 0.0f else textRenderer.getHeight(hintScale)
        val hintGap = if (hint == null) 0.0f else 6.0f

        val blockHeight = glyphHeight + 12.0f + msgHeight + hintGap + hintHeight
        val top = viewport.y + (viewport.height - blockHeight) / 2.0f

        scope.text(
            glyph, viewport.x + (viewport.width - glyphWidth) / 2.0f, top, glyphScale,
            MD3Theme.withAlpha(MD3Theme.TEXT_MUTED, 130), StaticFontLoader.ICONS,
        )
        scope.text(
            message, viewport.x + (viewport.width - msgWidth) / 2.0f, top + glyphHeight,
            msgScale, MD3Theme.TEXT_SECONDARY,
        )

        if (hint != null) {
            val hintWidth = textRenderer.getWidth(hint, hintScale)
            scope.text(
                hint, viewport.x + (viewport.width - hintWidth) / 2.0f,
                top + glyphHeight + msgHeight + hintGap, hintScale, MD3Theme.TEXT_MUTED,
            )
        }
    }

    private fun drawAddBar(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val gap = 4.0f
        val buttonWidth = (panelW - PAD * 2.0f - gap * 3.0f) / 4.0f
        val buttonHeight = 30.0f
        val labels = arrayOf(
            EpsilonTranslations.Gui.ACCOUNTS_ADD_CRACKED.getTranslatedName(),
            EpsilonTranslations.Gui.ACCOUNTS_ADD_ALTENING.getTranslatedName(),
            EpsilonTranslations.Gui.ACCOUNTS_ADD_SESSION.getTranslatedName(),
            EpsilonTranslations.Gui.ACCOUNTS_ADD_MICROSOFT.getTranslatedName(),
        )

        for (i in 0 until 4) {
            val x = panelX + PAD + i * (buttonWidth + gap)
            val rect = UiRect(x, addBarY, buttonWidth, buttonHeight)
            addButtons[i] = rect
            val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
            scope.button(
                x, addBarY, buttonWidth, buttonHeight, MD3Theme.CONTROL_RADIUS.toFloat(),
                MD3Theme.SECONDARY_CONTAINER, labels[i], 0.8f, MD3Theme.ON_SECONDARY_CONTAINER,
            )
            if (hovered) {
                scope.roundRect(
                    x, addBarY, buttonWidth, buttonHeight, MD3Theme.CONTROL_RADIUS.toFloat(),
                    MD3Theme.stateLayer(MD3Theme.PRIMARY, 0.16f, 36),
                )
            }
        }
    }

    private fun drawAddForm(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val viewport = listViewport
        if (microsoftWaiting) {
            if (viewport != null) {
                drawCenteredText(
                    scope, EpsilonTranslations.Gui.ACCOUNTS_WAITING_MICROSOFT.getTranslatedName(),
                    viewport, MD3Theme.TEXT_SECONDARY, 0.66f,
                )
            }
            drawFormButtons(scope, false)
            return
        }
        if (microsoftError) {
            if (viewport != null) {
                drawCenteredText(
                    scope, EpsilonTranslations.Gui.ACCOUNTS_MICROSOFT_ERROR.getTranslatedName(),
                    viewport, MD3Theme.ERROR, 0.66f,
                )
            }
            drawFormButtons(scope, false)
            return
        }

        val modeLabel = when (addMode) {
            AddMode.CRACKED -> EpsilonTranslations.Gui.ACCOUNTS_ADD_CRACKED.getTranslatedName()
            AddMode.ALTENING -> EpsilonTranslations.Gui.ACCOUNTS_ADD_ALTENING.getTranslatedName()
            AddMode.SESSION -> EpsilonTranslations.Gui.ACCOUNTS_ADD_SESSION.getTranslatedName()
            else -> ""
        }
        val formTitle = EpsilonTranslations.Gui.ACCOUNTS_ADD.getTranslatedName() + " " + modeLabel
        scope.text(formTitle, panelX + PAD, formTitleY, 0.95f, MD3Theme.TEXT_PRIMARY)

        val placeholder = when (addMode) {
            AddMode.CRACKED -> EpsilonTranslations.Gui.ACCOUNTS_NAME_PLACEHOLDER.getTranslatedName()
            else -> EpsilonTranslations.Gui.ACCOUNTS_TOKEN_PLACEHOLDER.getTranslatedName()
        }
        val fieldBounds = formFieldBounds
        if (fieldBounds != null) {
            field.buildUi(scope, fieldBounds, mouseX.toDouble(), mouseY.toDouble(), textRenderer, placeholder, 0.7f)
        }

        drawFormButtons(scope, true)
    }

    private fun drawFormButtons(scope: UiTree.Scope, withAdd: Boolean) {
        val buttonHeight = 30.0f
        val cancelWidth = 84.0f
        val addWidth = 104.0f
        val y = formButtonY

        val cancelBtn = UiRect(panelX + PAD, y, cancelWidth, buttonHeight)
        formCancelBtn = cancelBtn
        scope.button(
            cancelBtn.x, cancelBtn.y, cancelBtn.width, cancelBtn.height, MD3Theme.CONTROL_RADIUS.toFloat(),
            Color(0, 0, 0, 0), EpsilonTranslations.Gui.ACCOUNTS_CANCEL.getTranslatedName(), 0.6f, MD3Theme.TEXT_PRIMARY,
        )
        scope.outline(
            cancelBtn.x, cancelBtn.y, cancelBtn.width, cancelBtn.height,
            MD3Theme.CONTROL_RADIUS.toFloat(), 1.0f, MD3Theme.OUTLINE,
        )

        if (withAdd) {
            val addBtn = UiRect(panelX + panelW - PAD - addWidth, y, addWidth, buttonHeight)
            formAddBtn = addBtn
            val canAdd = field.getText().isNotBlank()
            val bg = if (canAdd) MD3Theme.PRIMARY else MD3Theme.withAlpha(MD3Theme.PRIMARY, 80)
            val fg = if (canAdd) MD3Theme.ON_PRIMARY else MD3Theme.withAlpha(MD3Theme.ON_PRIMARY, 100)
            scope.button(
                addBtn.x, addBtn.y, addBtn.width, addBtn.height, MD3Theme.CONTROL_RADIUS.toFloat(), bg,
                EpsilonTranslations.Gui.ACCOUNTS_ADD.getTranslatedName(), 0.6f, fg,
            )
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val e = LuminRenderSystem.toEpsilonMouseEvent(event)
        val mx = e.x()
        val my = e.y()
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val backBtn = headerBackBtn
            if (backBtn != null && backBtn.contains(mx, my)) {
                if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) exitAddMode() else close()
                return true
            }

            if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) {
                val cancelBtn = formCancelBtn
                if (cancelBtn != null && cancelBtn.contains(mx, my)) {
                    exitAddMode()
                    return true
                }
                if (addMode != AddMode.NONE) {
                    val addBtn = formAddBtn
                    if (addBtn != null && addBtn.contains(mx, my)) {
                        submitAdd()
                        return true
                    }
                    val fieldBounds = formFieldBounds
                    if (fieldBounds != null && field.focusIfContains(fieldBounds, mx, my)) {
                        return true
                    }
                    field.blur()
                }
                return true
            }

            val sortBounds = this.sortBounds
            if (sortBounds != null && sortBounds.contains(mx, my)) {
                sortMode = sortMode.next()
                scroll = 0.0f
                scrollVelocity = 0.0f
                return true
            }

            val searchBounds = this.searchBounds
            if (searchBounds != null && searchField.focusIfContains(searchBounds, mx, my)) {
                return true
            }
            searchField.blur()

            val viewport = listViewport
            if (viewport != null && viewport.contains(mx, my)) {
                for (i in visible.indices) {
                    val row = visible[i]
                    val deleteBounds = row.getDeleteBounds()
                    if (deleteBounds != null && deleteBounds.contains(mx, my)) {
                        focusedIndex = i
                        if (authenticating.get() === row.getAccount()) return true

                        if (row.isConfirmingDelete()) {
                            row.setConfirmingDelete(false)
                            AccountManager.remove(row.getAccount())
                        } else {
                            row.setConfirmingDelete(true)
                        }
                        return true
                    }
                    val rowBounds = row.getBounds()
                    if (rowBounds != null && rowBounds.contains(mx, my)) {
                        focusedIndex = i
                        login(row.getAccount())
                        return true
                    }
                }
            }

            for (i in 0 until 4) {
                val button = addButtons[i]
                if (button != null && button.contains(mx, my)) {
                    when (i) {
                        0 -> startAdd(AddMode.CRACKED)
                        1 -> startAdd(AddMode.ALTENING)
                        2 -> startAdd(AddMode.SESSION)
                        3 -> startAddMicrosoft()
                    }
                    return true
                }
            }

            clearDeleteConfirmations()
        }
        return super.mouseClicked(e, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val ex = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val ey = LuminRenderSystem.toEpsilonMouseY(mouseY)
        val viewport = listViewport
        if (addMode == AddMode.NONE && !microsoftWaiting && viewport != null && viewport.contains(ex, ey)) {
            scrollVelocity -= scrollY.toFloat() * 24.0f
            return true
        }
        return super.mouseScrolled(ex, ey, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) {
            if (event.isEscape) {
                exitAddMode()
                return true
            }
            if (isEnter(event)) {
                submitAdd()
                return true
            }
            if (addMode != AddMode.NONE && field.keyPressed(event)) {
                return true
            }
            return true
        }

        when (event.key()) {
            InputConstants.KEY_DOWN -> {
                moveFocus(1)
                return true
            }

            InputConstants.KEY_UP -> {
                moveFocus(-1)
                return true
            }
        }

        val focused = focusedAccount()
        if (focused != null) {
            if (isEnter(event)) {
                login(focused)
                return true
            }
            if (event.key() == InputConstants.KEY_DELETE && !searchField.isFocused()) {
                val row = visible[focusedIndex]
                if (authenticating.get() === focused) return true
                if (row.isConfirmingDelete()) {
                    row.setConfirmingDelete(false)
                    AccountManager.remove(focused)
                } else {
                    row.setConfirmingDelete(true)
                }
                return true
            }
        }

        if (searchField.keyPressed(event)) {
            return true
        }

        if (event.isEscape) {
            if (!searchField.isEmpty()) {
                searchField.clear()
                scroll = 0.0f
                scrollVelocity = 0.0f
                return true
            }
            searchField.blur()
            close()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (addMode != AddMode.NONE) {
            field.charTyped(event)
            return true
        }
        if (!searchField.isFocused()) {
            searchField.focus()
        }
        if (searchField.charTyped(event)) {
            scroll = 0.0f
            scrollVelocity = 0.0f
            return true
        }
        return super.charTyped(event)
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        this.preeditOverlay = if (event != null) IMEPreeditOverlay(event, this.font, 10) else null
        return true
    }

    override fun onClose() {
        exitAddMode()
        val minecraft = this.minecraft
        minecraft.gui.setScreen(MainMenuScreen)
    }

    override fun removed() {
        super.removed()
        exitAddMode()
        searchField.blur()
        IMEFocusHelper.forceDeactivate()
        preeditOverlay = null
        renderTarget?.close()
        renderTarget = null
    }

    private fun moveFocus(delta: Int) {
        if (visible.isEmpty()) {
            focusedIndex = -1
            return
        }
        clearDeleteConfirmations()
        if (focusedIndex < 0) {
            focusedIndex = if (delta > 0) 0 else visible.size - 1
        } else {
            focusedIndex = (focusedIndex + delta).mod(visible.size)
        }
        scrollIntoView()
    }

    private fun scrollIntoView() {
        val viewport = listViewport ?: return
        if (focusedIndex < 0) return
        val step = AccountRow.HEIGHT + AccountRow.GAP
        val top = focusedIndex * step
        val bottom = top + AccountRow.HEIGHT

        if (top < scroll) {
            scroll = top
        } else if (bottom > scroll + viewport.height) {
            scroll = bottom - viewport.height
        }
        scroll = Mth.clamp(scroll, 0.0f, maxScroll)
        scrollVelocity = 0.0f
    }

    private fun clearDeleteConfirmations() {
        for (row in rows) row.setConfirmingDelete(false)
    }

    private fun login(account: Account<*>) {
        if (!authenticating.compareAndSet(null, account)) return

        ExecutorManager.execute {
            try {
                if (account.fetchInfo() && account.login() && account.type != AccountType.Cracked) {
                    account.cache.loadHead()
                }
            } finally {
                authenticating.set(null)
            }
        }
    }

    private fun startAdd(mode: AddMode) {
        searchField.blur()
        addMode = mode
        field.clear()
        field.setMaxLength(if (mode == AddMode.CRACKED) 16 else 512)
        field.focus()
    }

    private fun startAddMicrosoft() {
        searchField.blur()
        microsoftWaiting = true
        microsoftError = false
        val session = ++microsoftSession
        MicrosoftLogin.getRefreshToken { token ->
            mc.execute {
                if (session != microsoftSession) return@execute

                microsoftWaiting = false
                if (token == null) {
                    microsoftError = true
                } else {
                    microsoftError = false
                    addAccount(MicrosoftAccount(token))
                }
            }
        }
    }

    private fun submitAdd() {
        if (addMode == AddMode.NONE) return
        val input = field.getText().trim()
        if (input.isBlank()) return

        val account: Account<*>? = when (addMode) {
            AddMode.CRACKED -> CrackedAccount(input)
            AddMode.ALTENING -> TheAlteningAccount(input)
            AddMode.SESSION -> SessionAccount(input)
            else -> null
        }
        if (account == null) return

        exitAddMode()
        addAccount(account)
    }

    private fun addAccount(account: Account<*>) {
        if (AccountManager.exists(account) || !authenticating.compareAndSet(null, account)) return

        ExecutorManager.execute {
            try {
                if (!account.fetchInfo()) return@execute
                AccountManager.add(account)
                if (account.type != AccountType.Cracked) account.cache.loadHead()
            } finally {
                authenticating.set(null)
            }
        }
    }

    private fun exitAddMode() {
        if (microsoftWaiting) {
            microsoftSession++
            MicrosoftLogin.stopServer()
        }
        addMode = AddMode.NONE
        microsoftWaiting = false
        microsoftError = false
        field.blur()
        field.clear()
        preeditOverlay = null
    }

    private fun isCurrent(account: Account<*>): Boolean {
        val cachedUuid = account.cache.uuid
        if (!cachedUuid.isNullOrBlank()) {
            val currentUuid = mc.user.profileId.toString().replace("-", "")
            return currentUuid.equals(cachedUuid.replace("-", ""), ignoreCase = true)
        }
        return mc.user.name.equals(account.username, ignoreCase = true)
    }

    private fun close() {
        val minecraft = this.minecraft
        minecraft.gui.setScreen(MainMenuScreen)
    }

    private fun drawCenteredText(scope: UiTree.Scope, text: String, area: UiRect, color: Color, scale: Float) {
        val textWidth = textRenderer.getWidth(text, scale)
        val textHeight = textRenderer.getHeight(scale)
        scope.text(
            text, area.x + (area.width - textWidth) / 2.0f, area.y + (area.height - textHeight) / 2.0f,
            scale, color,
        )
    }

    private fun drawIconGlyph(
        scope: UiTree.Scope,
        rect: UiRect,
        glyph: String,
        scale: Float,
        color: Color,
        hoverProgress: Float,
    ) {
        if (hoverProgress > 0.01f) {
            scope.roundRect(
                rect.x, rect.y, rect.width, rect.height, rect.height / 2.0f,
                MD3Theme.stateLayer(color, hoverProgress, 36),
            )
        }
        val glyphWidth = textRenderer.getWidth(glyph, scale, StaticFontLoader.ICONS)
        val glyphHeight = textRenderer.getHeight(scale, StaticFontLoader.ICONS)
        scope.text(
            glyph, rect.x + (rect.width - glyphWidth) / 2.0f, rect.y + (rect.height - glyphHeight) / 2.0f,
            scale, color, StaticFontLoader.ICONS,
        )
    }

    private fun isEnter(event: KeyEvent): Boolean {
        return event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
    }

    private val SCRIM: Color = Color(0, 0, 0, 150)
    private const val PAD = 12.0f
    private const val HEADER_HEIGHT = 48.0f
    private const val TOOLBAR_HEIGHT = 30.0f
    private const val ADD_BAR_HEIGHT = 34.0f
    private const val BACK_BUTTON_SIZE = 32.0f

}
