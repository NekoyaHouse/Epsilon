package com.github.epsilon.gui.troll;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
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
    private List<UiRect> pendingBlurRegions = List.of();
    private float pendingBlurStrength;
    private float pendingBlurWidth;
    private float pendingBlurHeight;

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
        var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("troll-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        int mx = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int my = LuminRenderSystem.toEpsilonMouseY(mouseY);
        layoutWindows();
        queueBackgroundBlur(graphics);
        UiScene frame = scene.get();
        frame.beginFrame();
        LuminRenderSystem.LuminRenderTarget previousTarget = LuminRenderSystem.getActiveTarget();
        LuminRenderSystem.setActiveTarget(renderTarget);
        try {
            if (ClientSetting.INSTANCE.trollBackground.getValue()) {
                frame.submit(UiLayer.BACKGROUND, UiTree.build(scope -> scope.rect(0, 0, screenWidth(), screenHeight(), new Color(0, 0, 0, 144))));
            }
            boolean modal = settingsWindow != null || colorPicker != null || popupHost.getActivePopup() != null;
            TrollCategoryWindow hovered = modal ? null : hoveredCategory(mx, my);
            for (int i = 0; i < categories.size(); i++) {
                TrollCategoryWindow category = categories.get(i);
                int hoverX = hovered == category ? mx : Integer.MIN_VALUE;
                int hoverY = hovered == category ? my : Integer.MIN_VALUE;
                // 每个窗口占用独立的相对层段，后置窗口的底色必须遮住先前窗口的文本。
                frame.submit(UiLayer.CONTENT, i * 8, UiTree.build(scope -> category.draw(scope, textMetrics.get(), hoverX, hoverY)));
            }
            if (settingsWindow != null) {
                boolean blocked = colorPicker != null || popupHost.getActivePopup() != null;
                frame.submit(UiLayer.FLOATING, UiTree.build(scope -> settingsWindow.draw(scope, textMetrics.get(), blocked ? Integer.MIN_VALUE : mx, blocked ? Integer.MIN_VALUE : my)));
            }
            if (colorPicker != null)
                frame.submit(UiLayer.POPUP, UiTree.build(scope -> colorPicker.draw(scope, textMetrics.get(), mx, my)));
            popupHost.render(graphics, frame.batch(UiLayer.OVERLAY), mx, my, partialTick);
        } finally {
            try {
                frame.endFrame();
                popupHost.flush();
            } finally {
                LuminRenderSystem.setActiveTarget(previousTarget);
            }
        }
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        popupHost.extractOverlay(graphics, mx, my, partialTick);
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) LuminRenderSystem.toMinecraftGuiX(IMEFocusHelper.activeCursorX), (int) LuminRenderSystem.toMinecraftGuiY(IMEFocusHelper.activeCursorY));
            graphics.setPreeditOverlay(preeditOverlay);
        }
    }

    private void queueBackgroundBlur(GuiGraphicsExtractor graphics) {
        pendingBlurRegions = List.of();
        pendingBlurStrength = ClientSetting.INSTANCE.trollBackgroundBlur.getValue().floatValue() * 15.0f;
        pendingBlurWidth = screenWidth();
        pendingBlurHeight = screenHeight();
        if (ClientSetting.INSTANCE.trollBlur.getValue()) {
            pendingBlurStrength = Math.max(1.0f, pendingBlurStrength);
            pendingBlurRegions = List.of(new UiRect(0, 0, pendingBlurWidth, pendingBlurHeight));
        } else if (pendingBlurStrength > 0.0f) {
            List<UiRect> regions = new ArrayList<>();
            for (TrollCategoryWindow category : categories) regions.add(category.bounds);
            if (settingsWindow != null) regions.add(settingsWindow.bounds);
            if (colorPicker != null) regions.add(colorPicker.bounds);
            pendingBlurRegions = List.copyOf(regions);
        }
        // 提取阶段只记录区域；原版会先绘制世界、全景及先前的 GUI stratum，再执行模糊。
        if (!pendingBlurRegions.isEmpty()) graphics.blurBeforeThisStratum();
    }

    /**
     * 在 GuiRenderer 的模糊提交点采样本帧主目标；不得在 Screen 提取阶段调用。
     *
     * @return 是否消费了本帧的 TrollHack 模糊请求
     */
    public boolean renderBackgroundBlur() {
        List<UiRect> regions = pendingBlurRegions;
        pendingBlurRegions = List.of();
        if (regions.isEmpty()) return false;

        float[] rects = new float[regions.size() * 4];
        for (int i = 0; i < regions.size(); i++) {
            UiRect bounds = regions.get(i);
            rects[i * 4] = bounds.x();
            rects[i * 4 + 1] = bounds.y();
            rects[i * 4 + 2] = bounds.width();
            rects[i * 4 + 3] = bounds.height();
        }
        LuminRenderSystem.LuminRenderTarget previousTarget = LuminRenderSystem.getActiveTarget();
        LuminRenderSystem.setActiveTarget(null);
        try {
            // 一次采样并合并窗口遮罩，避免重叠窗口重复模糊同一区域。
            BlurShader.INSTANCE.render(0, 0, pendingBlurWidth, pendingBlurHeight, 0.0f, pendingBlurStrength, rects, null, regions.size());
        } finally {
            LuminRenderSystem.setActiveTarget(previousTarget);
        }
        return true;
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
        pendingBlurRegions = List.of();
        pendingBlurStrength = 0.0f;
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

}
