package com.github.epsilon.gui.screen;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Epsilon 风格的模态卡片对话框基类。
 * <p>
 * 统一负责离屏渲染目标、遮罩、卡片外壳、标题、按钮布局与点击路由，子类只需要描述卡片
 * 尺寸、正文内容和按钮列表即可。
 */
public abstract class EpsilonDialogScreen extends Screen {

    protected static final float CARD_PADDING = 14.0f;
    protected static final float TITLE_SCALE = 0.95f;
    protected static final float BODY_SCALE = 0.62f;
    protected static final float BUTTON_HEIGHT = 26.0f;
    protected static final float BUTTON_GAP = 6.0f;
    protected static final float BUTTON_MIN_WIDTH = 78.0f;

    private static final Color SCRIM = new Color(0, 0, 0, 150);

    /**
     * 对话框按钮描述。
     *
     * @param label   按钮文案
     * @param primary 是否使用主色（否则使用次级容器色）
     * @param action  点击回调
     */
    protected record DialogButton(String label, boolean primary, Runnable action) {
    }

    private record ButtonHit(UiRect bounds, DialogButton button) {
    }

    protected final Screen parent;

    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);
    private final List<ButtonHit> buttonHits = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private UiRect cardBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    protected EpsilonDialogScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected abstract String titleText();

    protected abstract float cardWidth();

    protected abstract float cardHeight();

    /**
     * 绘制卡片正文；坐标以卡片左上角为原点。
     */
    protected abstract void buildBody(UiTree.Scope scope, float contentTop, int mouseX, int mouseY);

    /**
     * 当前帧的按钮列表，按从左到右的顺序返回。
     */
    protected abstract List<DialogButton> buttons();

    /**
     * 对话框被关闭（ESC、关闭按钮或父界面切换）时回调。
     */
    protected void onDialogClosed() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        onDialogClosed();
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("epsilon-dialog", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        LuminRenderSystem.setActiveTarget(renderTarget);

        int width = LuminRenderSystem.getScaledWidthInt();
        int height = LuminRenderSystem.getScaledHeightInt();
        int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);

        float cardW = Math.min(cardWidth(), width - 20.0f);
        float cardH = Math.min(cardHeight(), height - 20.0f);
        cardBounds = new UiRect((width - cardW) * 0.5f, (height - cardH) * 0.5f, cardW, cardH);
        List<ButtonHit> hits = layoutButtons();

        scene.beginFrame();
        UiTree tree = UiTree.build(scope -> {
            scope.rect(0.0f, 0.0f, width, height, SCRIM);
            scope.layer(0, card -> {
                card.shadow(cardBounds.x(), cardBounds.y(), cardBounds.width(), cardBounds.height(),
                        MD3Theme.CARD_RADIUS, MD3Theme.POPUP_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.POPUP_SHADOW_ALPHA));
                card.roundRect(cardBounds.x(), cardBounds.y(), cardBounds.width(), cardBounds.height(),
                        MD3Theme.CARD_RADIUS, MD3Theme.SURFACE_CONTAINER_LOW);
            });
            scope.pushAbsolute(cardBounds, content -> {
                content.text(titleText(), CARD_PADDING, CARD_PADDING, TITLE_SCALE, MD3Theme.TEXT_PRIMARY);
                buildBody(content, CARD_PADDING + 22.0f, epsilonMouseX, epsilonMouseY);
            });
            scope.layer(10, content -> {
                for (ButtonHit hit : hits) {
                    boolean hovered = hit.bounds().contains(epsilonMouseX, epsilonMouseY);
                    Color background = hit.button().primary()
                            ? (hovered ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.PRIMARY_CONTAINER)
                            : (hovered ? MD3Theme.SURFACE_CONTAINER_HIGHEST : MD3Theme.SURFACE_CONTAINER_HIGH);
                    Color foreground = hit.button().primary() ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY;
                    content.button(hit.bounds(), MD3Theme.CONTROL_RADIUS, background, hit.button().label(), 0.62f, foreground);
                }
            });
        });
        scene.submit(UiLayer.CONTENT, tree);
        scene.flush();
        scene.clear();

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (epsilonEvent.button() == 0) {
            for (ButtonHit hit : buttonHits) {
                if (hit.bounds().contains(epsilonEvent.x(), epsilonEvent.y())) {
                    hit.button().action().run();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
    }

    protected UiRect cardBounds() {
        return cardBounds;
    }

    /**
     * 供子类测量文本使用的渲染器。
     */
    protected com.github.epsilon.graphics.renderers.TextRenderer textMetrics() {
        return scene.scheduler().textMetrics();
    }

    private List<ButtonHit> layoutButtons() {
        buttonHits.clear();
        List<DialogButton> buttons = buttons();
        if (buttons == null || buttons.isEmpty() || cardBounds.width() <= 0.0f) {
            return List.of();
        }

        var metrics = scene.scheduler().textMetrics();
        float[] widths = new float[buttons.size()];
        float totalWidth = BUTTON_GAP * (buttons.size() - 1);
        for (int i = 0; i < buttons.size(); i++) {
            float textWidth = metrics.getWidth(buttons.get(i).label(), 0.62f);
            widths[i] = Math.max(BUTTON_MIN_WIDTH, textWidth + 26.0f);
            totalWidth += widths[i];
        }

        float x = cardBounds.x() + cardBounds.width() - CARD_PADDING - totalWidth;
        float y = cardBounds.y() + cardBounds.height() - CARD_PADDING - BUTTON_HEIGHT;
        for (int i = 0; i < buttons.size(); i++) {
            UiRect bounds = new UiRect(x, y, widths[i], BUTTON_HEIGHT);
            buttonHits.add(new ButtonHit(bounds, buttons.get(i)));
            x += widths[i] + BUTTON_GAP;
        }
        return List.copyOf(buttonHits);
    }

}
