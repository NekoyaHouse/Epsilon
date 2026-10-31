package com.github.epsilon.gui.screen

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.AssetManager
import kotlin.math.roundToInt
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.EnumSet
import java.util.Locale

/**
 * 运行时资源下载界面。
 *
 * 展示每个资源的大小与状态，用户确认后由 [AssetManager] 在后台线程下载。
 * 关闭界面不会中断下载，用户也可以在下载中显式取消。
 */
class AssetDownloadScreen(
    parent: Screen?,
    assets: Collection<AssetManager.Asset>,
    private val declineOnClose: Boolean,
) : EpsilonDialogScreen(parent, Component.literal("Assets")) {

    private val assets: List<AssetManager.Asset>

    private var downloadRequested: Boolean = false

    init {
        val ordered: MutableList<AssetManager.Asset> = ArrayList()
        for (asset in AssetManager.Asset.values()) {
            if (assets.contains(asset)) {
                ordered.add(asset)
            }
        }
        this.assets = ordered.toList()
    }

    override fun titleText(): String {
        return EpsilonTranslations.Resources.TITLE.getTranslatedName()
    }

    override fun cardWidth(): Float {
        return 350.0f
    }

    override fun cardHeight(): Float {
        return EpsilonDialogScreen.CARD_PADDING * 2.0f + 36.0f + maxOf(1, assets.size) * ROW_HEIGHT +
                STATUS_BLOCK_HEIGHT + EpsilonDialogScreen.BUTTON_HEIGHT + 10.0f
    }

    override fun buildBody(scope: UiTree.Scope, contentTop: Float, mouseX: Int, mouseY: Int) {
        val manager = AssetManager
        val job = manager.activeJob()
        val running = job != null && job.isRunning()

        var y = contentTop
        for (asset in assets) {
            val ready = manager.isReady(asset)
            val downloading = running && job!!.currentAsset() == asset

            scope.text(
                label(asset), EpsilonDialogScreen.CARD_PADDING, y, EpsilonDialogScreen.BODY_SCALE,
                if (ready) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_SECONDARY
            )

            val size = formatSize(asset.expectedBytes())
            val status = if (downloading) {
                String.format(
                    Locale.ROOT, "%s %d%%", EpsilonTranslations.Resources.STATUS_DOWNLOADING.getTranslatedName(),
                    (job!!.currentProgress() * 100.0f).roundToInt()
                )
            } else {
                if (ready) EpsilonTranslations.Resources.STATUS_READY.getTranslatedName()
                else EpsilonTranslations.Resources.STATUS_MISSING.getTranslatedName()
            }
            val trailing = "$size  ·  $status"
            val trailingWidth = textMetrics().getWidth(trailing, EpsilonDialogScreen.BODY_SCALE)
            scope.text(
                trailing, cardBounds().width - EpsilonDialogScreen.CARD_PADDING - trailingWidth, y,
                EpsilonDialogScreen.BODY_SCALE,
                if (ready) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_SECONDARY
            )
            y += ROW_HEIGHT
        }

        y += 6.0f
        if (running) {
            val barWidth = cardBounds().width - EpsilonDialogScreen.CARD_PADDING * 2.0f
            scope.roundRect(EpsilonDialogScreen.CARD_PADDING, y, barWidth, 6.0f, 3.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST)
            val filled = maxOf(2.0f, barWidth * job!!.progress())
            scope.roundRect(EpsilonDialogScreen.CARD_PADDING, y, filled, 6.0f, 3.0f, MD3Theme.PRIMARY_CONTAINER)
            y += 12.0f
            val current = job!!.currentAsset()
            val line = if (current == null) {
                EpsilonTranslations.Resources.DOWNLOADING.getTranslatedName()
            } else {
                String.format(
                    Locale.ROOT, "%s %s %d%%",
                    EpsilonTranslations.Resources.DOWNLOADING.getTranslatedName(),
                    label(current), (job!!.currentProgress() * 100.0f).roundToInt()
                )
            }
            scope.text(line, EpsilonDialogScreen.CARD_PADDING, y, EpsilonDialogScreen.BODY_SCALE, MD3Theme.TEXT_SECONDARY)
            return
        }

        if (job != null && job.status() == AssetManager.DownloadJob.Status.FAILED) {
            val error = String.format(EpsilonTranslations.Resources.FAILED.getTranslatedName(), job.error())
            scope.text(error, EpsilonDialogScreen.CARD_PADDING, y, EpsilonDialogScreen.BODY_SCALE, MD3Theme.ERROR)
            return
        }

        val allReady = assets.all { asset -> AssetManager.isReady(asset) }
        scope.text(
            if (allReady) EpsilonTranslations.Resources.DOWNLOAD_SUCCESS_MESSAGE.getTranslatedName()
            else EpsilonTranslations.Resources.HINT.getTranslatedName(),
            EpsilonDialogScreen.CARD_PADDING, y, EpsilonDialogScreen.BODY_SCALE, MD3Theme.TEXT_MUTED
        )
    }

    override fun buttons(): List<DialogButton> {
        val manager = AssetManager
        val job = manager.activeJob()
        if (job != null && job.isRunning()) {
            return listOf(
                DialogButton(EpsilonTranslations.Resources.BUTTON_STOP.getTranslatedName(), false, Runnable { job.cancel() }),
                DialogButton(EpsilonTranslations.Resources.BUTTON_BACKGROUND.getTranslatedName(), true, Runnable { onClose() })
            )
        }

        val missing: EnumSet<AssetManager.Asset> = EnumSet.noneOf(AssetManager.Asset::class.java)
        for (asset in assets) {
            if (!manager.isReady(asset)) {
                missing.add(asset)
            }
        }
        if (missing.isEmpty()) {
            return listOf(
                DialogButton(EpsilonTranslations.Resources.BUTTON_DONE.getTranslatedName(), true, Runnable { onClose() })
            )
        }

        val failed = job != null && job.status() == AssetManager.DownloadJob.Status.FAILED
        val primaryLabel = if (failed) EpsilonTranslations.Resources.BUTTON_RETRY.getTranslatedName()
        else EpsilonTranslations.Resources.BUTTON_DOWNLOAD.getTranslatedName()
        val secondaryLabel = if (failed) EpsilonTranslations.Resources.BUTTON_CLOSE.getTranslatedName()
        else EpsilonTranslations.Resources.BUTTON_CANCEL.getTranslatedName()
        return listOf(
            DialogButton(
                secondaryLabel, false, Runnable {
                    manager.markDeclined(missing)
                    onClose()
                }
            ),
            DialogButton(
                primaryLabel, true, Runnable {
                    downloadRequested = true
                    manager.startDownload(missing)
                }
            )
        )
    }

    override fun onDialogClosed() {
        if (declineOnClose && !downloadRequested) {
            AssetManager.markDeclined(assets)
        }
    }

    private companion object {

        private const val ROW_HEIGHT = 20.0f
        private const val STATUS_BLOCK_HEIGHT = 30.0f

        private fun label(asset: AssetManager.Asset): String {
            return when (asset) {
                AssetManager.Asset.VIDEO -> EpsilonTranslations.Resources.VIDEO.getTranslatedName()
                AssetManager.Asset.LIGHT_TRAILS -> EpsilonTranslations.Resources.LIGHT_TRAILS.getTranslatedName()
                AssetManager.Asset.REISA -> EpsilonTranslations.Resources.REISA.getTranslatedName()
                AssetManager.Asset.FFMPEG -> EpsilonTranslations.Resources.FFMPEG.getTranslatedName()
            }
        }

        private fun formatSize(bytes: Long): String {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
        }

    }

}
