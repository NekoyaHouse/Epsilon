package com.github.epsilon.gui.screen.accounts;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.MicrosoftLogin;
import com.github.epsilon.accounts.types.CrackedAccount;
import com.github.epsilon.accounts.types.MicrosoftAccount;
import com.github.epsilon.accounts.types.SessionAccount;
import com.github.epsilon.accounts.types.TheAlteningAccount;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.UiScrollBar;
import com.github.epsilon.gui.lib.control.UiTextField;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.AccountManager;
import com.github.epsilon.managers.ExecutorManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.epsilon.Constants.mc;

public class AccountsScreen extends Screen {

    public static final AccountsScreen INSTANCE = new AccountsScreen();

    private static final Color SCRIM = new Color(0, 0, 0, 150);

    private static final float PAD = 12.0f;
    private static final float HEADER_HEIGHT = 48.0f;
    private static final float TOOLBAR_HEIGHT = 30.0f;
    private static final float ADD_BAR_HEIGHT = 34.0f;
    private static final float BACK_BUTTON_SIZE = 32.0f;

    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);
    private final TextRenderer textRenderer = TextRenderer.create();
    private final UiContentBuffer listBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);

    private final List<AccountRow> rows = new ArrayList<>();
    private final List<AccountRow> visible = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;

    private float panelX, panelY, panelW, panelH;
    private UiRect headerBackBtn;
    private UiRect searchBounds;
    private UiRect sortBounds;
    private UiRect listViewport;
    private float addBarY;
    private float rowWidth;
    private final UiRect[] addButtons = new UiRect[4];
    private float formTitleY;
    private UiRect formFieldBounds;
    private UiRect formAddBtn;
    private UiRect formCancelBtn;
    private float formButtonY;

    private float scroll;
    private float maxScroll;
    private float scrollVelocity;

    private final UiTextField searchField = new UiTextField(32).leadingIcon(IconChars.SEARCH, 1.0f);
    private final AccountSortButton sortButton = new AccountSortButton();
    private AccountSortMode sortMode = AccountSortMode.ADDED;
    private int focusedIndex = -1;

    private AddMode addMode = AddMode.NONE;
    private final UiTextField field = new UiTextField(512);
    private boolean microsoftWaiting;
    private boolean microsoftError;
    private int microsoftSession;

    private final AtomicReference<Account<?>> authenticating = new AtomicReference<>();

    private AccountsScreen() {
        super(Component.literal("Accounts"));
    }

    private enum AddMode {
        NONE,
        CRACKED,
        ALTENING,
        SESSION
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        for (Account<?> account : AccountManager.INSTANCE.getAccounts()) {
            account.getCache().loadHead();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("accounts-screen", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        LuminRenderSystem.setActiveTarget(renderTarget);

        int ex = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int ey = LuminRenderSystem.toEpsilonMouseY(mouseY);
        int w = LuminRenderSystem.getScaledWidthInt();
        int h = LuminRenderSystem.getScaledHeightInt();

        layout(w, h);
        scroll = Mth.clamp(scroll + scrollVelocity * partialTick, 0.0f, maxScroll);
        scrollVelocity *= 0.86f;

        scene.beginFrame();
        listBuffer.clear();

        UiTree tree = UiTree.build(scope -> {
            scope.rect(0, 0, w, h, SCRIM);
            scope.layer(0, panel -> {
                panel.shadow(panelX, panelY, panelW, panelH, MD3Theme.PANEL_RADIUS, MD3Theme.PANEL_SHADOW_BLUR, MD3Theme.SHADOW);
                panel.roundRect(panelX, panelY, panelW, panelH, MD3Theme.PANEL_RADIUS, MD3Theme.SURFACE);
            });
            scope.layer(10, content -> {
                drawHeader(content, ex, ey);
                if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) {
                    drawAddForm(content, ex, ey);
                } else {
                    drawToolbar(content, ex, ey);
                    drawAccountList(content, ex, ey);
                }
            });
        });
        scene.submit(UiLayer.CONTENT, tree);

        scene.flush();
        listBuffer.flush();
        scene.clear();

        LuminRenderSystem.setActiveTarget(null);

        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void layout(int w, int h) {
        panelW = Math.min(460, w - 24);
        panelH = Math.min(560, h - 24);
        panelX = (w - panelW) / 2.0f;
        panelY = (h - panelH) / 2.0f;

        headerBackBtn = new UiRect(panelX + 10, panelY + (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2.0f, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);

        float contentTop = panelY + HEADER_HEIGHT;
        float contentBottom = panelY + panelH - PAD;

        addBarY = contentBottom - ADD_BAR_HEIGHT;

        float sortWidth = sortButton.measureWidth(textRenderer, sortMode);
        float toolbarY = contentTop + 4;
        sortBounds = new UiRect(panelX + panelW - PAD - sortWidth, toolbarY, sortWidth, TOOLBAR_HEIGHT);
        searchBounds = new UiRect(panelX + PAD, toolbarY, panelW - PAD * 2 - sortWidth - 6, TOOLBAR_HEIGHT);

        float listTop = toolbarY + TOOLBAR_HEIGHT + 8;
        listViewport = new UiRect(panelX + PAD, listTop, panelW - PAD * 2, Math.max(0.0f, addBarY - listTop - 10));

        formTitleY = contentTop + 12;
        formFieldBounds = new UiRect(panelX + PAD, contentTop + 52, panelW - PAD * 2, 40);
        formButtonY = contentBottom - 30;

        syncRows();
        rebuildVisible();

        float contentHeight = visible.size() * (AccountRow.HEIGHT + AccountRow.GAP);
        maxScroll = Math.max(0, contentHeight - listViewport.height());
        rowWidth = maxScroll > 0 ? listViewport.width() - UiScrollBar.TOTAL_WIDTH : listViewport.width();
    }

    private void syncRows() {
        rows.removeIf(row -> !AccountManager.INSTANCE.exists(row.getAccount()));
        for (Account<?> account : AccountManager.INSTANCE.getAccounts()) {
            if (rows.stream().noneMatch(row -> row.getAccount() == account)) {
                rows.add(new AccountRow(account));
            }
        }
    }

    private void rebuildVisible() {
        Account<?> previouslyFocused = focusedAccount();

        visible.clear();
        String query = searchField.getText().trim().toLowerCase();
        for (AccountRow row : rows) {
            if (query.isEmpty() || matches(row.getAccount(), query)) {
                visible.add(row);
            }
        }

        var comparator = sortMode.getComparator();
        if (comparator != null) {
            visible.sort((a, b) -> comparator.compare(a.getAccount(), b.getAccount()));
        }

        if (previouslyFocused == null) {
            focusedIndex = visible.isEmpty() ? -1 : Math.min(focusedIndex, visible.size() - 1);
            return;
        }

        focusedIndex = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).getAccount() == previouslyFocused) {
                focusedIndex = i;
                break;
            }
        }
    }

    private static boolean matches(Account<?> account, String query) {
        return account.getUsername().toLowerCase().contains(query)
                || account.getType().name().toLowerCase().contains(query);
    }

    private Account<?> focusedAccount() {
        if (focusedIndex < 0 || focusedIndex >= visible.size()) return null;
        return visible.get(focusedIndex).getAccount();
    }

    private void drawHeader(UiTree.Scope scope, int mouseX, int mouseY) {
        boolean backHovered = headerBackBtn.contains(mouseX, mouseY);
        drawIconGlyph(scope, headerBackBtn, IconChars.ARROW_BACK, 1.0f, MD3Theme.TEXT_PRIMARY, backHovered ? 0.9f : 0.0f);

        String title = EpsilonTranslations.Gui.ACCOUNTS_TITLE.getTranslatedName();
        float titleScale = 1.2f;
        float titleHeight = textRenderer.getHeight(titleScale, StaticFontLoader.JURA_LIGHT);
        scope.text(title, panelX + 50, panelY + (HEADER_HEIGHT - titleHeight) / 2.0f, titleScale, MD3Theme.TEXT_PRIMARY, StaticFontLoader.JURA_LIGHT);

        String currentName = mc.getUser().getName();
        if (!currentName.isBlank()) {
            float scale = 0.6f;
            float labelWidth = textRenderer.getWidth(currentName, scale);
            float labelY = panelY + (HEADER_HEIGHT - textRenderer.getHeight(scale)) / 2.0f;
            scope.text(currentName, panelX + panelW - PAD - labelWidth, labelY, scale, MD3Theme.TEXT_MUTED);
        }
    }

    private void drawToolbar(UiTree.Scope scope, int mouseX, int mouseY) {
        searchField.buildUi(scope, searchBounds, mouseX, mouseY, textRenderer, EpsilonTranslations.Gui.ACCOUNTS_SEARCH_PLACEHOLDER.getTranslatedName(), 1.0f);
        sortButton.draw(scope, sortBounds, mouseX, mouseY, textRenderer, sortMode);
    }

    private void drawAccountList(UiTree.Scope scope, int mouseX, int mouseY) {
        if (visible.isEmpty()) {
            drawEmptyState(scope);
            drawAddBar(scope, mouseX, mouseY);
            return;
        }

        float contentHeight = visible.size() * (AccountRow.HEIGHT + AccountRow.GAP);
        scope.viewport(listBuffer, listViewport, scroll, maxScroll, contentHeight, mouseX, mouseY, content -> {
            float y = listViewport.y() - scroll;
            for (int i = 0; i < visible.size(); i++) {
                AccountRow row = visible.get(i);
                Account<?> account = row.getAccount();
                UiRect bounds = new UiRect(listViewport.x(), y, rowWidth, AccountRow.HEIGHT);
                AccountRow.State state = new AccountRow.State(
                        isCurrent(account),
                        authenticating.get() == account,
                        authenticating.get() != null,
                        i == focusedIndex,
                        mouseX, mouseY);
                row.draw(content, bounds, mouseX, mouseY, textRenderer, state);
                y += AccountRow.HEIGHT + AccountRow.GAP;
            }
        });

        drawAddBar(scope, mouseX, mouseY);
    }

    private void drawEmptyState(UiTree.Scope scope) {
        boolean filtered = !rows.isEmpty();
        String glyph = filtered
                ? IconChars.SEARCH_OFF
                : IconChars.NO_ACCOUNTS;
        String message = filtered
                ? EpsilonTranslations.Gui.ACCOUNTS_SEARCH_EMPTY.getTranslatedName()
                : EpsilonTranslations.Gui.ACCOUNTS_EMPTY.getTranslatedName();

        float glyphScale = 5.0f;
        float glyphWidth = textRenderer.getWidth(glyph, glyphScale, StaticFontLoader.ICONS);
        float glyphHeight = textRenderer.getHeight(glyphScale, StaticFontLoader.ICONS);

        float msgScale = 1.25f;
        float msgWidth = textRenderer.getWidth(message, msgScale);
        float msgHeight = textRenderer.getHeight(msgScale);

        String hint = filtered ? null : EpsilonTranslations.Gui.ACCOUNTS_EMPTY_HINT.getTranslatedName();
        float hintScale = 1.0f;
        float hintHeight = hint == null ? 0.0f : textRenderer.getHeight(hintScale);
        float hintGap = hint == null ? 0.0f : 6.0f;

        float blockHeight = glyphHeight + 12.0f + msgHeight + hintGap + hintHeight;
        float top = listViewport.y() + (listViewport.height() - blockHeight) / 2.0f;

        scope.text(glyph, listViewport.x() + (listViewport.width() - glyphWidth) / 2.0f, top, glyphScale, MD3Theme.withAlpha(MD3Theme.TEXT_MUTED, 130), StaticFontLoader.ICONS);
        scope.text(message, listViewport.x() + (listViewport.width() - msgWidth) / 2.0f, top + glyphHeight, msgScale, MD3Theme.TEXT_SECONDARY);

        if (hint != null) {
            float hintWidth = textRenderer.getWidth(hint, hintScale);
            scope.text(hint, listViewport.x() + (listViewport.width() - hintWidth) / 2.0f, top + glyphHeight + msgHeight + hintGap, hintScale, MD3Theme.TEXT_MUTED);
        }
    }

    private void drawAddBar(UiTree.Scope scope, int mouseX, int mouseY) {
        float gap = 4.0f;
        float buttonWidth = (panelW - PAD * 2 - gap * 3) / 4.0f;
        float buttonHeight = 30.0f;
        String[] labels = {
                EpsilonTranslations.Gui.ACCOUNTS_ADD_CRACKED.getTranslatedName(),
                EpsilonTranslations.Gui.ACCOUNTS_ADD_ALTENING.getTranslatedName(),
                EpsilonTranslations.Gui.ACCOUNTS_ADD_SESSION.getTranslatedName(),
                EpsilonTranslations.Gui.ACCOUNTS_ADD_MICROSOFT.getTranslatedName()
        };

        for (int i = 0; i < 4; i++) {
            float x = panelX + PAD + i * (buttonWidth + gap);
            UiRect rect = new UiRect(x, addBarY, buttonWidth, buttonHeight);
            addButtons[i] = rect;
            boolean hovered = rect.contains(mouseX, mouseY);
            scope.button(x, addBarY, buttonWidth, buttonHeight, MD3Theme.CONTROL_RADIUS, MD3Theme.SECONDARY_CONTAINER, labels[i], 0.8f, MD3Theme.ON_SECONDARY_CONTAINER);
            if (hovered) {
                scope.roundRect(x, addBarY, buttonWidth, buttonHeight, MD3Theme.CONTROL_RADIUS, MD3Theme.stateLayer(MD3Theme.PRIMARY, 0.16f, 36));
            }
        }
    }

    private void drawAddForm(UiTree.Scope scope, int mouseX, int mouseY) {
        if (microsoftWaiting) {
            drawCenteredText(scope, EpsilonTranslations.Gui.ACCOUNTS_WAITING_MICROSOFT.getTranslatedName(), listViewport, MD3Theme.TEXT_SECONDARY, 0.66f);
            drawFormButtons(scope, false);
            return;
        }
        if (microsoftError) {
            drawCenteredText(scope, EpsilonTranslations.Gui.ACCOUNTS_MICROSOFT_ERROR.getTranslatedName(), listViewport, MD3Theme.ERROR, 0.66f);
            drawFormButtons(scope, false);
            return;
        }

        String modeLabel = switch (addMode) {
            case CRACKED -> EpsilonTranslations.Gui.ACCOUNTS_ADD_CRACKED.getTranslatedName();
            case ALTENING -> EpsilonTranslations.Gui.ACCOUNTS_ADD_ALTENING.getTranslatedName();
            case SESSION -> EpsilonTranslations.Gui.ACCOUNTS_ADD_SESSION.getTranslatedName();
            default -> "";
        };
        String formTitle = EpsilonTranslations.Gui.ACCOUNTS_ADD.getTranslatedName() + " " + modeLabel;
        scope.text(formTitle, panelX + PAD, formTitleY, 0.95f, MD3Theme.TEXT_PRIMARY);

        String placeholder = switch (addMode) {
            case CRACKED -> EpsilonTranslations.Gui.ACCOUNTS_NAME_PLACEHOLDER.getTranslatedName();
            default -> EpsilonTranslations.Gui.ACCOUNTS_TOKEN_PLACEHOLDER.getTranslatedName();
        };
        field.buildUi(scope, formFieldBounds, mouseX, mouseY, textRenderer, placeholder, 0.7f);

        drawFormButtons(scope, true);
    }

    private void drawFormButtons(UiTree.Scope scope, boolean withAdd) {
        float buttonHeight = 30.0f;
        float cancelWidth = 84.0f;
        float addWidth = 104.0f;
        float y = formButtonY;

        formCancelBtn = new UiRect(panelX + PAD, y, cancelWidth, buttonHeight);
        scope.button(formCancelBtn.x(), formCancelBtn.y(), formCancelBtn.width(), formCancelBtn.height(), MD3Theme.CONTROL_RADIUS,
                new Color(0, 0, 0, 0), EpsilonTranslations.Gui.ACCOUNTS_CANCEL.getTranslatedName(), 0.6f, MD3Theme.TEXT_PRIMARY);
        scope.outline(formCancelBtn.x(), formCancelBtn.y(), formCancelBtn.width(), formCancelBtn.height(), MD3Theme.CONTROL_RADIUS, 1.0f, MD3Theme.OUTLINE);

        if (withAdd) {
            formAddBtn = new UiRect(panelX + panelW - PAD - addWidth, y, addWidth, buttonHeight);
            boolean canAdd = !field.getText().isBlank();
            Color bg = canAdd ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.PRIMARY, 80);
            Color fg = canAdd ? MD3Theme.ON_PRIMARY : MD3Theme.withAlpha(MD3Theme.ON_PRIMARY, 100);
            scope.button(formAddBtn.x(), formAddBtn.y(), formAddBtn.width(), formAddBtn.height(), MD3Theme.CONTROL_RADIUS, bg,
                    EpsilonTranslations.Gui.ACCOUNTS_ADD.getTranslatedName(), 0.6f, fg);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent e = LuminRenderSystem.toEpsilonMouseEvent(event);
        double mx = e.x();
        double my = e.y();
        if (event.button() == 0) {
            if (headerBackBtn != null && headerBackBtn.contains(mx, my)) {
                if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) exitAddMode();
                else close();
                return true;
            }

            if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) {
                if (formCancelBtn != null && formCancelBtn.contains(mx, my)) {
                    exitAddMode();
                    return true;
                }
                if (addMode != AddMode.NONE) {
                    if (formAddBtn != null && formAddBtn.contains(mx, my)) {
                        submitAdd();
                        return true;
                    }
                    if (field.focusIfContains(formFieldBounds, mx, my)) {
                        return true;
                    }
                    field.blur();
                }
                return true;
            }

            if (sortBounds != null && sortBounds.contains(mx, my)) {
                sortMode = sortMode.next();
                scroll = 0.0f;
                scrollVelocity = 0.0f;
                return true;
            }

            if (searchField.focusIfContains(searchBounds, mx, my)) {
                return true;
            }
            searchField.blur();

            if (listViewport != null && listViewport.contains(mx, my)) {
                for (int i = 0; i < visible.size(); i++) {
                    AccountRow row = visible.get(i);
                    if (row.getDeleteBounds() != null && row.getDeleteBounds().contains(mx, my)) {
                        focusedIndex = i;
                        if (authenticating.get() == row.getAccount()) return true;

                        if (row.isConfirmingDelete()) {
                            row.setConfirmingDelete(false);
                            AccountManager.INSTANCE.remove(row.getAccount());
                        } else {
                            row.setConfirmingDelete(true);
                        }
                        return true;
                    }
                    if (row.getBounds() != null && row.getBounds().contains(mx, my)) {
                        focusedIndex = i;
                        login(row.getAccount());
                        return true;
                    }
                }
            }

            for (int i = 0; i < 4; i++) {
                if (addButtons[i] != null && addButtons[i].contains(mx, my)) {
                    switch (i) {
                        case 0 -> startAdd(AddMode.CRACKED);
                        case 1 -> startAdd(AddMode.ALTENING);
                        case 2 -> startAdd(AddMode.SESSION);
                        case 3 -> startAddMicrosoft();
                    }
                    return true;
                }
            }

            clearDeleteConfirmations();
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double ex = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double ey = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (addMode == AddMode.NONE && !microsoftWaiting && listViewport != null && listViewport.contains(ex, ey)) {
            scrollVelocity -= (float) scrollY * 24.0f;
            return true;
        }
        return super.mouseScrolled(ex, ey, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (addMode != AddMode.NONE || microsoftWaiting || microsoftError) {
            if (event.isEscape()) {
                exitAddMode();
                return true;
            }
            if (isEnter(event)) {
                submitAdd();
                return true;
            }
            if (addMode != AddMode.NONE && field.keyPressed(event)) {
                return true;
            }
            return true;
        }

        switch (event.key()) {
            case GLFW.GLFW_KEY_DOWN -> {
                moveFocus(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveFocus(-1);
                return true;
            }
            default -> {
            }
        }

        Account<?> focused = focusedAccount();
        if (focused != null) {
            if (isEnter(event)) {
                login(focused);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_DELETE && !searchField.isFocused()) {
                AccountRow row = visible.get(focusedIndex);
                if (authenticating.get() == focused) return true;
                if (row.isConfirmingDelete()) {
                    row.setConfirmingDelete(false);
                    AccountManager.INSTANCE.remove(focused);
                } else {
                    row.setConfirmingDelete(true);
                }
                return true;
            }
        }

        if (searchField.keyPressed(event)) {
            return true;
        }

        if (event.isEscape()) {
            if (!searchField.isEmpty()) {
                searchField.clear();
                scroll = 0.0f;
                scrollVelocity = 0.0f;
                return true;
            }
            searchField.blur();
            close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (addMode != AddMode.NONE) {
            field.charTyped(event);
            return true;
        }
        if (!searchField.isFocused()) {
            searchField.focus();
        }
        if (searchField.charTyped(event)) {
            scroll = 0.0f;
            scrollVelocity = 0.0f;
            return true;
        }
        return super.charTyped(event);
    }

    private static boolean isEnter(KeyEvent event) {
        return event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
    }

    private void moveFocus(int delta) {
        if (visible.isEmpty()) {
            focusedIndex = -1;
            return;
        }
        clearDeleteConfirmations();
        if (focusedIndex < 0) {
            focusedIndex = delta > 0 ? 0 : visible.size() - 1;
        } else {
            focusedIndex = Math.floorMod(focusedIndex + delta, visible.size());
        }
        scrollIntoView();
    }

    private void scrollIntoView() {
        if (focusedIndex < 0 || listViewport == null) return;
        float step = AccountRow.HEIGHT + AccountRow.GAP;
        float top = focusedIndex * step;
        float bottom = top + AccountRow.HEIGHT;

        if (top < scroll) {
            scroll = top;
        } else if (bottom > scroll + listViewport.height()) {
            scroll = bottom - listViewport.height();
        }
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
        scrollVelocity = 0.0f;
    }

    private void clearDeleteConfirmations() {
        for (AccountRow row : rows) row.setConfirmingDelete(false);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public void onClose() {
        exitAddMode();
        minecraft.gui.setScreen(MainMenuScreen.INSTANCE);
    }

    @Override
    public void removed() {
        super.removed();
        exitAddMode();
        searchField.blur();
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
    }

    private void login(Account<?> account) {
        if (!authenticating.compareAndSet(null, account)) return;

        ExecutorManager.INSTANCE.execute(() -> {
            try {
                if (account.fetchInfo() && account.login() && account.getType() != AccountType.Cracked) {
                    account.getCache().loadHead();
                }
            } finally {
                authenticating.set(null);
            }
        });
    }

    private void startAdd(AddMode mode) {
        searchField.blur();
        addMode = mode;
        field.clear();
        field.setMaxLength(mode == AddMode.CRACKED ? 16 : 512);
        field.focus();
    }

    private void startAddMicrosoft() {
        searchField.blur();
        microsoftWaiting = true;
        microsoftError = false;
        final int session = ++microsoftSession;
        MicrosoftLogin.getRefreshToken(token -> mc.execute(() -> {
            if (session != microsoftSession) return;

            microsoftWaiting = false;
            if (token == null) {
                microsoftError = true;
            } else {
                microsoftError = false;
                addAccount(new MicrosoftAccount(token));
            }
        }));
    }

    private void submitAdd() {
        if (addMode == AddMode.NONE) return;
        String input = field.getText().trim();
        if (input.isBlank()) return;

        Account<?> account = switch (addMode) {
            case CRACKED -> new CrackedAccount(input);
            case ALTENING -> new TheAlteningAccount(input);
            case SESSION -> new SessionAccount(input);
            default -> null;
        };
        if (account == null) return;

        exitAddMode();
        addAccount(account);
    }

    private void addAccount(Account<?> account) {
        if (AccountManager.INSTANCE.exists(account) || !authenticating.compareAndSet(null, account)) return;

        ExecutorManager.INSTANCE.execute(() -> {
            try {
                if (!account.fetchInfo()) return;
                AccountManager.INSTANCE.add(account);
                if (account.getType() != AccountType.Cracked) account.getCache().loadHead();
            } finally {
                authenticating.set(null);
            }
        });
    }

    private void exitAddMode() {
        if (microsoftWaiting) {
            microsoftSession++;
            MicrosoftLogin.stopServer();
        }
        addMode = AddMode.NONE;
        microsoftWaiting = false;
        microsoftError = false;
        field.blur();
        field.clear();
        preeditOverlay = null;
    }

    private boolean isCurrent(Account<?> account) {
        String cachedUuid = account.getCache().uuid;
        if (cachedUuid != null && !cachedUuid.isBlank()) {
            String currentUuid = mc.getUser().getProfileId().toString().replace("-", "");
            return currentUuid.equalsIgnoreCase(cachedUuid.replace("-", ""));
        }
        return mc.getUser().getName().equalsIgnoreCase(account.getUsername());
    }

    private void close() {
        minecraft.gui.setScreen(MainMenuScreen.INSTANCE);
    }

    private void drawCenteredText(UiTree.Scope scope, String text, UiRect area, Color color, float scale) {
        float textWidth = textRenderer.getWidth(text, scale);
        float textHeight = textRenderer.getHeight(scale);
        scope.text(text, area.x() + (area.width() - textWidth) / 2.0f, area.y() + (area.height() - textHeight) / 2.0f, scale, color);
    }

    private void drawIconGlyph(UiTree.Scope scope, UiRect rect, String glyph, float scale, Color color, float hoverProgress) {
        if (hoverProgress > 0.01f) {
            scope.roundRect(rect.x(), rect.y(), rect.width(), rect.height(), rect.height() / 2.0f, MD3Theme.stateLayer(color, hoverProgress, 36));
        }
        float glyphWidth = textRenderer.getWidth(glyph, scale, StaticFontLoader.ICONS);
        float glyphHeight = textRenderer.getHeight(scale, StaticFontLoader.ICONS);
        scope.text(glyph, rect.x() + (rect.width() - glyphWidth) / 2.0f, rect.y() + (rect.height() - glyphHeight) / 2.0f, scale, color, StaticFontLoader.ICONS);
    }

}
