package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen;
import com.github.epsilon.gui.dropdown.component.CategoryPanel;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup;
import com.github.epsilon.gui.panel.popup.StringListSelectPopup;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.HudElementManager;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.settings.impl.StringListSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class HudEditorScreen extends Screen implements ListSettingPopupScreen {

    public static final HudEditorScreen INSTANCE = new HudEditorScreen();

    private static final float SNAP_DISTANCE = 6.0f;
    private static final float ELEMENT_PADDING = 3.0f;
    private static final float LABEL_HEIGHT = 13.0f;
    private static final float GUIDE_ALPHA = 95.0f;

    private CategoryPanel hudPanel;
    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;
    private int renderFrameId;
    private int panelElementCount = -1;
    private HudModule selectedElement;
    private HudModule draggingElement;
    private float dragOffsetX;
    private float dragOffsetY;
    private SnapInfo currentSnap = SnapInfo.none();

    private final TextRenderer textMetrics = TextRenderer.create();
    private final UiTextMetrics uiTextMetrics = new EditorTextMetrics();
    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private UiRenderBatch editorBatch;
    private UiTree.Scope editorScope;
    private int editorLayer;
    private boolean shouldRenderElements;

    private HudEditorScreen() {
        super(Component.literal("HudEditor"));
    }

    @Override
    protected void init() {
        NotificationManager.INSTANCE.clearAll();
        ensureHudPanel();
        hudPanel.setVisible(true);
        hudPanel.setOpened(true);
        hudPanel.setMaxPanelHeight(resolveMaxPanelHeight());
        hudPanel.startIntro();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        LuminRenderSystem.LuminRenderTarget renderTarget = getRenderTarget(window.getWidth(), window.getHeight());
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();

        LuminRenderSystem.setActiveTarget(renderTarget);
        scene.beginFrame();

        int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);

        drawEditor(graphics, epsilonMouseX, epsilonMouseY, a);

        shouldRenderElements = true;

        LuminRenderSystem.setActiveTarget(null);
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                renderTarget.getIdentifier(),
                0,
                0,
                0.0f,
                window.getHeight(),
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight(),
                window.getWidth(),
                -window.getHeight(),
                window.getWidth(),
                window.getHeight()
        );
        if (popupHost.getActivePopup() == null) {
            drawElementOverlays(graphics);
        }
        popupHost.extractOverlay(graphics, epsilonMouseX, epsilonMouseY, a);
    }

    private void drawEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ensureHudPanel();

        for (HudModule element : HudElementManager.INSTANCE.getElements()) {
            element.updateLayout();
        }

        validateSelection();

        editorBatch = scene.batch(UiLayer.CONTENT);
        editorLayer = -120;

        float screenW = LuminRenderSystem.getScaledWidth();
        float screenH = LuminRenderSystem.getScaledHeight();
        popupHost.setOverlayBounds(new UiRect(0.0f, 0.0f, screenW, screenH));

        beginEditorLayer(10);
        editorScope.rect(0.0f, 0.0f, screenW, screenH, MD3Theme.withAlpha(MD3Theme.SURFACE_DIM, 72));
        flushEditorLayer();

        float centerX = screenW / 2.0f;
        float centerY = screenH / 2.0f;
        Color centerGuide = MD3Theme.withAlpha(MD3Theme.OUTLINE, 52);

        beginEditorLayer(10);
        editorScope.rect(centerX - 0.5f, 0.0f, 1.0f, screenH, centerGuide);
        editorScope.rect(0.0f, centerY - 0.5f, screenW, 1.0f, centerGuide);
        drawSnapGuides(editorScope, screenW, screenH);
        flushEditorLayer();

        boolean popupActive = popupHost.getActivePopup() != null;
        int backgroundMouseX = popupActive ? Integer.MIN_VALUE : mouseX;
        int backgroundMouseY = popupActive ? Integer.MIN_VALUE : mouseY;

        beginEditorLayer(100);
        List<HudModule> elements = HudElementManager.INSTANCE.getElements();
        HudModule hovered = findElementAt(backgroundMouseX, backgroundMouseY, true);
        for (HudModule element : elements) {
            if (!element.isEnabled()) continue;
            boolean selected = element == selectedElement;
            boolean hover = element == hovered;
            if (!selected && !hover) continue;
            drawElementFrame(editorScope, uiTextMetrics, element, selected, hover);
        }
        flushEditorLayer();

        drawCanvasChrome();

        drawPanel(backgroundMouseX, backgroundMouseY);
        popupHost.render(graphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick);
    }

    /**
     * 在世界画面提交后执行 HUD 预览，确保背景模糊能读取到主渲染目标的有效内容。
     */
    public void renderPendingHudElements() {
        if (!shouldRenderElements || minecraft.gui.screen() != this || renderTarget == null) {
            return;
        }

        LuminRenderSystem.LuminRenderTarget previousTarget = LuminRenderSystem.getActiveTarget();
        LuminRenderSystem.setActiveTarget(renderTarget);

        for (HudModule element : HudElementManager.INSTANCE.getElements()) {
            if (element.isEnabled()) {
                element.renderWithBatch(minecraft.getDeltaTracker(), scene.batch(UiLayer.CONTENT, -40));
            }
        }
        scene.flush();
        popupHost.flush();
        scene.clear();

        shouldRenderElements = false;
        LuminRenderSystem.setActiveTarget(previousTarget);
    }

    private LuminRenderSystem.LuminRenderTarget getRenderTarget(int width, int height) {
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("hud-editor-gui", width, height);
        }
        return renderTarget;
    }

    private void drawElementOverlays(GuiGraphicsExtractor graphics) {
        float overlayScale = (float) (LuminRenderSystem.getGuiScale() / minecraft.getWindow().getGuiScale());
        graphics.pose().pushMatrix();
        graphics.pose().scale(overlayScale, overlayScale);
        for (HudModule element : HudElementManager.INSTANCE.getElements()) {
            if (!element.isEnabled()) continue;
            element.renderOverlay(graphics, minecraft.getDeltaTracker());
        }
        graphics.pose().popMatrix();
    }

    private void drawPanel(int mouseX, int mouseY) {
        hudPanel.setMaxPanelHeight(resolveMaxPanelHeight());
        hudPanel.beginRenderFrame(++renderFrameId);

        float shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f;
        float intro = hudPanel.getIntroValue();
        if (intro > 0.001f) {
            float slideOffset = (1.0f - intro) * 10.0f;
            float origY = hudPanel.getY();
            hudPanel.setPosition(hudPanel.getX(), origY - slideOffset);

            float panelH = hudPanel.getPanelHeight();
            float revealedH = panelH * intro;

            beginEditorLayer(10);
            withEditorScissor(intro < 1.0f,
                    hudPanel.getX() - shadowPad,
                    hudPanel.getY() - shadowPad,
                    hudPanel.getWidth() + shadowPad * 2.0f,
                    revealedH + shadowPad * 2.0f,
                    scope -> hudPanel.drawBackground(scope, uiTextMetrics)
            );
            flushEditorLayer();

            float clipY = hudPanel.getContentClipY();
            float clipH = hudPanel.getContentClipHeight();
            float revealedBottom = hudPanel.getY() + revealedH;
            float actualClipH = Math.min(clipH, revealedBottom - clipY);
            if (actualClipH > 0.5f) {
                beginEditorLayer(10);
                boolean requiresContentScissor = intro < 1.0f || hudPanel.requiresContentScissor();
                withEditorScissor(requiresContentScissor, hudPanel.getX(), clipY, hudPanel.getWidth(), actualClipH,
                        scope -> hudPanel.drawContent(scope, uiTextMetrics, mouseX, mouseY));
                flushEditorLayer();
            }

            hudPanel.setPosition(hudPanel.getX(), origY);
        }
    }

    private void drawCanvasChrome() {
        String title = "HUD Editor";
        String subtitle = selectedElement == null ? "Select and drag an element" : selectedElement.getTranslatedName();
        float titleScale = 0.64f;
        float subtitleScale = 0.56f;
        float titleW = textMetrics.getWidth(title, titleScale);
        float subW = textMetrics.getWidth(subtitle, subtitleScale);
        float titleH = textMetrics.getHeight(titleScale);
        float subtitleH = textMetrics.getHeight(subtitleScale);
        float boxW = Math.max(titleW, subW) + 24.0f;
        float boxH = 32.0f;
        float radius = 8.0f;
        float middlePadding = 3.0f;
        float labelX = (LuminRenderSystem.getScaledWidth() - boxW) * 0.5f;
        float labelY = DropdownTheme.PANEL_MARGIN_Y + 2.0f;
        float titleY = labelY + (boxH - titleH - middlePadding - subtitleH) * 0.5f;
        float subtitleY = titleY + titleH + middlePadding;

        beginEditorLayer(10);
        editorScope.shadow(labelX, labelY, boxW, boxH, radius, MD3Theme.FLOATING_LABEL_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.FLOATING_LABEL_SHADOW_ALPHA));
        editorScope.roundRect(labelX, labelY, boxW, boxH, radius, MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 238));
        editorScope.text(title, labelX + 12.0f, titleY, titleScale, MD3Theme.TEXT_PRIMARY);
        editorScope.text(subtitle, labelX + 12.0f, subtitleY, subtitleScale, MD3Theme.TEXT_MUTED);
        flushEditorLayer();
    }

    private void drawSnapGuides(UiTree.Scope scope, float screenW, float screenH) {
        if (currentSnap.hasAny()) {
            Color guideColor = MD3Theme.withAlpha(MD3Theme.PRIMARY, (int) GUIDE_ALPHA);
            if (!Float.isNaN(currentSnap.verticalLineX())) {
                float x = currentSnap.verticalLineX();
                scope.rect(x - 0.5f, 0.0f, 1.0f, screenH, guideColor);
            }
            if (!Float.isNaN(currentSnap.horizontalLineY())) {
                float y = currentSnap.horizontalLineY();
                scope.rect(0.0f, y - 0.5f, screenW, 1.0f, guideColor);
            }
        }
    }

    private void drawElementFrame(UiTree.Scope scope, UiTextMetrics textMetrics,
                                  HudModule element, boolean selected, boolean hover) {
        float x = element.x - ELEMENT_PADDING;
        float y = element.y - ELEMENT_PADDING;
        float w = element.width + ELEMENT_PADDING * 2.0f;
        float h = element.height + ELEMENT_PADDING * 2.0f;
        Color frameColor = selected ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 150);
        Color fillColor = selected ? MD3Theme.withAlpha(MD3Theme.PRIMARY_CONTAINER, 44) : MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, hover ? 48 : 24);

        scope.rect(x, y, w, h, fillColor);
        scope.rectOutline(x, y, w, h, selected ? 1.2f : 0.8f, frameColor);

        if (selected) {
            drawAnchorMarker(scope, element, frameColor);
            drawElementLabel(scope, textMetrics, element, x, y);
        }
    }

    private void drawAnchorMarker(UiTree.Scope scope, HudModule element, Color color) {
        float anchorX = HudLayoutHelper.getAnchorPointX(element.getHorizontalAnchor(), element.x, element.width);
        float anchorY = HudLayoutHelper.getAnchorPointY(element.getVerticalAnchor(), element.y, element.height);
        scope.rect(anchorX - 2.5f, anchorY - 2.5f, 5.0f, 5.0f, color);
    }

    private void drawElementLabel(UiTree.Scope scope, UiTextMetrics textMetrics,
                                  HudModule element, float frameX, float frameY) {
        String label = element.getTranslatedName();
        float scale = 0.48f;
        float textW = textMetrics.textWidth(label, scale);
        float labelW = textW + 10.0f;
        float labelY = frameY - LABEL_HEIGHT - 3.0f;
        float textY = labelY + (LABEL_HEIGHT - textMetrics.textHeight(scale)) * 0.5f;
        scope.roundRect(frameX, labelY, labelW, LABEL_HEIGHT, 6.5f, MD3Theme.PRIMARY_CONTAINER);
        scope.text(label, frameX + (labelW - textW) / 2.0f, textY, scale, MD3Theme.ON_PRIMARY_CONTAINER);
    }

    private void beginEditorLayer(int step) {
        editorLayer += step;
        editorScope = new UiTree.Scope();
    }

    private void flushEditorLayer() {
        editorBatch.render(UiTree.from(editorScope), editorLayer);
    }

    private void withEditorScissor(boolean required, float guiX, float guiY, float guiW, float guiH,
                                   Consumer<UiTree.Scope> content) {
        editorScope.scissorIf(required, new UiRect(guiX, guiY, guiW, guiH), content);
    }

    private final class EditorTextMetrics implements UiTextMetrics {
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
    public boolean keyPressed(KeyEvent event) {
        if (popupHost.keyPressed(event)) {
            return true;
        }
        if (hudPanel != null && hudPanel.hasActiveInput() && hudPanel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (handleEditorKey(event)) {
            return true;
        }
        if (hudPanel != null && hudPanel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popupHost.charTyped(event)) {
            return true;
        }
        String typed = event.codepointAsString();
        if (hudPanel != null && !typed.isEmpty() && hudPanel.charTyped(typed)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        preeditOverlay = event == null ? null : new IMEPreeditOverlay(event, font, 10);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (popupHost.mouseClicked(epsilonEvent, isDoubleClick)) {
            return true;
        }
        if (hudPanel != null && hudPanel.mouseClicked(epsilonEvent.x(), epsilonEvent.y(), epsilonEvent.button())) {
            validateSelection();
            return true;
        }
        if (epsilonEvent.button() == 0) {
            HudModule element = findElementAt(epsilonEvent.x(), epsilonEvent.y(), false);
            if (element != null) {
                selectedElement = element;
                draggingElement = element;
                dragOffsetX = (float) epsilonEvent.x() - element.x;
                dragOffsetY = (float) epsilonEvent.y() - element.y;
                currentSnap = SnapInfo.none();
                return true;
            }
            selectedElement = null;
            currentSnap = SnapInfo.none();
            return true;
        }
        return super.mouseClicked(epsilonEvent, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (popupHost.mouseReleased(epsilonEvent)) {
            return true;
        }
        if (draggingElement != null && epsilonEvent.button() == 0) {
            draggingElement = null;
            currentSnap = SnapInfo.none();
            return true;
        }
        if (hudPanel != null && hudPanel.mouseReleased(epsilonEvent.x(), epsilonEvent.y(), epsilonEvent.button())) {
            return true;
        }
        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)) {
            return true;
        }
        if (draggingElement != null) {
            double epsilonEventX = LuminRenderSystem.toEpsilonMouseX(event.x());
            double epsilonEventY = LuminRenderSystem.toEpsilonMouseY(event.y());
            moveElementTo(draggingElement, (float) epsilonEventX - dragOffsetX, (float) epsilonEventY - dragOffsetY, true);
            return true;
        }
        if (hudPanel != null) {
            hudPanel.mouseDragged(LuminRenderSystem.toEpsilonMouseX(event.x()), LuminRenderSystem.toEpsilonMouseY(event.y()));
        }
        return super.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            return true;
        }
        if (hudPanel != null && hudPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY);
    }

    private boolean handleEditorKey(KeyEvent event) {
        if (selectedElement == null) {
            return false;
        }

        float step = event.hasShiftDown() ? 10.0f : 1.0f;
        return switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                moveElementTo(selectedElement, selectedElement.x - step, selectedElement.y, false);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveElementTo(selectedElement, selectedElement.x + step, selectedElement.y, false);
                yield true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveElementTo(selectedElement, selectedElement.x, selectedElement.y - step, false);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveElementTo(selectedElement, selectedElement.x, selectedElement.y + step, false);
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                selectedElement.setEnabled(false);
                selectedElement = null;
                yield true;
            }
            default -> false;
        };
    }

    private void moveElementTo(HudModule element, float targetX, float targetY, boolean snap) {
        SnapInfo snapInfo = snap ? computeSnap(element, targetX, targetY) : new SnapInfo(targetX, targetY, Float.NaN, Float.NaN);
        currentSnap = snapInfo;
        element.moveTo(snapInfo.resolvedX(), snapInfo.resolvedY());
    }

    private SnapInfo computeSnap(HudModule element, float targetX, float targetY) {
        float screenW = LuminRenderSystem.getScaledWidth();
        float screenH = LuminRenderSystem.getScaledHeight();
        float snappedX = targetX;
        float snappedY = targetY;
        float verticalGuide = Float.NaN;
        float horizontalGuide = Float.NaN;

        float leftDelta = Math.abs(targetX);
        float centerDelta = Math.abs(targetX + element.width / 2.0f - screenW / 2.0f);
        float rightDelta = Math.abs(targetX + element.width - screenW);
        float bestX = leftDelta;
        int bestXIndex = 0;
        if (centerDelta < bestX) {
            bestX = centerDelta;
            bestXIndex = 1;
        }
        if (rightDelta < bestX) {
            bestX = rightDelta;
            bestXIndex = 2;
        }
        if (bestX <= SNAP_DISTANCE) {
            if (bestXIndex == 0) {
                snappedX = 0.0f;
                verticalGuide = 0.0f;
            } else if (bestXIndex == 1) {
                snappedX = screenW / 2.0f - element.width / 2.0f;
                verticalGuide = screenW / 2.0f;
            } else {
                snappedX = screenW - element.width;
                verticalGuide = screenW;
            }
        }

        float topDelta = Math.abs(targetY);
        float middleDelta = Math.abs(targetY + element.height / 2.0f - screenH / 2.0f);
        float bottomDelta = Math.abs(targetY + element.height - screenH);
        float bestY = topDelta;
        int bestYIndex = 0;
        if (middleDelta < bestY) {
            bestY = middleDelta;
            bestYIndex = 1;
        }
        if (bottomDelta < bestY) {
            bestY = bottomDelta;
            bestYIndex = 2;
        }
        if (bestY <= SNAP_DISTANCE) {
            if (bestYIndex == 0) {
                snappedY = 0.0f;
                horizontalGuide = 0.0f;
            } else if (bestYIndex == 1) {
                snappedY = screenH / 2.0f - element.height / 2.0f;
                horizontalGuide = screenH / 2.0f;
            } else {
                snappedY = screenH - element.height;
                horizontalGuide = screenH;
            }
        }

        return new SnapInfo(snappedX, snappedY, verticalGuide, horizontalGuide);
    }

    private HudModule findElementAt(double mouseX, double mouseY, boolean includePanelArea) {
        if (!includePanelArea && isOverPanel(mouseX, mouseY)) {
            return null;
        }
        List<HudModule> elements = HudElementManager.INSTANCE.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudModule element = elements.get(i);
            if (!element.isEnabled()) continue;
            if (element.contains(mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    private boolean isOverPanel(double mouseX, double mouseY) {
        return hudPanel != null
                && mouseX >= hudPanel.getX()
                && mouseX <= hudPanel.getX() + hudPanel.getWidth()
                && mouseY >= hudPanel.getY()
                && mouseY <= hudPanel.getY() + hudPanel.getPanelHeight();
    }

    private void validateSelection() {
        if (selectedElement != null && !selectedElement.isEnabled()) {
            selectedElement = null;
            draggingElement = null;
            currentSnap = SnapInfo.none();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        IMEFocusHelper.forceDeactivate();
        super.onClose();

        minecraft.gui.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        });
    }

    @Override
    public void removed() {
        super.removed();
        popupHost.close();
        shouldRenderElements = false;
        scene.clear();
        draggingElement = null;
        currentSnap = SnapInfo.none();
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, a);
        }
    }

    private float resolveMaxPanelHeight() {
        return Math.min(LuminRenderSystem.getScaledHeight() * 0.72f, 350.0f);
    }

    @Override
    public void openRegistryListSettingPopup(RegistryListSetting<?> setting) {
        UiRect bounds = popupHost.getCenteredBounds(
                Math.min(360.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
                Math.min(300.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        );
        popupHost.open(RegistryListSelectPopup.create(bounds, setting));
    }

    @Override
    public void openStringListSettingPopup(StringListSetting setting) {
        UiRect bounds = popupHost.getCenteredBounds(
                Math.min(300.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
                Math.min(260.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        );
        popupHost.open(new StringListSelectPopup(bounds, setting, setting::add, setting::remove));
    }

    private void ensureHudPanel() {
        int elementCount = HudElementManager.INSTANCE.getElements().size();
        if (hudPanel != null && panelElementCount == elementCount) return;

        float x = hudPanel == null ? DropdownTheme.PANEL_MARGIN_X : hudPanel.getX();
        float y = hudPanel == null ? DropdownTheme.PANEL_MARGIN_Y : hudPanel.getY();
        hudPanel = new CategoryPanel("hud_elements", "HUD", IconChars.WIDGETS, 0, HudElementManager.INSTANCE.getElements());
        hudPanel.setVisible(true);
        hudPanel.setOpened(true);
        hudPanel.setPosition(x, y);
        hudPanel.setMaxPanelHeight(resolveMaxPanelHeight());
        panelElementCount = elementCount;
    }

    private record SnapInfo(float x, float y, float verticalLineX, float horizontalLineY) {

        private static SnapInfo none() {
            return new SnapInfo(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        }

        private boolean hasAny() {
            return !Float.isNaN(verticalLineX) || !Float.isNaN(horizontalLineY);
        }

        private float resolvedX() {
            return Float.isNaN(x) ? 0.0f : x;
        }

        private float resolvedY() {
            return Float.isNaN(y) ? 0.0f : y;
        }

    }

}
