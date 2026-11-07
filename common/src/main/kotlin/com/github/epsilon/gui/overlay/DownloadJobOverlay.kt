package com.github.epsilon.gui.overlay

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.screen.EpsilonDialogScreen
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.managers.AssetManager
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.roundToInt

object DownloadJobOverlay : AutoCloseable {

    private const val MARGIN = 10.0f
    private const val WIDTH = 244.0f
    private const val HEIGHT = 82.0f
    private const val PADDING = 12.0f

    private var scene: UiScene? = null
    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var bounds: UiRect? = null
    private var renderedScreen: Screen? = null
    private var renderedJob: AssetManager.DownloadJob? = null

    fun extractRenderState(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val minecraft = mc
        val job = AssetManager.activeJob()
        if (screen != minecraft.gui.screen() || screen is EpsilonDialogScreen || job == null || !job.isRunning()) {
            close()
            return
        }

        val width = LuminRenderSystem.getScaledWidth()
        val height = LuminRenderSystem.getScaledHeight()
        val cardWidth = minOf(WIDTH, width - MARGIN * 2.0f)
        if (cardWidth < 120.0f || height < HEIGHT + MARGIN * 2.0f) {
            close()
            return
        }
        val cardBounds = UiRect(width - MARGIN - cardWidth, height - MARGIN - HEIGHT, cardWidth, HEIGHT)
        bounds = cardBounds
        val hovered = cardBounds.contains(
            LuminRenderSystem.toEpsilonMouseX(mouseX).toDouble(),
            LuminRenderSystem.toEpsilonMouseY(mouseY).toDouble()
        )
        MD3Theme.syncFromSettings()

        val window = minecraft.window
        if (scene == null) scene = UiScene(EpsilonUiTheme)
        if (renderTarget == null) {
            renderTarget =
                LuminRenderSystem.LuminRenderTarget.create("download-job-overlay", window.width, window.height)
        }
        val target = renderTarget!!
        target.resize(window.width, window.height)
        target.clear()
        val previousTarget = LuminRenderSystem.getActiveTarget()
        LuminRenderSystem.setActiveTarget(target)
        try {
            scene!!.beginFrame()
            drawCard(job, hovered)
            scene!!.endFrame()
        } finally {
            LuminRenderSystem.setActiveTarget(previousTarget)
        }
        graphics.nextStratum()
        graphics.blit(
            target.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0.0f, 1.0f, 1.0f, 0.0f
        )
        renderedScreen = screen
        renderedJob = job
    }

