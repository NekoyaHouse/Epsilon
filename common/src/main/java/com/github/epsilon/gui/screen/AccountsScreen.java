package com.github.epsilon.gui.screen;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.MicrosoftLogin;
import com.github.epsilon.accounts.types.CrackedAccount;
import com.github.epsilon.accounts.types.MicrosoftAccount;
import com.github.epsilon.accounts.types.SessionAccount;
import com.github.epsilon.accounts.types.TheAlteningAccount;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.UiScrollBar;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.holders.AccountHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.mojang.blaze3d.platform.InputConstants;
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

import static com.github.epsilon.Constants.mc;

public class AccountsScreen extends Screen {

    public static final AccountsScreen INSTANCE = new AccountsScreen();

    private static final Color SCRIM = new Color(0, 0, 0, 150);

    private static final float PAD = 12.0f;
    private static final float HEADER_HEIGHT = 48.0f;
    private static final float ADD_BAR_HEIGHT = 34.0f;
    private static final float ROW_HEIGHT = 44.0f;
    private static final float ROW_GAP = 6.0f;
    private static final float AVATAR_SIZE = 28.0f;
    private static final float BACK_BUTTON_SIZE = 32.0f;

    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);
    private final TextRenderer textRenderer = TextRenderer.create();
    private final UiContentBuffer listBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final List<AccountRow> rows = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;

    // Layout（每帧由 layout() 刷新，供渲染与命中测试共用）
    private float panelX, panelY, panelW, panelH;
    private UiRect headerBackBtn;
    private UiRect listViewport;
    private float addBarY;
    private float rowWidth;
    private final UiRect[] addButtons = new UiRect[4];
    private float formTitleY;
    private UiRect formFieldBounds;
    private UiRect formAddBtn;
    private UiRect formCancelBtn;
    private float formButtonY;

    // 列表滚动
    private float scroll;
    private float maxScroll;
    private float scrollVelocity;

    // 添加流程状态
    private AddMode addMode = AddMode.NONE;
    private final AccountField field = new AccountField(512);
    private boolean microsoftWaiting;
    private boolean microsoftError;

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
        for (Account<?> account : AccountHolder.INSTANCE.getAccounts()) {
            account.getCache().loadHead();
        }
    }

    // ---------- 渲染 ----------

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
        listViewport = new UiRect(panelX + PAD, contentTop + 8, panelW - PAD * 2, Math.max(0.0f, addBarY - (contentTop + 8) - 10));

        formTitleY = contentTop + 12;
        formFieldBounds = new UiRect(panelX + PAD, contentTop + 52, panelW - PAD * 2, 40);
        formButtonY = contentBottom - 30;

        syncRows();
        float contentHeight = rows.size() * (ROW_HEIGHT + ROW_GAP);
        maxScroll = Math.max(0, contentHeight - listViewport.height());
        rowWidth = maxScroll > 0 ? listViewport.width() - UiScrollBar.TOTAL_WIDTH : listViewport.width();
    }

    private void syncRows() {
        rows.removeIf(row -> !AccountHolder.INSTANCE.exists(row.account));
        for (Account<?> account : AccountHolder.INSTANCE.getAccounts()) {
            if (rows.stream().noneMatch(row -> row.account == account)) {
                rows.add(new AccountRow(account));
            }
        }
    }

    private void drawHeader(UiTree.Scope scope, int mouseX, int mouseY) {
        boolean backHovered = headerBackBtn.contains(mouseX, mouseY);
        drawIconGlyph(scope, headerBackBtn, IconChars.ARROW_BACK, 0.55f, MD3Theme.TEXT_PRIMARY, backHovered ? 0.9f : 0.0f);

        String title = EpsilonTranslations.Gui.ACCOUNTS_TITLE.getTranslatedName();
        float titleScale = 1.15f;
        float titleHeight = textRenderer.getHeight(titleScale, StaticFontLoader.JURA_LIGHT);
        scope.text(title, panelX + 50, panelY + (HEADER_HEIGHT - titleHeight) / 2.0f, titleScale, MD3Theme.TEXT_PRIMARY, StaticFontLoader.JURA_LIGHT);

        // 右上角显示当前会话用户名
        String currentName = mc.getUser().getName();
        if (!currentName.isBlank()) {
            float scale = 0.6f;
            float labelWidth = textRenderer.getWidth(currentName, scale);
            float labelY = panelY + (HEADER_HEIGHT - textRenderer.getHeight(scale)) / 2.0f;
            scope.text(currentName, panelX + panelW - PAD - labelWidth, labelY, scale, MD3Theme.TEXT_MUTED);
        }
    }

    private void drawAccountList(UiTree.Scope scope, int mouseX, int mouseY) {
        if (rows.isEmpty()) {
            String msg = EpsilonTranslations.Gui.ACCOUNTS_EMPTY.getTranslatedName();
            drawCenteredText(scope, msg, listViewport, MD3Theme.TEXT_MUTED, 0.62f);
            drawAddBar(scope, mouseX, mouseY);
            return;
        }

        float contentHeight = rows.size() * (ROW_HEIGHT + ROW_GAP);
        scope.viewport(listBuffer, listViewport, scroll, maxScroll, contentHeight, mouseX, mouseY, content -> {
            float y = listViewport.y() - scroll;
            for (AccountRow row : rows) {
                row.bounds = new UiRect(listViewport.x(), y, rowWidth, ROW_HEIGHT);
                drawAccountRow(content, row, mouseX, mouseY);
                y += ROW_HEIGHT + ROW_GAP;
            }
        });

        drawAddBar(scope, mouseX, mouseY);
    }

    private void drawAccountRow(UiTree.Scope scope, AccountRow row, int mouseX, int mouseY) {
        UiRect bounds = row.bounds;
        Account<?> account = row.account;
        boolean hovered = bounds.contains(mouseX, mouseY);
        row.hoverProgress = Mth.lerp(hovered ? 0.28f : 0.2f, row.hoverProgress, hovered ? 1.0f : 0.0f);
        boolean current = isCurrent(account);

        scope.pushAbsolute(bounds, s -> {
            s.roundRect(0, 0, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(row.hoverProgress));
            if (current) {
                s.roundRect(0, 0, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.stateLayer(MD3Theme.PRIMARY, 0.18f, 40));
                s.roundRect(0, 3, 3, bounds.height() - 6, 1.5f, MD3Theme.PRIMARY);
            }

            LuminTexture head = account.getCache().getHeadTexture();
            s.roundedTexture(head, 10, (bounds.height() - AVATAR_SIZE) / 2.0f, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE * 0.3f, 0.0f, 0.0f, 1.0f, 1.0f, Color.WHITE);

            // 用户名
            String username = account.getUsername();
            float nameScale = 0.78f;
            float nameHeight = textRenderer.getHeight(nameScale);
            float nameX = 10 + AVATAR_SIZE + 10;
            s.text(username, nameX, (bounds.height() - nameHeight) / 2.0f, nameScale, current ? MD3Theme.PRIMARY : MD3Theme.TEXT_PRIMARY);

            // 尾部控件：类型 chip + 登录按钮 + 删除按钮
            float deleteSize = 26.0f;
            float gap = 4.0f;
            float loginW = 58.0f;
            float loginH = 26.0f;
            float rightInset = 6.0f;
            float deleteX = bounds.width() - rightInset - deleteSize;
            float loginX = deleteX - gap - loginW;
            float loginY = (bounds.height() - loginH) / 2.0f;
            row.loginBounds = new UiRect(bounds.x() + loginX, bounds.y() + loginY, loginW, loginH);
            row.deleteBounds = new UiRect(bounds.x() + deleteX, bounds.y() + (bounds.height() - deleteSize) / 2.0f, deleteSize, deleteSize);

            String typeName = current ? EpsilonTranslations.Gui.ACCOUNTS_CURRENT.getTranslatedName() : account.getType().name();
            float chipScale = 0.5f;
            float chipTextWidth = textRenderer.getWidth(typeName, chipScale);
            float chipWidth = chipTextWidth + 14.0f;
            float chipHeight = 20.0f;
            float chipX = loginX - gap - 8.0f - chipWidth;
            float chipY = (bounds.height() - chipHeight) / 2.0f;
            Color chipBg = current ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SECONDARY_CONTAINER;
            Color chipFg = current ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.ON_SECONDARY_CONTAINER;
            s.roundRect(chipX, chipY, chipWidth, chipHeight, MD3Theme.CHIP_RADIUS, chipBg);
            s.text(typeName, chipX + (chipWidth - chipTextWidth) / 2.0f, chipY + (chipHeight - textRenderer.getHeight(chipScale)) / 2.0f, chipScale, chipFg);

            String loginLabel = EpsilonTranslations.Gui.ACCOUNTS_LOGIN.getTranslatedName();
            s.button(loginX, loginY, loginW, loginH, 8.0f, MD3Theme.PRIMARY, loginLabel, 0.6f, MD3Theme.ON_PRIMARY);

            UiRect deleteLocal = row.deleteBounds.relativeTo(bounds);
            drawDeleteButton(s, deleteLocal, row, mouseX, mouseY);
        });
    }

    private void drawDeleteButton(UiTree.Scope scope, UiRect local, AccountRow row, int mouseX, int mouseY) {
        boolean hovered = row.deleteBounds.contains(mouseX, mouseY);
        float scale = 0.52f;
        float glyphWidth = textRenderer.getWidth(IconChars.DELETE, scale, StaticFontLoader.ICONS);
        float glyphHeight = textRenderer.getHeight(scale, StaticFontLoader.ICONS);
        float glyphX = local.x() + (local.width() - glyphWidth) / 2.0f;
        float glyphY = local.y() + (local.height() - glyphHeight) / 2.0f;

        if (row.confirmingDelete) {
            scope.roundRect(local.x(), local.y(), local.width(), local.height(), local.height() / 2.0f, MD3Theme.ERROR);
            scope.text(IconChars.DELETE, glyphX, glyphY, scale, new Color(255, 255, 255), StaticFontLoader.ICONS);
        } else {
            if (hovered) {
                scope.roundRect(local.x(), local.y(), local.width(), local.height(), local.height() / 2.0f, MD3Theme.stateLayer(MD3Theme.ERROR, 0.9f, 28));
            }
            scope.text(IconChars.DELETE, glyphX, glyphY, scale, MD3Theme.ERROR, StaticFontLoader.ICONS);
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
            scope.button(x, addBarY, buttonWidth, buttonHeight, MD3Theme.CONTROL_RADIUS, MD3Theme.SECONDARY_CONTAINER, labels[i], 0.6f, MD3Theme.ON_SECONDARY_CONTAINER);
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
        float titleScale = 0.95f;
        scope.text(formTitle, panelX + PAD, formTitleY, titleScale, MD3Theme.TEXT_PRIMARY);

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

    // ---------- 输入 ----------

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

            // 只在可视 viewport 内命中行，避免滚动出去的行的矩形误触发
            if (listViewport != null && listViewport.contains(mx, my)) {
                for (AccountRow row : rows) {
                    if (row.deleteBounds != null && row.deleteBounds.contains(mx, my)) {
                        if (row.confirmingDelete) {
                            row.confirmingDelete = false;
                            AccountHolder.INSTANCE.remove(row.account);
                        } else {
                            row.confirmingDelete = true;
                        }
                        return true;
                    }
                    if (row.loginBounds != null && row.loginBounds.contains(mx, my)) {
                        login(row.account);
                        return true;
                    }
                    if (row.bounds != null && row.bounds.contains(mx, my)) {
                        login(row.account);
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

            for (AccountRow row : rows) row.confirmingDelete = false;
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
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                submitAdd();
                return true;
            }
            if (addMode != AddMode.NONE && field.keyPressed(event)) {
                return true;
            }
            return true;
        }

        if (event.isEscape()) {
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
        return super.charTyped(event);
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
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
    }

    private void login(Account<?> account) {
        Managers.EXECUTOR.execute(() -> {
            if (account.fetchInfo() && account.login()) {
                if (account.getType() != AccountType.Cracked) account.getCache().loadHead();
            }
        });
    }

    private void startAdd(AddMode mode) {
        addMode = mode;
        field.clear();
        field.setMaxLength(mode == AddMode.CRACKED ? 16 : 512);
        field.focus();
    }

    private void startAddMicrosoft() {
        microsoftWaiting = true;
        microsoftError = false;
        MicrosoftLogin.getRefreshToken(token -> mc.execute(() -> {
            if (token == null) {
                microsoftError = true;
                microsoftWaiting = false;
            } else {
                microsoftWaiting = false;
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
        if (AccountHolder.INSTANCE.exists(account)) return;

        Managers.EXECUTOR.execute(() -> {
            if (!account.fetchInfo()) return;

            // 与 Meteor 一致：fetchInfo/login 在后台线程执行，账号列表使用 CopyOnWriteArrayList 保证并发安全
            AccountHolder.INSTANCE.add(account);
            if (account.login()) {
                if (account.getType() != AccountType.Cracked) account.getCache().loadHead();
            }
        });
    }

    private void exitAddMode() {
        if (microsoftWaiting) MicrosoftLogin.stopServer();
        addMode = AddMode.NONE;
        microsoftWaiting = false;
        microsoftError = false;
        field.blur();
        field.clear();
        preeditOverlay = null;
    }

    private boolean isCurrent(Account<?> account) {
        return mc.getUser().getName().equalsIgnoreCase(account.getUsername());
    }

    private void close() {
        minecraft.gui.setScreen(MainMenuScreen.INSTANCE);
    }

    // ---------- 绘制工具 ----------

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

    // ---------- 账号行 ----------

    private static final class AccountRow {
        private final Account<?> account;
        private UiRect bounds;
        private UiRect loginBounds;
        private UiRect deleteBounds;
        private float hoverProgress;
        private boolean confirmingDelete;

        private AccountRow(Account<?> account) {
            this.account = account;
        }
    }

    /**
     * MD3 Filled Text Field 的轻量实现，参考 {@code ClientSettingTextField}。
     */
    private static final class AccountField {
        private static final long ANIM_DURATION = 120L;

        private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, ANIM_DURATION);
        private final Animation focusAnimation = new Animation(Easing.EASE_OUT_CUBIC, ANIM_DURATION);

        private int maxLength;
        private boolean focused;
        private String text = "";
        private int cursor;

        private AccountField(int maxLength) {
            this.maxLength = maxLength;
            hoverAnimation.setStartValue(0.0f);
            focusAnimation.setStartValue(0.0f);
        }

        public void buildUi(UiTree.Scope scope, UiRect bounds, int mouseX, int mouseY,
                            TextRenderer textRenderer, String placeholder, float textScale) {
            boolean hovered = bounds.contains(mouseX, mouseY);
            float hoverProgress = scope.animate(hoverAnimation, hovered);
            float focusProgress = scope.animate(focusAnimation, focused);
            float textInset = 10.0f;
            float textHeight = textRenderer.getHeight(textScale);
            float textX = bounds.x() + textInset;
            float textY = bounds.y() + (bounds.height() - textHeight) / 2.0f;

            boolean showPlaceholder = text.isEmpty() && !focused;
            String display = showPlaceholder ? placeholder : text;
            Color textColor = showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY;
            scope.input(bounds, focused, hoverProgress,
                    focusProgress, MD3Theme.PRIMARY, 1.0f,
                    textInset, display, textScale, textColor,
                    null, null,
                    focused ? Math.min(cursor, text.length()) : null, focused ? MD3Theme.TEXT_PRIMARY : null,
                    null, 0.0f, null);

            if (focused) {
                int safeCursor = Math.min(cursor, text.length());
                float caretX = textX + textRenderer.getWidth(text.substring(0, safeCursor), textScale);
                IMEFocusHelper.updateCursorPos(caretX, textY);
            }
        }

        public boolean focusIfContains(UiRect bounds, double mouseX, double mouseY) {
            if (!bounds.contains(mouseX, mouseY)) {
                return false;
            }
            focus();
            return true;
        }

        public void focus() {
            focused = true;
            cursor = text.length();
            IMEFocusHelper.activate();
        }

        public void blur() {
            if (focused) {
                focused = false;
                IMEFocusHelper.deactivate();
            }
        }

        public boolean keyPressed(KeyEvent event) {
            if (!focused) {
                return false;
            }
            if (isControlDown()) {
                return handleControlShortcut(event.key());
            }
            return switch (event.key()) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0 && !text.isEmpty()) {
                        text = text.substring(0, cursor - 1) + text.substring(cursor);
                        cursor--;
                    }
                    yield true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (cursor < text.length()) {
                        text = text.substring(0, cursor) + text.substring(cursor + 1);
                    }
                    yield true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    cursor = Math.max(0, cursor - 1);
                    yield true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    cursor = Math.min(text.length(), cursor + 1);
                    yield true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    cursor = 0;
                    yield true;
                }
                case GLFW.GLFW_KEY_END -> {
                    cursor = text.length();
                    yield true;
                }
                default -> false;
            };
        }

        public boolean charTyped(CharacterEvent event) {
            if (!focused) {
                return false;
            }
            String typed = event.codepointAsString();
            if (typed.isEmpty()) {
                return true;
            }
            insertText(typed);
            return true;
        }

        public boolean hasActiveAnimations() {
            return !hoverAnimation.isFinished() || !focusAnimation.isFinished();
        }

        public boolean isFocused() {
            return focused;
        }

        public String getText() {
            return text;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
            if (text.length() > maxLength) {
                text = text.substring(0, maxLength);
            }
            cursor = Math.min(cursor, text.length());
        }

        public void clear() {
            text = "";
            cursor = 0;
        }

        private boolean handleControlShortcut(int key) {
            return switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    cursor = text.length();
                    yield true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clipboard = mc.keyboardHandler.getClipboard();
                    if (!clipboard.isEmpty()) {
                        String sanitized = clipboard.codePoints()
                                .filter(cp -> cp >= 32 && cp != 127)
                                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                .toString();
                        if (!sanitized.isEmpty()) {
                            insertText(sanitized);
                        }
                    }
                    yield true;
                }
                default -> false;
            };
        }

        private void insertText(String inserted) {
            if (inserted == null || inserted.isEmpty()) {
                return;
            }
            int available = maxLength - text.length();
            if (available <= 0) {
                return;
            }
            String safeInsert = inserted.length() > available ? inserted.substring(0, available) : inserted;
            text = text.substring(0, cursor) + safeInsert + text.substring(cursor);
            cursor += safeInsert.length();
        }

        private boolean isControlDown() {
            var window = mc.getWindow();
            return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        }
    }

}
