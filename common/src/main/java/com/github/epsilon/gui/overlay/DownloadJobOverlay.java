package com.github.epsilon.gui.overlay;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.screen.EpsilonDialogScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.AssetManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import static com.github.epsilon.Constants.mc;

public class DownloadJobOverlay implements AutoCloseable {

    public static final DownloadJobOverlay INSTANCE = new DownloadJobOverlay();

    private static final float MARGIN = 10.0f;
    private static final float WIDTH = 244.0f;
    private static final float HEIGHT = 82.0f;
    private static final float PADDING = 12.0f;

    private UiScene scene;
    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private UiRect bounds;
    private Screen renderedScreen;
    private AssetManager.DownloadJob renderedJob;

    private DownloadJobOverlay() {
    }

    public void extractRenderState(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (mc == null) return;
        AssetManager.DownloadJob job = AssetManager.INSTANCE.activeJob();
        if (screen != mc.gui.screen() || screen instanceof EpsilonDialogScreen || job == null || !job.isRunning()) {
            close();
            return;
        }

        float width = LuminRenderSystem.getScaledWidth();
        float height = LuminRenderSystem.getScaledHeight();
        float cardWidth = Math.min(WIDTH, width - MARGIN * 2.0f);
        if (cardWidth < 120.0f || height < HEIGHT + MARGIN * 2.0f) {
            close();
            return;
        }
        bounds = new UiRect(width - MARGIN - cardWidth, height - MARGIN - HEIGHT, cardWidth, HEIGHT);
        boolean hovered = bounds.contains(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
        MD3Theme.syncFromSettings();

        var window = mc.getWindow();
        if (scene == null) scene = new UiScene(EpsilonUiTheme.INSTANCE);
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("download-job-overlay", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        var previousTarget = LuminRenderSystem.getActiveTarget();
        LuminRenderSystem.setActiveTarget(renderTarget);
        try {
            scene.beginFrame();
            drawCard(job, hovered);
            scene.endFrame();
        } finally {
            LuminRenderSystem.setActiveTarget(previousTarget);
        }
        graphics.nextStratum();
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        renderedScreen = screen;
        renderedJob = job;
    }

    private void drawCard(AssetManager.DownloadJob job, boolean hovered) {
        AssetManager.Asset current = job.currentAsset();
        float progress = job.progress();
        String percent = Math.round(progress * 100.0f) + "%";
        String currentPercent = current == null ? "" : Math.round(job.currentProgress() * 100.0f) + "%";
        String title = EpsilonTranslations.Resources.TITLE.getTranslatedName();
        String resource = current == null ? EpsilonTranslations.Resources.JOB_PREPARING.getTranslatedName() : switch (current) {
            case VIDEO -> EpsilonTranslations.Resources.VIDEO.getTranslatedName();
            case LIGHT_TRAILS -> EpsilonTranslations.Resources.LIGHT_TRAILS.getTranslatedName();
            case REISA -> EpsilonTranslations.Resources.REISA.getTranslatedName();
            case FFMPEG -> EpsilonTranslations.Resources.FFMPEG.getTranslatedName();
        };
        int index = current == null ? 0 : job.assets().indexOf(current) + 1;
        String count = index + " / " + job.assets().size();
        String details = EpsilonTranslations.Resources.JOB_DETAILS.getTranslatedName();
        float contentWidth = bounds.width() - PADDING * 2.0f;
        float percentWidth = scene.scheduler().textMetrics().getWidth(percent, 0.68f);
        float currentPercentWidth = scene.scheduler().textMetrics().getWidth(currentPercent, 0.58f);
        float countWidth = scene.scheduler().textMetrics().getWidth(count, 0.54f);
        float detailsScale = fitScale(details, 0.54f, contentWidth - countWidth - 12.0f);
        float detailsWidth = scene.scheduler().textMetrics().getWidth(details, detailsScale);

        UiTree tree = UiTree.build(scope -> {
            scope.shadow(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS,
                    MD3Theme.POPUP_SHADOW_BLUR, MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.POPUP_SHADOW_ALPHA));
            scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS,
                    hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER);
            scope.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS,
                    0.8f, hovered ? MD3Theme.PRIMARY : MD3Theme.OUTLINE_SOFT);
            scope.pushAbsolute(bounds, card -> {
                card.text(title, PADDING, 10.0f, fitScale(title, 0.68f, contentWidth - percentWidth - 10.0f), MD3Theme.TEXT_PRIMARY);
                card.text(percent, bounds.width() - PADDING - percentWidth, 10.0f, 0.68f, MD3Theme.PRIMARY);
                card.roundRect(PADDING, 31.0f, contentWidth, 4.0f, 2.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST);
                if (progress > 0.0f) {
                    card.roundRect(PADDING, 31.0f, contentWidth * progress, 4.0f, 2.0f, MD3Theme.PRIMARY);
                }
                card.text(resource, PADDING, 43.0f, fitScale(resource, 0.58f, contentWidth - currentPercentWidth - 10.0f), MD3Theme.TEXT_SECONDARY);
                card.text(currentPercent, bounds.width() - PADDING - currentPercentWidth, 43.0f, 0.58f, MD3Theme.TEXT_SECONDARY);
                card.text(count, PADDING, 63.0f, 0.54f, MD3Theme.TEXT_MUTED);
                card.text(details, bounds.width() - PADDING - detailsWidth, 63.0f, detailsScale, MD3Theme.PRIMARY);
            });
        });
        scene.submit(UiLayer.CONTENT, tree);
    }

    private float fitScale(String text, float preferredScale, float maxWidth) {
        float textWidth = scene.scheduler().textMetrics().getWidth(text, preferredScale);
        return textWidth > maxWidth && textWidth > 0.0f ? preferredScale * Math.max(1.0f, maxWidth) / textWidth : preferredScale;
    }

    public boolean mouseClicked(Screen screen, MouseButtonEvent event) {
        if (mc == null || screen != mc.gui.screen() || screen != renderedScreen || bounds == null
                || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        AssetManager.DownloadJob job = AssetManager.INSTANCE.activeJob();
        if (job == null || job != renderedJob || !job.isRunning()) return false;
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (!bounds.contains(epsilonEvent.x(), epsilonEvent.y())) return false;
        AssetManager.INSTANCE.openDownloadScreen(job.assets());
        return true;
    }

    @Override
    public void close() {
        bounds = null;
        renderedScreen = null;
        renderedJob = null;
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (scene != null) {
            scene.close();
            scene = null;
        }
    }

}