    /**
     * 绘制下载卡片。
     *
     * 作用域原点就是 [cardBounds] 左上角，所以卡片内部的坐标全部相对卡片本身。
     */
    private fun drawCard(job: AssetManager.DownloadJob, hovered: Boolean) {
        val cardBounds = bounds ?: return
        val current = job.currentAsset()
        val progress = job.progress()
        val percent = (progress * 100.0f).roundToInt().toString() + "%"
        val currentPercent = if (current == null) "" else (job.currentProgress() * 100.0f).roundToInt().toString() + "%"
        val title = EpsilonTranslations.Resources.TITLE.getTranslatedName()
        val resource = if (current == null) {
            EpsilonTranslations.Resources.JOB_PREPARING.getTranslatedName()
        } else {
            when (current) {
                AssetManager.Asset.VIDEO -> EpsilonTranslations.Resources.VIDEO.getTranslatedName()
                AssetManager.Asset.LIGHT_TRAILS -> EpsilonTranslations.Resources.LIGHT_TRAILS.getTranslatedName()
                AssetManager.Asset.REISA -> EpsilonTranslations.Resources.REISA.getTranslatedName()
                AssetManager.Asset.FFMPEG -> EpsilonTranslations.Resources.FFMPEG.getTranslatedName()
            }
        }
        val index = if (current == null) 0 else job.assets().indexOf(current) + 1
        val count = index.toString() + " / " + job.assets().size
        val details = EpsilonTranslations.Resources.JOB_DETAILS.getTranslatedName()
        val contentWidth = cardBounds.width - PADDING * 2.0f

        scene!!.LuminUIScene(cardBounds, UiLayer.CONTENT, key = "download", immediate = true) {
            Interaction(job) { onClick { AssetManager.openDownloadScreen(job.assets()); true } }
            val radius = MD3Theme.CARD_RADIUS.toFloat()
            val percentWidth = metrics.textWidth(percent, 0.68f)
            val currentPercentWidth = metrics.textWidth(currentPercent, 0.58f)
            val countWidth = metrics.textWidth(count, 0.54f)
            val detailsScale = fitScale(details, 0.54f, contentWidth - countWidth - 12.0f)
            val detailsWidth = metrics.textWidth(details, detailsScale)

            Shadow(
                0.0f, 0.0f, cardBounds.width, cardBounds.height, radius,
                MD3Theme.POPUP_SHADOW_BLUR, MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.POPUP_SHADOW_ALPHA)
            )
            RoundRect(
                0.0f, 0.0f, cardBounds.width, cardBounds.height, radius,
                if (hovered) MD3Theme.SURFACE_CONTAINER_HIGH else MD3Theme.SURFACE_CONTAINER
            )
            Outline(
                0.0f, 0.0f, cardBounds.width, cardBounds.height, radius,
                0.8f, if (hovered) MD3Theme.PRIMARY else MD3Theme.OUTLINE_SOFT
            )
            Text(
                title,
                PADDING,
                10.0f,
                fitScale(title, 0.68f, contentWidth - percentWidth - 10.0f),
                MD3Theme.TEXT_PRIMARY
            )
            Text(percent, cardBounds.width - PADDING - percentWidth, 10.0f, 0.68f, MD3Theme.PRIMARY)
            RoundRect(PADDING, 31.0f, contentWidth, 4.0f, 2.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST)
            if (progress > 0.0f) {
                RoundRect(PADDING, 31.0f, contentWidth * progress, 4.0f, 2.0f, MD3Theme.PRIMARY)
            }
            Text(
                resource, PADDING, 43.0f, fitScale(resource, 0.58f, contentWidth - currentPercentWidth - 10.0f),
                MD3Theme.TEXT_SECONDARY
            )
            Text(
                currentPercent, cardBounds.width - PADDING - currentPercentWidth, 43.0f, 0.58f,
                MD3Theme.TEXT_SECONDARY
            )
            Text(count, PADDING, 63.0f, 0.54f, MD3Theme.TEXT_MUTED)
            Text(details, cardBounds.width - PADDING - detailsWidth, 63.0f, detailsScale, MD3Theme.PRIMARY)
        }
    }

    private fun UiScope.fitScale(text: String, preferredScale: Float, maxWidth: Float): Float {
        val textWidth = metrics.textWidth(text, preferredScale)
        return if (textWidth > maxWidth && textWidth > 0.0f) {
            preferredScale * maxOf(1.0f, maxWidth) / textWidth
        } else {
            preferredScale
        }
    }

    fun mouseClicked(screen: Screen, event: MouseButtonEvent): Boolean {
        val minecraft = mc
        val cardBounds = bounds
        if (minecraft.gui.screen() != screen || screen != renderedScreen || cardBounds == null ||
            event.button() != InputConstants.MOUSE_BUTTON_LEFT
        ) return false
        val job = AssetManager.activeJob()
        if (job == null || job !== renderedJob || !job.isRunning()) return false
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        return scene?.input?.mouseClicked(epsilonEvent, false) ?: false
    }

    override fun close() {
        bounds = null
        renderedScreen = null
        renderedJob = null
        val target = renderTarget
        if (target != null) {
            target.close()
            renderTarget = null
        }
        val currentScene = scene
        if (currentScene != null) {
            currentScene.close()
            scene = null
        }
    }

}
