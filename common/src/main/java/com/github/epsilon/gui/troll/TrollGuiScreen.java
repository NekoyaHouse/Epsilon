package com.github.epsilon.gui.troll;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup;
import com.github.epsilon.gui.panel.popup.StringListSelectPopup;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.settings.impl.StringListSetting;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TrollGuiScreen extends Screen implements ListSettingPopupScreen {

    public static final TrollGuiScreen INSTANCE = new TrollGuiScreen();

    private final Supplier<TextRenderer> textMetrics = Suppliers.memoize(TextRenderer::create);
    private final Supplier<UiScene> scene = Suppliers.memoize(() -> new UiScene(EpsilonUiTheme.INSTANCE));
    private final List<TrollCategoryWindow> categories = new ArrayList<>();
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private TrollSettingsWindow settingsWindow;
    private TrollColorPicker colorPicker;
    private TrollWindow capturedWindow;
    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;
    private final List<WindowPass> pendingWindows = new ArrayList<>();
    private UiScene pendingScene;
    private UiRenderBatch pendingBackground;
    private float pendingWindowBlurStrength;
    private float pendingFullscreenBlurStrength;
    private float pendingFrameWidth;
    private float pendingFrameHeight;

    private TrollGuiScreen() {
        super(Component.literal("TrollHack"));
    }

    @Override
    protected void init() {
        super.init();
        if (categories.isEmpty()) {
            float x = 0.0f;
            float y = 0.0f;
            float rowHeight = 0.0f;
            List<Category> types = new ArrayList<>();
            types.add(null);
            types.addAll(List.of(Category.values()));
            for (Category category : types) {
                TrollCategoryWindow window = new TrollCategoryWindow(category);
                window.layout(textMetrics.get(), screenWidth(), screenHeight());
                if (x > 0.0f && x + window.bounds.width() > screenWidth()) {
                    x = 0.0f;
                    y += rowHeight + 2.0f;
                    rowHeight = 0.0f;
                }
                window.position(x, Math.clamp(screenHeight() - window.bounds.height(), 0.0f, y));
                categories.add(window);
                x += window.bounds.width() + 2.0f;
                rowHeight = Math.max(rowHeight, window.bounds.height());
            }
        }
        layoutWindows();
    }

    private float screenWidth() {
        return Math.max(1.0f, LuminRenderSystem.getScaledWidth());
    }

    private float screenHeight() {
        return Math.max(1.0f, LuminRenderSystem.getScaledHeight());
    }

    private void layoutWindows() {
        for (TrollCategoryWindow window : categories) window.layout(textMetrics.get(), screenWidth(), screenHeight());
        if (settingsWindow != null) settingsWindow.layout(textMetrics.get(), screenWidth(), screenHeight());
        if (colorPicker != null) colorPicker.layout(textMetrics.get(), screenWidth(), screenHeight());
        popupHost.setOverlayBounds(new UiRect(0, 0, screenWidth(), screenHeight()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        endPendingFrame();
        var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("troll-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        int mx = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int my = LuminRenderSystem.toEpsilonMouseY(mouseY);
        layoutWindows();
        UiScene frame = scene.get();
        frame.beginFrame();
        pendingScene = frame;
        pendingFrameWidth = screenWidth();
        pendingFrameHeight = screenHeight();
        pendingWindowBlurStrength = ClientSetting.INSTANCE.trollBackgroundBlur.getValue().floatValue() * 15.0f;
        pendingFullscreenBlurStrength = ClientSetting.INSTANCE.trollBlur.getValue() ? Math.max(1.0f, pendingWindowBlurStrength) : 0.0f;
        try {
            graphics.blurBeforeThisStratum();
            pendingBackground = frame.batch(UiLayer.BACKGROUND);
            if (ClientSetting.INSTANCE.trollBackground.getValue()) {
                pendingBackground.render(UiTree.build(scope -> scope.rect(0, 0, pendingFrameWidth, pendingFrameHeight, new Color(0, 0, 0, 144))));
            }
            boolean modal = settingsWindow != null || colorPicker != null || popupHost.getActivePopup() != null;
            TrollCategoryWindow hovered = modal ? null : hoveredCategory(mx, my);
            for (int i = 0; i < categories.size(); i++) {
                TrollCategoryWindow category = categories.get(i);
                int hoverX = hovered == category ? mx : Integer.MIN_VALUE;
                int hoverY = hovered == category ? my : Integer.MIN_VALUE;
                queueWindow(frame, UiLayer.CONTENT, i * 8, category.bounds, UiTree.build(scope -> category.draw(scope, textMetrics.get(), hoverX, hoverY)));
            }
            if (settingsWindow != null) {
                boolean blocked = colorPicker != null || popupHost.getActivePopup() != null;
                queueWindow(frame, UiLayer.FLOATING, 0, settingsWindow.bounds, UiTree.build(scope -> settingsWindow.draw(scope, textMetrics.get(), blocked ? Integer.MIN_VALUE : mx, blocked ? Integer.MIN_VALUE : my)));
            }
            if (colorPicker != null)
                queueWindow(frame, UiLayer.POPUP, 0, colorPicker.bounds, UiTree.build(scope -> colorPicker.draw(scope, textMetrics.get(), mx, my)));
            UiRenderBatch popupBatch = frame.batch(UiLayer.OVERLAY);
            popupHost.render(graphics, popupBatch, mx, my, partialTick);
            if (popupHost.getActivePopup() != null) {
                pendingWindows.add(new WindowPass(popupHost.getActivePopup().getBounds(), popupBatch, true));
            }
        } catch (RuntimeException | Error exception) {
            endPendingFrame();
            throw exception;
        }
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        popupHost.extractOverlay(graphics, mx, my, partialTick);
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) LuminRenderSystem.toMinecraftGuiX(IMEFocusHelper.activeCursorX), (int) LuminRenderSystem.toMinecraftGuiY(IMEFocusHelper.activeCursorY));
            graphics.setPreeditOverlay(preeditOverlay);
        }
    }

    private void queueWindow(UiScene frame, UiLayer layer, int relativeLayer, UiRect bounds, UiTree tree) {
        UiRenderBatch batch = frame.batch(layer, relativeLayer);
        batch.render(tree);
        pendingWindows.add(new WindowPass(bounds, batch, false));
    }

    public boolean renderPendingFrame() {
        if (pendingScene == null) return false;

        LuminRenderSystem.LuminRenderTarget previousTarget = LuminRenderSystem.getActiveTarget();
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousProjectionType = RenderSystem.getProjectionType();
        try {
            var mainTarget = minecraft.gameRenderer.mainRenderTarget();
            var source = mainTarget.getColorTexture();
            if (source == null || renderTarget == null || mainTarget.width != renderTarget.width() || mainTarget.height != renderTarget.height()) {
                return true;
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, renderTarget.colorTexture(), 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
            LuminRenderSystem.setActiveTarget(renderTarget);
            if (pendingFullscreenBlurStrength > 0.0f) {
                BlurShader.INSTANCE.render(renderTarget, 0, 0, pendingFrameWidth, pendingFrameHeight, 0.0f, pendingFullscreenBlurStrength);
            }
            pendingBackground.flushAndClear();
            for (WindowPass pass : pendingWindows) {
                if (pendingWindowBlurStrength > 0.0f) {
                    BlurShader.INSTANCE.render(renderTarget, pass.bounds.x(), pass.bounds.y(), pass.bounds.width(), pass.bounds.height(), 0.0f, pendingWindowBlurStrength);
                }
                pass.batch.flushAndClear();
                if (pass.popup) popupHost.flush();
            }
        } finally {
            try {
                endPendingFrame();
            } finally {
                LuminRenderSystem.setActiveTarget(previousTarget);
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
        return true;
    }

    private void endPendingFrame() {
        UiScene frame = pendingScene;
        pendingScene = null;
        pendingBackground = null;
        pendingWindows.clear();
        if (frame != null) {
            frame.clear();
            frame.endFrame();
        }
    }

    private TrollCategoryWindow hoveredCategory(double mouseX, double mouseY) {
        for (int i = categories.size() - 1; i >= 0; i--) {
            if (categories.get(i).bounds.contains(mouseX, mouseY)) return categories.get(i);
        }
        return null;
    }

    public TrollSettingsWindow openSettings(Module module, double x, double y) {
        closeSettings();
        settingsWindow = new TrollSettingsWindow(module, (float) x, (float) y);
        layoutWindows();
        return settingsWindow;
    }

    public void openColorPicker(ColorSetting setting, double x, double y) {
        if (colorPicker != null) colorPicker.cancel();
        colorPicker = new TrollColorPicker(setting, (float) x, (float) y);
        colorPicker.layout(textMetrics.get(), screenWidth(), screenHeight());
    }

    private void closeSettings() {
        TrollSettingsWindow previous = settingsWindow;
        settingsWindow = null;
        if (previous != null) previous.resetInput();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent mouse = LuminRenderSystem.toEpsilonMouseEvent(event);
        capturedWindow = null;
        if (popupHost.getActivePopup() != null) {
            popupHost.mouseClicked(mouse, doubleClick);
            return true;
        }
        if (colorPicker != null) {
            if (!colorPicker.bounds.contains(mouse.x(), mouse.y())) colorPicker.cancel();
            else {
                capturedWindow = colorPicker;
                colorPicker.mouseClicked(mouse);
            }
            if (colorPicker.isClosed()) colorPicker = null;
            return true;
        }
        if (settingsWindow != null) {
            if (settingsWindow.captureMouseBind(mouse)) return true;
            if (!settingsWindow.bounds.contains(mouse.x(), mouse.y())) closeSettings();
            else {
                capturedWindow = settingsWindow;
                settingsWindow.mouseClicked(mouse);
            }
            return true;
        }
        TrollCategoryWindow category = hoveredCategory(mouse.x(), mouse.y());
        if (category != null) {
            categories.remove(category);
            categories.add(category);
            capturedWindow = category;
            category.mouseClicked(mouse);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        TrollWindow captured = capturedWindow;
        capturedWindow = null;
        if (popupHost.getActivePopup() != null) popupHost.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event));
        else if (captured != null) captured.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent mouse = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (popupHost.getActivePopup() != null)
            popupHost.mouseDragged(mouse, LuminRenderSystem.toEpsilonMouseX(dx), LuminRenderSystem.toEpsilonMouseY(dy));
        else if (capturedWindow != null) capturedWindow.mouseDragged(mouse);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        double mx = LuminRenderSystem.toEpsilonMouseX(x);
        double my = LuminRenderSystem.toEpsilonMouseY(y);
        if (popupHost.getActivePopup() != null) popupHost.mouseScrolled(mx, my, scrollX, scrollY);
        else if (colorPicker != null) return true;
        else if (settingsWindow != null) {
            if (settingsWindow.viewport.contains(mx, my)) settingsWindow.scroll.scroll(scrollY);
        } else {
            TrollCategoryWindow category = hoveredCategory(mx, my);
            if (category != null) category.scroll.scroll(scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popupHost.getActivePopup() != null) {
            popupHost.keyPressed(event);
            return true;
        }
        if (colorPicker != null) {
            if (event.isEscape()) colorPicker.cancel();
            else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
                colorPicker.confirm();
            if (colorPicker.isClosed()) colorPicker = null;
            return true;
        }
        if (settingsWindow != null) {
            if (!settingsWindow.keyPressed(event) && event.isEscape()) closeSettings();
            return true;
        }
        if (event.isEscape()) onClose();
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popupHost.getActivePopup() != null) return popupHost.charTyped(event);
        return colorPicker == null && settingsWindow != null && settingsWindow.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        preeditOverlay = event == null ? null : new IMEPreeditOverlay(event, font, 10);
        return true;
    }

    @Override
    public void openRegistryListSettingPopup(RegistryListSetting<?> setting) {
        popupHost.open(RegistryListSelectPopup.create(popupHost.getCenteredBounds(Math.min(360.0f, screenWidth()), Math.min(300.0f, screenHeight())), setting));
    }

    @Override
    public void openStringListSettingPopup(StringListSetting setting) {
        popupHost.open(new StringListSelectPopup(popupHost.getCenteredBounds(Math.min(300.0f, screenWidth()), Math.min(260.0f, screenHeight())), setting, setting::add, setting::remove));
    }

    @Override
    public void removed() {
        endPendingFrame();
        popupHost.close();
        if (colorPicker != null) colorPicker.cancel();
        colorPicker = null;
        closeSettings();
        for (TrollCategoryWindow category : categories) category.resetInput();
        capturedWindow = null;
        preeditOverlay = null;
        IMEFocusHelper.forceDeactivate();
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        ConfigManager.INSTANCE.saveNow();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record WindowPass(UiRect bounds, UiRenderBatch batch, boolean popup) {
    }

}
