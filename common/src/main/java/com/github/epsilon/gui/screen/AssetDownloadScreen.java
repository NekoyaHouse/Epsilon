package com.github.epsilon.gui.screen;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.AssetManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 运行时资源下载界面。
 * <p>
 * 展示每个资源的大小与状态，用户确认后由 {@link AssetManager} 在后台线程下载。
 * 关闭界面不会中断下载，用户也可以在下载中显式取消。
 */
public class AssetDownloadScreen extends EpsilonDialogScreen {

    private static final float ROW_HEIGHT = 20.0f;
    private static final float STATUS_BLOCK_HEIGHT = 30.0f;

    private final List<AssetManager.Asset> assets;
    private final boolean declineOnClose;

    private boolean downloadRequested;

    public AssetDownloadScreen(Screen parent, Collection<AssetManager.Asset> assets, boolean declineOnClose) {
        super(parent, Component.literal("Assets"));
        List<AssetManager.Asset> ordered = new ArrayList<>();
        for (AssetManager.Asset asset : AssetManager.Asset.values()) {
            if (assets.contains(asset)) {
                ordered.add(asset);
            }
        }
        this.assets = List.copyOf(ordered);
        this.declineOnClose = declineOnClose;
    }

    @Override
    protected String titleText() {
        return EpsilonTranslations.Resources.TITLE.getTranslatedName();
    }

    @Override
    protected float cardWidth() {
        return 350.0f;
    }

    @Override
    protected float cardHeight() {
        return CARD_PADDING * 2.0f + 36.0f + Math.max(1, assets.size()) * ROW_HEIGHT
                + STATUS_BLOCK_HEIGHT + BUTTON_HEIGHT + 10.0f;
    }

    @Override
    protected void buildBody(UiTree.Scope scope, float contentTop, int mouseX, int mouseY) {
        AssetManager manager = AssetManager.INSTANCE;
        AssetManager.DownloadJob job = manager.activeJob();
        boolean running = job != null && job.isRunning();

        float y = contentTop;
        for (AssetManager.Asset asset : assets) {
            boolean ready = manager.isReady(asset);
            boolean downloading = running && job.currentAsset() == asset;

            scope.text(label(asset), CARD_PADDING, y, BODY_SCALE, ready ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);

            String size = formatSize(asset.expectedBytes());
            String status = downloading
                    ? String.format(Locale.ROOT, "%s %d%%", EpsilonTranslations.Resources.STATUS_DOWNLOADING.getTranslatedName(),
                    Math.round(job.currentProgress() * 100.0f))
                    : (ready ? EpsilonTranslations.Resources.STATUS_READY.getTranslatedName()
                    : EpsilonTranslations.Resources.STATUS_MISSING.getTranslatedName());
            String trailing = size + "  ·  " + status;
            float trailingWidth = textMetrics().getWidth(trailing, BODY_SCALE);
            scope.text(trailing, cardBounds().width() - CARD_PADDING - trailingWidth, y, BODY_SCALE,
                    ready ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_SECONDARY);
            y += ROW_HEIGHT;
        }

        y += 6.0f;
        if (running) {
            float barWidth = cardBounds().width() - CARD_PADDING * 2.0f;
            scope.roundRect(CARD_PADDING, y, barWidth, 6.0f, 3.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST);
            float filled = Math.max(2.0f, barWidth * job.progress());
            scope.roundRect(CARD_PADDING, y, filled, 6.0f, 3.0f, MD3Theme.PRIMARY_CONTAINER);
            y += 12.0f;
            AssetManager.Asset current = job.currentAsset();
            String line = current == null ? EpsilonTranslations.Resources.DOWNLOADING.getTranslatedName()
                    : String.format(Locale.ROOT, "%s %s %d%%",
                    EpsilonTranslations.Resources.DOWNLOADING.getTranslatedName(),
                    label(current), Math.round(job.currentProgress() * 100.0f));
            scope.text(line, CARD_PADDING, y, BODY_SCALE, MD3Theme.TEXT_SECONDARY);
            return;
        }

        if (job != null && job.status() == AssetManager.DownloadJob.Status.FAILED) {
            String error = String.format(EpsilonTranslations.Resources.FAILED.getTranslatedName(), job.error());
            scope.text(error, CARD_PADDING, y, BODY_SCALE, MD3Theme.ERROR);
            return;
        }

        boolean allReady = assets.stream().allMatch(AssetManager.INSTANCE::isReady);
        scope.text(allReady ? EpsilonTranslations.Resources.DOWNLOAD_SUCCESS_MESSAGE.getTranslatedName()
                        : EpsilonTranslations.Resources.HINT.getTranslatedName(),
                CARD_PADDING, y, BODY_SCALE, MD3Theme.TEXT_MUTED);
    }

    @Override
    protected List<DialogButton> buttons() {
        AssetManager manager = AssetManager.INSTANCE;
        AssetManager.DownloadJob job = manager.activeJob();
        if (job != null && job.isRunning()) {
            return List.of(
                    new DialogButton(EpsilonTranslations.Resources.BUTTON_STOP.getTranslatedName(), false, job::cancel),
                    new DialogButton(EpsilonTranslations.Resources.BUTTON_BACKGROUND.getTranslatedName(), true, this::onClose)
            );
        }

        EnumSet<AssetManager.Asset> missing = EnumSet.noneOf(AssetManager.Asset.class);
        for (AssetManager.Asset asset : assets) {
            if (!manager.isReady(asset)) {
                missing.add(asset);
            }
        }
        if (missing.isEmpty()) {
            return List.of(new DialogButton(EpsilonTranslations.Resources.BUTTON_DONE.getTranslatedName(), true, this::onClose));
        }

        boolean failed = job != null && job.status() == AssetManager.DownloadJob.Status.FAILED;
        String primaryLabel = failed ? EpsilonTranslations.Resources.BUTTON_RETRY.getTranslatedName()
                : EpsilonTranslations.Resources.BUTTON_DOWNLOAD.getTranslatedName();
        String secondaryLabel = failed ? EpsilonTranslations.Resources.BUTTON_CLOSE.getTranslatedName()
                : EpsilonTranslations.Resources.BUTTON_CANCEL.getTranslatedName();
        return List.of(
                new DialogButton(secondaryLabel, false, () -> {
                    manager.markDeclined(missing);
                    onClose();
                }),
                new DialogButton(primaryLabel, true, () -> {
                    downloadRequested = true;
                    manager.startDownload(missing);
                })
        );
    }

    @Override
    protected void onDialogClosed() {
        if (declineOnClose && !downloadRequested) {
            AssetManager.INSTANCE.markDeclined(assets);
        }
    }

    private static String label(AssetManager.Asset asset) {
        return switch (asset) {
            case VIDEO -> EpsilonTranslations.Resources.VIDEO.getTranslatedName();
            case LIGHT_TRAILS -> EpsilonTranslations.Resources.LIGHT_TRAILS.getTranslatedName();
            case REISA -> EpsilonTranslations.Resources.REISA.getTranslatedName();
            case FFMPEG -> EpsilonTranslations.Resources.FFMPEG.getTranslatedName();
        };
    }

    private static String formatSize(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

}
