package com.github.epsilon.gui.dropdown;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dropdown.component.*;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup;
import com.github.epsilon.gui.panel.popup.StringListSelectPopup;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.settings.impl.StringListSetting;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DropdownScreen extends Screen {

    public static final DropdownScreen INSTANCE = new DropdownScreen();

    private final List<DropdownPanel> panels = new ArrayList<>();
    private final TextRenderer textMetrics = TextRenderer.create();
    private final UiTextMetrics uiTextMetrics = new DropdownTextMetrics();
    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final Animation scrimAnim = new Animation(Easing.EASE_OUT_SINE, 200L);
    private final DropdownTextField searchField = new DropdownTextField(64);
    private final ReisaDropdownCompanion reisaCompanion = new ReisaDropdownCompanion();
    private final Set<String> visiblePanelIds = new HashSet<>();

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;
    private boolean initialized;
    private int sessionId;
    private int renderFrameId;
    private UiRenderBatch dropdownBatch;
    private UiTree.Scope dropdownScope;
    private int dropdownLayer;

    private DropdownScreen() {
        super(Component.literal("DropdownGui"));
    }

    @Override
    protected void init() {
        super.init();
        sessionId++;
        if (isReisaCompanionEnabled()) {
            reisaCompanion.open(sessionId);
        }
        scrimAnim.setStartValue(0.0f);
        scrimAnim.run(0.0f);
        scrimAnim.run(1.0f);

        if (!initialized) {
            buildPanels();
            initialized = true;
        }

        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel));
            panel.startIntro();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("dropdown-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        LuminRenderSystem.setActiveTarget(renderTarget);
        scene.beginFrame();

        int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        drawGui(graphics, epsilonMouseX, epsilonMouseY, partialTick);
        scene.clear();

        LuminRenderSystem.setActiveTarget(null);
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        popupHost.extractOverlay(graphics, epsilonMouseX, epsilonMouseY, partialTick);
    }

    private void drawGui(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        scrimAnim.run(1.0f);
        dropdownBatch = scene.batch(UiLayer.CONTENT);
        dropdownLayer = -10;
        popupHost.setOverlayBounds(new UiRect(0.0f, 0.0f, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight()));
        updatePanelHeightLimits();
        updateVisiblePanelIds();
        beginPanelFrames();

        beginDropdownLayer();
        Color scrim = DropdownTheme.scrim();
        float scrimAlpha = scrimAnim.getValue();
        dropdownScope.rect(0, 0, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight(), new Color(scrim.getRed(), scrim.getGreen(), scrim.getBlue(), (int) (scrim.getAlpha() * scrimAlpha)));
        flushDropdownLayer();

        float shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f;
        boolean popupHovered = popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
        int backgroundMouseX = popupHovered ? Integer.MIN_VALUE : mouseX;
        int backgroundMouseY = popupHovered ? Integer.MIN_VALUE : mouseY;

        DropdownPanel topmostHovered = null;
        if (!popupHovered) {
            for (int i = panels.size() - 1; i >= 0; i--) {
                DropdownPanel p = panels.get(i);
                if (!p.isVisible()) continue;
                float ph = p.getPanelHeight();
                if (mouseX >= p.getX() && mouseX <= p.getX() + p.getWidth()
                        && mouseY >= p.getY() && mouseY <= p.getY() + ph) {
                    topmostHovered = p;
                    break;
                }
            }
        }

        if (isReisaCompanionEnabled()) {
            reisaCompanion.open(sessionId);
            beginDropdownLayer();
            reisaCompanion.draw(
                    dropdownScope,
                    LuminRenderSystem.getScaledWidth(),
                    LuminRenderSystem.getScaledHeight(),
                    mouseX,
                    mouseY,
                    popupHovered || topmostHovered != null
            );
            flushDropdownLayer();
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            float intro = panel.getIntroValue();
            if (intro < 0.001f) continue;

            float slideOffset = (1.0f - intro) * 10.0f;
            float origY = panel.getY();
            panel.setPosition(panel.getX(), origY - slideOffset);

            float panelH = panel.getPanelHeight();
            float revealedH = panelH * intro;

            beginDropdownLayer();
            withDropdownScissor(intro < 1.0f,
                    panel.getX() - shadowPad,
                    panel.getY() - shadowPad,
                    panel.getWidth() + shadowPad * 2,
                    revealedH + shadowPad * 2,
                    scope -> panel.drawBackground(scope, uiTextMetrics));
            flushDropdownLayer();

            float clipY = panel.getContentClipY();
            float clipH = panel.getContentClipHeight();
            float revealedBottom = panel.getY() + revealedH;
            float actualClipH = Math.min(clipH, revealedBottom - clipY);
            if (actualClipH > 0.5f) {
                beginDropdownLayer();
                int hoverMouseX = panel == topmostHovered ? backgroundMouseX : -1;
                int hoverMouseY = panel == topmostHovered ? backgroundMouseY : -1;
                boolean requiresContentScissor = intro < 1.0f || panel.requiresContentScissor();
                withDropdownScissor(requiresContentScissor, panel.getX(), clipY, panel.getWidth(), actualClipH,
                        scope -> panel.drawContent(scope, uiTextMetrics, hoverMouseX, hoverMouseY));
                flushDropdownLayer();
            }

            panel.setPosition(panel.getX(), origY);
        }

        drawSearch(backgroundMouseX, backgroundMouseY);
        popupHost.render(graphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick);
        scene.flush();
        popupHost.flush();
    }

    private void drawSearch(int mouseX, int mouseY) {
        beginDropdownLayer();
        float searchX = getSearchX();
        float searchY = getSearchY();
        searchField.draw(dropdownScope, uiTextMetrics, searchX, searchY, getSearchWidth(), getSearchHeight(), mouseX, mouseY, EpsilonTranslations.Gui.SEARCH.getTranslatedName(), 0.58f);
        drawHints();
        flushDropdownLayer();
    }

    private void drawHints() {
        if (ClientSetting.INSTANCE.dropdownHints.getValue()) {
            float scale = 0.62f;
            float lineGap = 5.0f;
            float lineHeight = uiTextMetrics.textHeight(scale);
            String[] hints = {
                    EpsilonTranslations.Gui.DROPDOWN_HINT_SEARCH.getTranslatedName(),
                    EpsilonTranslations.Gui.DROPDOWN_HINT_PANELS.getTranslatedName(),
                    EpsilonTranslations.Gui.DROPDOWN_HINT_DRAG.getTranslatedName()
            };
            float screenWidth = LuminRenderSystem.getScaledWidth();
            float xRight = screenWidth - DropdownTheme.PANEL_MARGIN_X;
            if (isReisaCompanionEnabled()) {
                float companionLeft = reisaCompanion.getLeftEdge(screenWidth, LuminRenderSystem.getScaledHeight());
                xRight = Math.min(xRight, companionLeft - 8.0f);
            }
            xRight = Math.max(getSearchX() + getSearchWidth(), xRight);
            float y = LuminRenderSystem.getScaledHeight() - DropdownTheme.PANEL_MARGIN_Y - hints.length * lineHeight - (hints.length - 1) * lineGap;
            int alpha = (int) (255 * scrimAnim.getValue());
            if (alpha <= 0) {
                return;
            }
            Color color = MD3Theme.withAlpha(Color.WHITE, alpha);
            for (String hint : hints) {
                float x = xRight - uiTextMetrics.textWidth(hint, scale);
                dropdownScope.text(hint, x, y, scale, color);
                y += lineHeight + lineGap;
            }
        }
    }


    private void beginDropdownLayer() {
        dropdownLayer += 10;
        dropdownScope = new UiTree.Scope();
    }

    private void flushDropdownLayer() {
        dropdownBatch.render(UiTree.from(dropdownScope), dropdownLayer);
    }

    private void withDropdownScissor(boolean required, float guiX, float guiY, float guiW, float guiH,
                                     Consumer<UiTree.Scope> content) {
        dropdownScope.scissorIf(required, new UiRect(guiX, guiY, guiW, guiH), content);
    }

    private final class DropdownTextMetrics implements UiTextMetrics {
        @Override
        public float textWidth(String text, float scale) {
            return textMetrics.getWidth(text, scale);
        }

        @Override
        public float textWidth(String text, float scale, TtfFontLoader fontLoader) {
            return textMetrics.getWidth(text, scale, fontLoader);
        }

        @Override
        public float textHeight(float scale) {
            return textMetrics.getHeight(scale);
        }

        @Override
        public float textHeight(float scale, TtfFontLoader fontLoader) {
            return textMetrics.getHeight(scale, fontLoader);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double mx = epsilonEvent.x();
        double my = epsilonEvent.y();
        int button = epsilonEvent.button();

        if (popupHost.mouseClicked(epsilonEvent, isDoubleClick)) {
            react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }

        if (button == 0 && searchField.focusIfContains(mx, my, getSearchX(), getSearchY(), getSearchWidth(), getSearchHeight())) {
            react(ReisaDropdownCompanion.Action.TYPING);
            return true;
        } else if (button == 0 && searchField.isFocused()) {
            searchField.blur();
        }

        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            long reactionRevision = reisaCompanion.getReactionRevision();
            if (panel.mouseClicked(mx, my, button)) {
                if (i < panels.size() - 1) {
                    panels.remove(i);
                    panels.add(panel);
                }
                DropdownLayoutState.save(panels);
                if (reactionRevision == reisaCompanion.getReactionRevision()) {
                    reactMouseButton(button);
                }
                return true;
            }
        }
        return super.mouseClicked(epsilonEvent, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double mx = epsilonEvent.x();
        double my = epsilonEvent.y();
        int button = epsilonEvent.button();

        if (popupHost.mouseReleased(epsilonEvent)) {
            react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.mouseReleased(mx, my, button)) {
                DropdownLayoutState.save(panels);
                react(ReisaDropdownCompanion.Action.CONFIRM);
                return true;
            }
        }
        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)) {
            react(ReisaDropdownCompanion.Action.DRAG);
            return true;
        }
        boolean handled = false;
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.mouseDragged(LuminRenderSystem.toEpsilonMouseX(event.x()), LuminRenderSystem.toEpsilonMouseY(event.y()))) {
                handled = true;
            }
        }
        if (handled) {
            DropdownLayoutState.save(panels);
            react(ReisaDropdownCompanion.Action.DRAG);
            return true;
        }
        return super.mouseDragged(epsilonEvent, LuminRenderSystem.toEpsilonMouseX(event.x()), LuminRenderSystem.toEpsilonMouseY(event.y()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            reactScroll(scrollY);
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollY)) {
                reactScroll(scrollY);
                return true;
            }
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popupHost.keyPressed(event)) {
            react(event.isEscape() ? ReisaDropdownCompanion.Action.CANCEL : ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F && InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
            searchField.focus();
            react(ReisaDropdownCompanion.Action.TYPING);
            return true;
        }
        if (searchField.isFocused()) {
            if (event.isEscape()) {
                searchField.blur();
                react(ReisaDropdownCompanion.Action.CANCEL);
                return true;
            }
            if (searchField.keyPressed(event)) {
                syncSearchQuery();
                react(ReisaDropdownCompanion.Action.TYPING);
                return true;
            }
        }

        boolean hasActiveInput = panels.stream().filter(DropdownPanel::isVisible).anyMatch(DropdownPanel::hasActiveInput);

        if (hasActiveInput) {
            for (DropdownPanel panel : panels) {
                if (!panel.isVisible()) continue;
                if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                    react(event.isEscape() ? ReisaDropdownCompanion.Action.CANCEL : ReisaDropdownCompanion.Action.CONFIRM);
                    return true;
                }
            }
        }

        if (event.isEscape()) {
            onClose();
            return true;
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                react(event.isEscape() ? ReisaDropdownCompanion.Action.CANCEL : ReisaDropdownCompanion.Action.CONFIRM);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popupHost.charTyped(event)) {
            react(ReisaDropdownCompanion.Action.TYPING);
            return true;
        }
        if (searchField.charTyped(event)) {
            syncSearchQuery();
            react(ReisaDropdownCompanion.Action.TYPING);
            return true;
        }
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && panel.charTyped(typed)) {
                react(ReisaDropdownCompanion.Action.TYPING);
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        IMEFocusHelper.forceDeactivate();
        DropdownLayoutState.save(panels);
        super.onClose();
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        popupHost.close();
        searchField.blur();
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buildPanels() {
        panels.clear();
        int index = 0;
        MainDropdownPanel mainPanel = new MainDropdownPanel(index++, this::handleMainPanelAction, this::anySubPanelVisible, this::isPanelVisible);
        mainPanel.setPosition(DropdownTheme.PANEL_MARGIN_X, DropdownTheme.PANEL_MARGIN_Y);
        panels.add(mainPanel);

        float x = DropdownTheme.PANEL_MARGIN_X + mainPanel.getWidth() + DropdownTheme.PANEL_GAP;
        float y = DropdownTheme.PANEL_MARGIN_Y;
        for (Category category : Category.values()) {
            panels.add(createSubPanel(new CategoryPanel(category, index++), x, y));
            y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        }

        panels.add(createSubPanel(new FriendDropdownPanel(index++), x, y));
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        panels.add(createSubPanel(new ConfigDropdownPanel(index++), x, y));
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        panels.add(createSubPanel(new AddonDropdownPanel(index), x, y));

        DropdownLayoutState.load(panels);
    }

    private DropdownPanel createSubPanel(DropdownPanel panel, float x, float y) {
        panel.setPosition(x, y);
        panel.setVisible(false);
        panel.setOpened(false);
        return panel;
    }

    private void handleMainPanelAction(String panelId) {
        if ("__collapse_all__".equals(panelId)) {
            for (DropdownPanel panel : panels) {
                if (!"main".equals(panel.getId())) {
                    panel.setVisible(false);
                    panel.setOpened(false);
                }
            }
            DropdownLayoutState.save(panels);
            react(ReisaDropdownCompanion.Action.PANEL_CLOSE);
            return;
        }

        for (DropdownPanel panel : panels) {
            if (panel.getId().equals(panelId)) {
                panel.setVisible(!panel.isVisible());
                panel.setOpened(false);
                DropdownLayoutState.save(panels);
                react(panel.isVisible()
                        ? ReisaDropdownCompanion.Action.PANEL_OPEN
                        : ReisaDropdownCompanion.Action.PANEL_CLOSE);
                return;
            }
        }
    }

    private boolean anySubPanelVisible() {
        return panels.stream().anyMatch(panel -> !"main".equals(panel.getId()) && panel.isVisible());
    }

    private boolean isPanelVisible(String panelId) {
        return visiblePanelIds.contains(panelId);
    }

    private void updateVisiblePanelIds() {
        visiblePanelIds.clear();
        for (DropdownPanel panel : panels) {
            if (panel.isVisible()) {
                visiblePanelIds.add(panel.getId());
            }
        }
    }

    private float resolveMaxPanelHeight(DropdownPanel panel) {
        return resolveMaxPanelHeight(panel, LuminRenderSystem.getScaledHeight() * 0.72f);
    }

    private float resolveMaxPanelHeight(DropdownPanel panel, float screenLimited) {
        return switch (panel.getId()) {
            case "main", "addon" -> Math.min(screenLimited, 260.0f);
            case "friend", "config" -> Math.min(screenLimited, 220.0f);
            default -> Math.min(screenLimited, 350.0f);
        };
    }

    private void updatePanelHeightLimits() {
        float screenLimited = LuminRenderSystem.getScaledHeight() * 0.72f;
        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel, screenLimited));
        }
    }

    private void beginPanelFrames() {
        int frameId = ++renderFrameId;
        for (DropdownPanel panel : panels) {
            panel.beginRenderFrame(frameId);
        }
    }

    private void syncSearchQuery() {
        String query = searchField.getText();
        for (DropdownPanel panel : panels) {
            if (panel instanceof CategoryPanel categoryPanel) {
                categoryPanel.setSearchQuery(query);
            }
        }
    }

    private float getSearchX() {
        return DropdownTheme.PANEL_MARGIN_X;
    }

    private float getSearchY() {
        return LuminRenderSystem.getScaledHeight() - DropdownTheme.PANEL_MARGIN_Y - getSearchHeight();
    }

    private float getSearchWidth() {
        return Mth.clamp(LuminRenderSystem.getScaledWidth() - DropdownTheme.PANEL_MARGIN_X * 2.0f, 140.0f, 200.0f);
    }

    private float getSearchHeight() {
        return 20.0f;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void react(ReisaDropdownCompanion.Action action) {
        if (isReisaCompanionEnabled()) {
            reisaCompanion.open(sessionId);
            reisaCompanion.react(action);
        }
    }

    private boolean isReisaCompanionEnabled() {
        return ClientSetting.INSTANCE.showReisaInDropdown.getValue();
    }

    public void openRegistryListSettingPopup(RegistryListSetting<?> setting) {
        UiRect bounds = popupHost.getCenteredBounds(
                Math.min(360.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
                Math.min(300.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        );
        popupHost.open(RegistryListSelectPopup.create(bounds, setting));
        react(ReisaDropdownCompanion.Action.PANEL_OPEN);
    }

    public void openStringListSettingPopup(StringListSetting setting) {
        UiRect bounds = popupHost.getCenteredBounds(
                Math.min(300.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
                Math.min(260.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        );
        popupHost.open(new StringListSelectPopup(bounds, setting, setting::add, setting::remove));
        react(ReisaDropdownCompanion.Action.PANEL_OPEN);
    }

    private void reactMouseButton(int button) {
        react(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                ? ReisaDropdownCompanion.Action.SECONDARY_CLICK
                : ReisaDropdownCompanion.Action.PRIMARY_CLICK);
    }

    private void reactScroll(double scrollY) {
        react(scrollY >= 0.0
                ? ReisaDropdownCompanion.Action.SCROLL_UP
                : ReisaDropdownCompanion.Action.SCROLL_DOWN);
    }

}
