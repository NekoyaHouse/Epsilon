package com.github.epsilon.gui.troll;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
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
import com.github.epsilon.modules.impl.GuiSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.settings.impl.StringListSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
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

    private static final float TOGGLE_SIZE = 30.0f;
    private static final float TOGGLE_GAP = 8.0f;
    private static final float TOGGLE_MARGIN = 10.0f;

    private final Supplier<TextRenderer> textMetrics = Suppliers.memoize(TextRenderer::create);
    private final Supplier<UiScene> scene = Suppliers.memoize(() -> new UiScene(EpsilonUiTheme.INSTANCE));
    private final List<TrollWindow> windows = new ArrayList<>();
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final Animation guiOpenAnimation = new Animation(Easing.EASE_OUT_CUBIC, 400L);
    private final Animation friendToggleHover = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation configToggleHover = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private TrollFriendWindow friendWindow;
    private TrollConfigWindow configWindow;
    private boolean friendWindowOpen;
    private boolean configWindowOpen;
    private boolean guiAnimationStarted;
    private UiRect friendToggleBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private UiRect configToggleBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
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
    private float guiOpenOffsetY;
    private float pendingFrameWidth;
    private float pendingFrameHeight;

    private TrollGuiScreen() {
        super(Component.literal("TrollGui"));
        guiOpenAnimation.setStartValue(0.0f);
        friendToggleHover.setStartValue(0.0f);
        configToggleHover.setStartValue(0.0f);
    }

    @Override
    protected void init() {
        super.init();
        if (windows.isEmpty()) placeWindows();
        layoutWindows();
        if (!guiAnimationStarted) {
            guiAnimationStarted = true;
            guiOpenAnimation.setValue(0.0f);
            guiOpenAnimation.reset();
            guiOpenAnimation.run(1.0f);
            for (TrollWindow window : windows) window.startOpenAnimation();
        }
    }

    private void placeWindows() {
        TextRenderer metrics = textMetrics.get();
        float gap = 3.0f;
        float headerHeight = TrollTheme.headerHeight(metrics);
        float width = screenWidth();
        float height = screenHeight();

        List<TrollWindow> types = new ArrayList<>();
        types.add(new TrollCategoryWindow(null));
        for (Category category : Category.values()) types.add(new TrollCategoryWindow(category));
        types.add(new TrollFriendWindow());
        types.add(new TrollConfigWindow(popupHost));

        List<List<TrollWindow>> rows = new ArrayList<>();
        List<TrollWindow> currentRow = new ArrayList<>();
        float currentRowWidth = 0.0f;
        for (TrollWindow window : types) {
            window.layout(metrics, width, height);
            windows.add(window);
            if (window instanceof TrollFriendWindow friend) friendWindow = friend;
            if (window instanceof TrollConfigWindow config) configWindow = config;
            float windowWidth = window.bounds.width();
            if (!currentRow.isEmpty() && currentRowWidth + gap + windowWidth > width) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentRowWidth = 0.0f;
            }
            if (!currentRow.isEmpty()) currentRowWidth += gap;
            currentRow.add(window);
            currentRowWidth += windowWidth;
        }
        rows.add(currentRow);

        float nextTop = 0.0f;
        boolean cascaded = false;
        for (List<TrollWindow> row : rows) {
            float rowHeight = 0.0f;
            for (TrollWindow window : row) rowHeight = Math.max(rowHeight, window.bounds.height());
            float top = cascaded ? nextTop : Math.clamp(height - rowHeight, 0.0f, nextTop);
            float x = 0.0f;
            for (TrollWindow window : row) {
                window.position(x, cascaded ? top : Math.clamp(height - window.bounds.height(), 0.0f, nextTop));
                x += window.bounds.width() + gap;
            }
            float stacked = top + rowHeight + gap;
            if (stacked + headerHeight <= height) {
                cascaded = false;
                nextTop = stacked;
            } else {
                cascaded = true;
                nextTop = top + headerHeight + gap;
            }
        }

        if (friendWindow != null) {
            friendWindow.position(Math.max(0.0f, width - friendWindow.bounds.width() - gap), gap);
        }
        if (configWindow != null) {
            float configY = gap + (friendWindow == null ? 0.0f : friendWindow.bounds.height() + gap);
            configWindow.position(Math.max(0.0f, width - configWindow.bounds.width() - gap), configY);
        }
    }

    private float screenWidth() {
        return Math.max(1.0f, LuminRenderSystem.getScaledWidth());
    }

    private float screenHeight() {
        return Math.max(1.0f, LuminRenderSystem.getScaledHeight());
    }

    private void layoutWindows() {
        for (TrollWindow window : windows) window.layout(textMetrics.get(), screenWidth(), screenHeight());
        if (settingsWindow != null) settingsWindow.layout(textMetrics.get(), screenWidth(), screenHeight());
        if (colorPicker != null) colorPicker.layout(textMetrics.get(), screenWidth(), screenHeight());
        updateToggleBounds();
        popupHost.setOverlayBounds(new UiRect(0, 0, screenWidth(), screenHeight()));
    }

    private void updateToggleBounds() {
        float screenWidth = screenWidth();
        float screenHeight = screenHeight();
        float y = Math.max(0.0f, screenHeight - TOGGLE_MARGIN - TOGGLE_SIZE);
        float configX = Math.max(0.0f, screenWidth - TOGGLE_MARGIN - TOGGLE_SIZE);
        float friendX = Math.max(0.0f, configX - TOGGLE_GAP - TOGGLE_SIZE);
        friendToggleBounds = new UiRect(friendX, y, TOGGLE_SIZE, TOGGLE_SIZE);
        configToggleBounds = new UiRect(configX, y, TOGGLE_SIZE, TOGGLE_SIZE);
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
        guiOpenAnimation.run(1.0f);
        float openProgress = guiOpenAnimation.getValue();
        guiOpenOffsetY = (1.0f - openProgress) * screenHeight();
        UiScene frame = scene.get();
        frame.beginFrame();
        pendingScene = frame;
        pendingFrameWidth = screenWidth();
        pendingFrameHeight = screenHeight();
        pendingWindowBlurStrength = GuiSetting.INSTANCE.blurStrength.getValue().floatValue() * openProgress;
        pendingFullscreenBlurStrength = GuiSetting.INSTANCE.backgroundBlur.getValue()
                ? Math.max(1.5f, GuiSetting.INSTANCE.blurStrength.getValue().floatValue()) * openProgress
                : 0.0f;
        try {
            graphics.blurBeforeThisStratum();
            pendingBackground = frame.batch(UiLayer.BACKGROUND);
            if (GuiSetting.INSTANCE.background.getValue()) {
                int backgroundAlpha = (int) Math.round(144.0f * openProgress);
                pendingBackground.render(UiTree.build(scope -> scope.rect(0, 0, pendingFrameWidth, pendingFrameHeight,
                        new Color(0, 0, 0, backgroundAlpha))));
            }
            boolean modal = settingsWindow != null || colorPicker != null || popupHost.getActivePopup() != null;
            TrollWindow hovered = modal ? null : hoveredWindow(mx, my);
            for (int i = 0; i < windows.size(); i++) {
                TrollWindow trollWindow = windows.get(i);
                if (!isWindowVisible(trollWindow)) continue;
                int hoverX = hovered == trollWindow ? mx : Integer.MIN_VALUE;
                int hoverY = hovered == trollWindow ? my : Integer.MIN_VALUE;
                renderAnimatedWindow(frame, UiLayer.CONTENT, i * 8, trollWindow, hoverX, hoverY);
            }
            if (!modal) {
                renderToggleButtons(frame, mx, my);
            }
            if (settingsWindow != null) {
                boolean blocked = colorPicker != null || popupHost.getActivePopup() != null;
                renderAnimatedWindow(frame, UiLayer.FLOATING, 0, settingsWindow,
                        blocked ? Integer.MIN_VALUE : mx, blocked ? Integer.MIN_VALUE : my);
            }
            if (colorPicker != null) {
                renderAnimatedWindow(frame, UiLayer.POPUP, 0, colorPicker, mx, my);
            }
            UiRenderBatch popupBatch = frame.batch(UiLayer.OVERLAY);
            popupHost.render(graphics, popupBatch, mx, my, partialTick);
            if (popupHost.getActivePopup() != null) {
                pendingWindows.add(new WindowPass(
                        popupHost.getActivePopup().getBounds(),
                        popupBatch,
                        true,
                        null,
                        null,
                        0
                ));
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
        queueWindow(frame, layer, relativeLayer, bounds, tree, null, null, 0);
    }

    private void queueWindow(UiScene frame, UiLayer layer, int relativeLayer, UiRect bounds, UiTree tree, float[] blurRects, float[] blurRadii, int blurSegmentCount) {
        UiRenderBatch batch = frame.batch(layer, relativeLayer);
        batch.render(tree);
        pendingWindows.add(new WindowPass(bounds, batch, false, blurRects, blurRadii, blurSegmentCount));
    }

    private void renderAnimatedWindow(UiScene frame, UiLayer layer, int relativeLayer, TrollWindow window, int mouseX, int mouseY) {
        float windowProgress = window.getOpenProgress();
        float fullHeight = window.bounds.height();
        float revealHeight = window.headerHeight + Math.max(0.0f, fullHeight - window.headerHeight) * windowProgress;
        if (revealHeight <= 0.01f) return;

        UiRect clip = new UiRect(window.bounds.x(), window.bounds.y(), window.bounds.width(), revealHeight);
        UiRect passBounds = new UiRect(window.bounds.x(), window.bounds.y() + guiOpenOffsetY, window.bounds.width(), revealHeight);
        UiTree tree = UiTree.build(scope -> scope.pushAbsolute(0.0f, guiOpenOffsetY, translated -> translated.scissor(clip, content -> window.draw(content, textMetrics.get(), mouseX, mouseY))));
        queueWindow(frame, layer, relativeLayer, passBounds, tree);
    }

    private void renderToggleButtons(UiScene frame, int mouseX, int mouseY) {
        if (friendToggleBounds.width() <= 0.0f || configToggleBounds.width() <= 0.0f) return;
        UiTree tree = UiTree.build(scope -> {
            drawToggleButton(scope, friendToggleBounds, IconChars.PEOPLE, friendWindowOpen,
                    mouseX, mouseY, friendToggleHover);
            drawToggleButton(scope, configToggleBounds, IconChars.SETTINGS, configWindowOpen,
                    mouseX, mouseY, configToggleHover);
        });
        float left = Math.min(friendToggleBounds.x(), configToggleBounds.x());
        float top = Math.min(friendToggleBounds.y(), configToggleBounds.y());
        float right = Math.max(friendToggleBounds.right(), configToggleBounds.right());
        float bottom = Math.max(friendToggleBounds.bottom(), configToggleBounds.bottom());
        float[] blurRects = {
                friendToggleBounds.x(), friendToggleBounds.y(), friendToggleBounds.width(), friendToggleBounds.height(),
                configToggleBounds.x(), configToggleBounds.y(), configToggleBounds.width(), configToggleBounds.height()
        };
        float[] blurRadii = {
                friendToggleBounds.width() * 0.5f,
                configToggleBounds.width() * 0.5f
        };
        queueWindow(frame, UiLayer.FLOATING, -90, new UiRect(left, top, right - left, bottom - top), tree, blurRects, blurRadii, 2);
    }

    private void drawToggleButton(UiTree.Scope scope, UiRect bounds, String icon, boolean active, int mouseX, int mouseY, Animation hoverAnimation) {
        float hover = scope.animate(hoverAnimation, bounds.contains(mouseX, mouseY));
        float radius = bounds.width() * 0.5f;
        int backgroundAlpha = active ? (int) (205 + 50 * hover) : (int) (120 + 40 * hover);
        Color background = active
                ? TrollTheme.alpha(TrollTheme.primary(), backgroundAlpha)
                : new Color(0, 0, 0, backgroundAlpha);
        scope.roundRect(2, bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, background);
        TextRenderer metrics = textMetrics.get();
        float iconScale = 0.92f;
        float iconWidth = metrics.getWidth(icon, iconScale, StaticFontLoader.ICONS);
        float iconHeight = metrics.getHeight(iconScale, StaticFontLoader.ICONS);
        scope.text(4, icon,
                bounds.centerX() - iconWidth * 0.5f,
                bounds.centerY() - iconHeight * 0.5f,
                iconScale,
                active ? TrollTheme.text() : TrollTheme.alpha(TrollTheme.text(), 220),
                StaticFontLoader.ICONS);
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
                    if (pass.blurSegmentCount > 0) {
                        BlurShader.INSTANCE.render(renderTarget, pass.bounds.x(), pass.bounds.y(),
                                pass.bounds.width(), pass.bounds.height(), 0.0f, 0.0f, 0.0f, 0.0f,
                                pendingWindowBlurStrength, pass.blurRects, pass.blurRadii, pass.blurSegmentCount);
                    } else {
                        BlurShader.INSTANCE.render(renderTarget, pass.bounds.x(), pass.bounds.y(),
                                pass.bounds.width(), pass.bounds.height(), 0.0f, pendingWindowBlurStrength);
                    }
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

    private TrollWindow hoveredWindow(double mouseX, double mouseY) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            TrollWindow window = windows.get(i);
            if (isWindowVisible(window) && window.bounds.contains(mouseX, mouseY)) return window;
        }
        return null;
    }

    private boolean isWindowVisible(TrollWindow window) {
        if (window == friendWindow && !friendWindowOpen) return false;
        if (window == configWindow && !configWindowOpen) return false;
        return true;
    }

    private void toggleFriendWindow() {
        if (friendWindow == null) return;
        friendWindowOpen = !friendWindowOpen;
        if (friendWindowOpen) {
            friendWindow.startOpenAnimation();
            bringToFront(friendWindow);
        } else {
            friendWindow.resetInput();
        }
    }

    private void toggleConfigWindow() {
        if (configWindow == null) return;
        configWindowOpen = !configWindowOpen;
        if (configWindowOpen) {
            configWindow.startOpenAnimation();
            bringToFront(configWindow);
        } else {
            configWindow.resetInput();
        }
    }

    private void bringToFront(TrollWindow window) {
        windows.remove(window);
        windows.add(window);
    }

    public TrollSettingsWindow openSettings(Module module, double x, double y) {
        for (TrollWindow window : windows) window.blurInput();
        closeSettings();
        settingsWindow = new TrollSettingsWindow(module, (float) x, (float) y);
        settingsWindow.startOpenAnimation();
        layoutWindows();
        settingsWindow.startSettingAnimations();
        return settingsWindow;
    }

    public void openColorPicker(ColorSetting setting, double x, double y) {
        for (TrollWindow window : windows) window.blurInput();
        if (colorPicker != null) colorPicker.cancel();
        colorPicker = new TrollColorPicker(setting, (float) x, (float) y);
        colorPicker.startOpenAnimation();
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
        if (friendToggleBounds.contains(mouse.x(), mouse.y())) {
            for (TrollWindow window : windows) window.blurInput();
            toggleFriendWindow();
            return true;
        }
        if (configToggleBounds.contains(mouse.x(), mouse.y())) {
            for (TrollWindow window : windows) window.blurInput();
            toggleConfigWindow();
            return true;
        }
        TrollWindow window = hoveredWindow(mouse.x(), mouse.y());
        if (window != null) {
            for (TrollWindow other : windows) {
                if (other != window) other.blurInput();
            }
            windows.remove(window);
            windows.add(window);
            capturedWindow = window;
            window.mouseClicked(mouse);
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
            TrollWindow window = hoveredWindow(mx, my);
            if (window != null) window.mouseScrolled(mx, my, scrollY);
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
        for (int i = windows.size() - 1; i >= 0; i--) {
            TrollWindow window = windows.get(i);
            if (isWindowVisible(window) && window.keyPressed(event)) return true;
        }
        if (event.isEscape()) onClose();
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popupHost.getActivePopup() != null) return popupHost.charTyped(event);
        if (colorPicker != null) return false;
        if (settingsWindow != null) return settingsWindow.charTyped(event);
        for (int i = windows.size() - 1; i >= 0; i--) {
            TrollWindow window = windows.get(i);
            if (isWindowVisible(window) && window.charTyped(event)) return true;
        }
        return false;
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        preeditOverlay = event == null ? null : new IMEPreeditOverlay(event, font, 15);
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
        for (TrollWindow window : windows) window.resetInput();
        friendWindowOpen = false;
        configWindowOpen = false;
        guiAnimationStarted = false;
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

    private record WindowPass(
            UiRect bounds, UiRenderBatch batch, boolean popup,
            float[] blurRects, float[] blurRadii, int blurSegmentCount
    ) {
    }

}
