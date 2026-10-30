package com.github.epsilon.gui.screen

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.shaders.GlslSandBox
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.graphics.video.VideoPlayer
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.screen.accounts.AccountsScreen
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.AssetManager
import com.github.epsilon.managers.VideoManager
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util
import java.awt.Color

object MainMenuScreen : Screen(Component.literal("MainMenuScreen")) {

    enum class Background {
        SEA_LEVEL,
        CLOUDS,
        ALIEN_TERRAIN,
        INFERNO,
        PLANET,
        BLACK_HOLE,
        MINECRAFT,
    }

    private const val MENU_REFERENCE_WIDTH = 1600.0f
    private const val MENU_REFERENCE_HEIGHT = 900.0f
    private const val COLUMBINA_TITLE_DELAY_MS = 80L
    private const val COLUMBINA_TITLE_DURATION_MS = 680L
    private const val COLUMBINA_MENU_DELAY_MS = 190L
    private const val COLUMBINA_ITEM_STAGGER_MS = 78L
    private const val COLUMBINA_ITEM_DURATION_MS = 560L
    private const val COLUMBINA_ITEM_ENTER_OFFSET = 28.0f
    private const val COLUMBINA_ITEM_ENTER_RISE = 10.0f
    private const val REISA_ASPECT_RATIO = 710.0f / 1280.0f
    private const val REISA_PAGE_SLICES = 12
    private const val REISA_ENTRANCE_DURATION_MS = 900L
    private const val REISA_BUBBLE_DELAY_MS = 620L
    private const val REISA_FALLBACK_VISIBLE_MS = 3_800L
    private const val REISA_EXIT_POSE_DELAY_MS = 60L
    private const val REISA_EXIT_POSE_DURATION_MS = 240L
    private const val REISA_EXIT_MOVE_DELAY_MS = 180L
    private const val REISA_EXIT_MOVE_DURATION_MS = 670L
    private const val REISA_EXIT_BASE_DELAY_MS = 120L
    private const val REISA_EXIT_BASE_DURATION_MS = 500L
    private const val REISA_EXIT_DURATION_MS = 900L

    private const val REISA_SHUTDOWN_ENTRANCE_DELAY_MS = 220L
    private const val REISA_SHUTDOWN_ENTRANCE_DURATION_MS = 760L
    private const val REISA_SHUTDOWN_BUBBLE_DELAY_MS = 1_150L
    private const val REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS = 7_666L
    private const val REISA_SHUTDOWN_EXIT_DURATION_MS = 900L
    private const val REISA_SHUTDOWN_MENU_FADE_DURATION_MS = 2_200L
    private const val REISA_BUBBLE_SHADOW_BLUR = 21.7f
    private const val REISA_SHUTDOWN_BUBBLE_SHADOW_ALPHA = 0.60f
    private const val REISA_GREETING_BUBBLE_SHADOW_ALPHA = 0.58f

    private const val REISA_WELCOME_SUFFIX = "00"
    private const val REISA_EXIT_SUFFIX = "09"
    private const val REISA_SHUTDOWN_ENTRANCE_SUFFIX = "10"
    private const val REISA_SHUTDOWN_FINAL_SUFFIX = "18"

    private val scene = UiScene(EpsilonUiTheme)

    private val entries: MutableList<MenuEntry> = ArrayList()

    private var backgroundRenderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var uiRenderTarget: LuminRenderSystem.LuminRenderTarget? = null

    private var introStartMs: Long = 0L
    private var initialized: Boolean = false
    private var reisaGreetingQueued: Boolean = false
    private var reisaGreetingStartMs: Long = -1L
    private var reisaExitStartMs: Long = -1L
    private var reisaWelcomeSound: SoundInstance? = null
    private var reisaShutdownStartMs: Long = -1L
    private var reisaShutdownExitStartMs: Long = -1L
    private var reisaShutdownSound: SoundInstance? = null
    private var reisaShutdownCommitted: Boolean = false
    private var reisaShutdownTexturesPrewarmed: Boolean = false

    /**
     * 玲纱立绘改为按需下载，纹理标识在渲染线程上惰性注册；未下载时返回 null。
     */
    private fun reisaWelcomeTexture(): Identifier? = AssetManager.INSTANCE.reisaTexture(REISA_WELCOME_SUFFIX)

    private fun reisaExitTexture(): Identifier? = AssetManager.INSTANCE.reisaTexture(REISA_EXIT_SUFFIX)

    private fun reisaShutdownEntranceTexture(): Identifier? =
        AssetManager.INSTANCE.reisaTexture(REISA_SHUTDOWN_ENTRANCE_SUFFIX)

    private fun reisaShutdownFinalTexture(): Identifier? = AssetManager.INSTANCE.reisaTexture(REISA_SHUTDOWN_FINAL_SUFFIX)

    init {
        entries.add(MenuEntry("Singleplayer", Runnable { minecraft?.gui?.setScreen(SelectWorldScreen(this)) }))
        entries.add(
            MenuEntry(
                "Multiplayer", Runnable {
                    val minecraft = minecraft ?: return@Runnable
                    val screen: Screen = if (minecraft.options.skipMultiplayerWarning) {
                        JoinMultiplayerScreen(this)
                    } else {
                        SafetyScreen(this)
                    }
                    minecraft.gui.setScreen(screen)
                }
            )
        )
        entries.add(MenuEntry("Accounts", Runnable { minecraft?.gui?.setScreen(AccountsScreen) }))
        entries.add(MenuEntry("GUI", Runnable { minecraft?.gui?.setScreen(ClientSetting.getGuiScreen()) }))
        entries.add(
            MenuEntry(
                "Options", Runnable {
                    minecraft?.gui?.setScreen(OptionsScreen(this, minecraft!!.options))
                }
            )
        )
        entries.add(
            MenuEntry(
                "Quit", Runnable {
                    if (!requestShutdown()) minecraft?.stop()
                }
            )
        )
    }

    override fun init() {
        super.init()
        val assets = AssetManager.INSTANCE
        val columbinaStyle = ClientSetting.mainMenuStyle.`is`(ClientSetting.MainMenuStyle.Columbina)
        updateBackgroundVideo()
        if (columbinaStyle && assets.isVideoSupported) {
            // 视频叠层光效可以独立缺失，缺失时补提示；已就绪或本会话已拒绝时不会重复弹窗。
            assets.requestDownload(
                listOf(
                    AssetManager.Asset.VIDEO,
                    AssetManager.Asset.LIGHT_TRAILS,
                    AssetManager.Asset.FFMPEG
                )
            )
        }
        requestReisaAssetsIfNeeded()
        if (!initialized) {
            initialized = true
            introStartMs = Util.getMillis()
            GlslSandBox.resetTime()
            for (entry in entries) {
                entry.hoverProgress = 0.0f
                entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f)
            }
        }
        if (reisaGreetingQueued) {
            startReisaGreeting()
        }
    }

    private fun updateBackgroundVideo() {
        val assets = AssetManager.INSTANCE
        val columbinaStyle = ClientSetting.mainMenuStyle.`is`(ClientSetting.MainMenuStyle.Columbina)
        if (!columbinaStyle || !assets.isVideoSupported) {
            if (columbinaStyle) {
                ClientSetting.mainMenuStyle.value = ClientSetting.MainMenuStyle.Classic
            }
            if (!VideoPlayer.isStopped()) {
                VideoPlayer.stop()
            }
            return
        }
        if (!VideoPlayer.isStopped()) {
            VideoPlayer.resume()
            return
        }

        val downloadJob = assets.activeJob()
        if (downloadJob != null && downloadJob.isRunning()) return

        val videoReady = assets.isVideoReady
        val ffmpegReady = assets.isFfmpegReady
        if (videoReady && ffmpegReady) {
            try {
                VideoManager.INSTANCE.loadBackground()
            } catch (exception: Exception) {
                Constants.LOGGER.error("Unable to load the Columbina main-menu video", exception)
            }
            if (VideoPlayer.isStopped()) {
                ClientSetting.mainMenuStyle.value = ClientSetting.MainMenuStyle.Classic
            }
        } else if ((!videoReady && assets.isDeclinedThisSession(AssetManager.Asset.VIDEO)) ||
            (!ffmpegReady && assets.isDeclinedThisSession(AssetManager.Asset.FFMPEG))
        ) {
            ClientSetting.mainMenuStyle.value = ClientSetting.MainMenuStyle.Classic
        }
    }

    fun queueReisaGreeting() {
        if (!ClientSetting.showReisaOnStartup.value || !AssetManager.INSTANCE.isReisaReady) {
            reisaGreetingQueued = false
            return
        }
        reisaGreetingQueued = true
        if (initialized && minecraft?.gui?.screen() === this) {
            startReisaGreeting()
        }
    }

    /**
     * 玲纱立绘缺失时提示下载，仅在启用了启动问候或关机动画时需要。
     */
    private fun requestReisaAssetsIfNeeded() {
        val assets = AssetManager.INSTANCE
        if (assets.isReisaReady) {
            return
        }
        val needed = ClientSetting.showReisaOnStartup.value || ClientSetting.showReisaOnShutdown.value
        if (needed) {
            assets.requestDownload(listOf(AssetManager.Asset.REISA))
        }
    }

    private fun startReisaGreeting() {
        if (!ClientSetting.showReisaOnStartup.value || !AssetManager.INSTANCE.isReisaReady) {
            clearReisaGreeting()
            return
        }
        reisaGreetingQueued = false
        reisaGreetingStartMs = Util.getMillis()
        reisaExitStartMs = -1L
        reisaWelcomeSound = SoundManager.INSTANCE.playTracked(
            SoundKey.REISA_WELCOME,
            ClientSetting.reisaVolume.value.toFloat()
        ).orElse(null)
    }

    fun requestShutdown(): Boolean {
        if (!ClientSetting.showReisaOnShutdown.value || !AssetManager.INSTANCE.isReisaReady) return false
        val minecraft = this.minecraft ?: return false
        if (!initialized || minecraft.gui.screen() !== this || reisaShutdownCommitted) return false
        if (reisaShutdownStartMs >= 0L) return true

        reisaWelcomeSound?.let { minecraft.soundManager.stop(it) }
        clearReisaGreeting()
        minecraft.soundManager.stop()
        reisaShutdownStartMs = Util.getMillis()
        reisaShutdownExitStartMs = -1L
        reisaShutdownSound = SoundManager.INSTANCE.playTracked(
            SoundKey.REISA_BYE,
            ClientSetting.reisaVolume.value.toFloat()
        ).orElse(null)
        return true
    }

    override fun tick() {
        super.tick()
        val minecraft = this.minecraft ?: return
        if (reisaShutdownStartMs >= 0L) {
            val now = Util.getMillis()
            if (reisaShutdownExitStartMs < 0L) {
                val soundFinished = reisaShutdownSound != null && !minecraft.soundManager.isActive(reisaShutdownSound!!)
                val fallbackFinished = reisaShutdownSound == null &&
                        now - reisaShutdownStartMs >= REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS
                if (soundFinished || fallbackFinished) {
                    reisaShutdownSound = null
                    reisaShutdownExitStartMs = now
                }
                return
            }

            if (now - reisaShutdownExitStartMs >= REISA_SHUTDOWN_EXIT_DURATION_MS) {
                reisaShutdownCommitted = true
                minecraft.stop()
            }
            return
        }
        updateBackgroundVideo()
        if (reisaGreetingStartMs < 0L) return

        val now = Util.getMillis()
        if (reisaExitStartMs >= 0L) {
            if (now - reisaExitStartMs >= REISA_EXIT_DURATION_MS) {
                clearReisaGreeting()
            }
            return
        }

        val welcomeFinished = reisaWelcomeSound != null && !minecraft.soundManager.isActive(reisaWelcomeSound!!)
        val fallbackFinished = reisaWelcomeSound == null && now - reisaGreetingStartMs >= REISA_FALLBACK_VISIBLE_MS
        if (welcomeFinished || fallbackFinished) {
            reisaWelcomeSound = null
            reisaExitStartMs = now
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val minecraft = this.minecraft ?: return
        val window = minecraft.window
        if (backgroundRenderTarget == null) {
            backgroundRenderTarget =
                LuminRenderSystem.LuminRenderTarget.create("main-menu-background", window.width, window.height)
        }

        backgroundRenderTarget!!.resize(window.width, window.height)
        backgroundRenderTarget!!.clear()
        LuminRenderSystem.setActiveTarget(backgroundRenderTarget)

        if (ClientSetting.mainMenuStyle.`is`(ClientSetting.MainMenuStyle.Columbina) && drawColumbinaBackground()) {
            LuminRenderSystem.setActiveTarget(null)
            graphics.blit(backgroundRenderTarget!!.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0, 1, 1, 0)
        } else {
            val background = when (ClientSetting.mainMenuBackground.value) {
                Background.SEA_LEVEL -> GlslSandBox.SEA_LEVEL
                Background.CLOUDS -> GlslSandBox.CLOUDS
                Background.ALIEN_TERRAIN -> GlslSandBox.ALIEN_TERRAIN
                Background.INFERNO -> GlslSandBox.INFERNO
                Background.PLANET -> GlslSandBox.PLANET
                Background.BLACK_HOLE -> GlslSandBox.BLACK_HOLE
                Background.MINECRAFT -> GlslSandBox.MINECRAFT
            }

            GlslSandBox.render(
                background,
                LuminRenderSystem.toEpsilonMouseX(mouseX).toDouble(),
                LuminRenderSystem.toEpsilonMouseY(mouseY).toDouble()
            )

            LuminRenderSystem.setActiveTarget(null)
            graphics.blit(backgroundRenderTarget!!.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0, 1, 1, 0)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val minecraft = this.minecraft ?: return
        val window = minecraft.window
        if (uiRenderTarget == null) {
            uiRenderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu-ui", window.width, window.height)
        }

        uiRenderTarget!!.resize(window.width, window.height)
        uiRenderTarget!!.clear()
        LuminRenderSystem.setActiveTarget(uiRenderTarget)

        if (ClientSetting.mainMenuStyle.`is`(ClientSetting.MainMenuStyle.Columbina)) {
            drawColumbinaMenu(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY))
        } else {
            scene.beginFrame()
            drawClassicMenu(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY))
            scene.endFrame()
        }

        LuminRenderSystem.setActiveTarget(null)
        graphics.blit(uiRenderTarget!!.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0, 1, 1, 0)
    }

    private fun drawColumbinaBackground(): Boolean {
        val texture = VideoPlayer.getGuiTexture() ?: return false
        val textureWidth = VideoPlayer.getGuiTextureWidth()
        val textureHeight = VideoPlayer.getGuiTextureHeight()
        if (textureWidth <= 0 || textureHeight <= 0) {
            return false
        }

        val width = LuminRenderSystem.getScaledWidthInt()
        val height = LuminRenderSystem.getScaledHeightInt()
        val sourceAspect = textureWidth / textureHeight.toFloat()
        val targetAspect = width / Math.max(1, height).toFloat()
        var u0 = 0.0f
        var u1 = 1.0f
        var v0 = 0.0f
        var v1 = 1.0f
        if (sourceAspect > targetAspect) {
            val visible = targetAspect / sourceAspect
            u0 = (1.0f - visible) * 0.5f
            u1 = u0 + visible
        } else {
            val visible = sourceAspect / targetAspect
            v0 = (1.0f - visible) * 0.5f
            v1 = v0 + visible
        }

        val finalU0 = u0
        val finalU1 = u1
        val finalV0 = v0
        val finalV1 = v1
        val lightTrails = AssetManager.INSTANCE.videoOverlayTexture()
        val backgroundTree = UiTree.build { scope ->
            scope.layer(0) { layer ->
                layer.texture(
                    texture, 0.0f, 0.0f, width.toFloat(), height.toFloat(),
                    finalU0, finalV0, finalU1, finalV1, Color.WHITE, true
                )
                if (lightTrails != null) {
                    layer.texture(lightTrails, 0.0f, 0.0f, width.toFloat(), height.toFloat(), 0.0f, 0.0f, 1.0f, 1.0f, Color.WHITE, true)
                }
            }
            scope.layer(1) { layer ->
                layer.rectHorizontalGradient(
                    width * 0.42f, 0.0f, width * 0.58f, height.toFloat(),
                    Color(14, 20, 45, 0), Color(7, 11, 30, 150)
                )
                layer.rectVerticalGradient(0.0f, 0.0f, width.toFloat(), height.toFloat(), Color(7, 10, 25, 20), Color(4, 7, 18, 88))
            }
        }
        scene.beginFrame()
        scene.submit(UiLayer.CONTENT, backgroundTree)
        scene.endFrame()
        return true
    }

    private fun drawColumbinaMenu(mouseX: Int, mouseY: Int) {
        val clientName = Constants.NAME.uppercase()
        val now = Util.getMillis()
        val width = LuminRenderSystem.getScaledWidthInt()
        val height = LuminRenderSystem.getScaledHeightInt()
        val scale = resolutionScale(width, height)
        val layoutWidth = Math.round(MENU_REFERENCE_WIDTH * scale)
        val layoutHeight = Math.round(MENU_REFERENCE_HEIGHT * scale)
        val layoutX = width - layoutWidth
        val layoutY = height - layoutHeight
        val layoutMouseX = mouseX - layoutX
        val layoutMouseY = mouseY - layoutY
        val menuCenterCorrectionX = (width - layoutWidth) * 0.2f
        val introElapsed = Math.max(0L, now - introStartMs)
        val titleProgress = Easing.EASE_OUT_CUBIC.function.apply(
            Mth.clamp((introElapsed - COLUMBINA_TITLE_DELAY_MS) / COLUMBINA_TITLE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        )
        val subtitleProgress = Easing.EASE_OUT_CUBIC.function.apply(
            Mth.clamp(
                (introElapsed - COLUMBINA_TITLE_DELAY_MS - 70L) / (COLUMBINA_TITLE_DURATION_MS - 70L).toFloat(), 0.0f, 1.0f
            )
        )
        val shutdownElapsed = if (reisaShutdownStartMs < 0L) 0.0f else (now - reisaShutdownStartMs).toFloat()
        val exit = Easing.EASE_IN_OUT_CUBIC.function.apply(
            Mth.clamp(shutdownElapsed / REISA_SHUTDOWN_MENU_FADE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        )
        val menuVisibility = 1.0f - exit
        val titleVisibility = titleProgress * menuVisibility
        val subtitleVisibility = subtitleProgress * menuVisibility

        val rightMargin = Math.max(46.5f * scale, layoutWidth * 0.055f)
        val menuWidth = Mth.clamp(layoutWidth * 0.25f, 294.5f * scale, 418.5f * scale)
        val restingMenuX = layoutWidth - rightMargin - menuWidth - menuCenterCorrectionX
        val menuX = restingMenuX + exit * 34.1f * scale
        val titleCenterX = restingMenuX + menuWidth * 0.53f
        val titleScale = 8.6025f * scale
        val metrics = scene.scheduler().textMetrics()
        val titleWidth = metrics.getWidth(clientName, titleScale, StaticFontLoader.CINZEL_DECORATIVE)
        val titleHeight = metrics.getHeight(titleScale, StaticFontLoader.CINZEL_DECORATIVE)
        val titleX = titleCenterX - titleWidth * 0.5f + (1.0f - titleProgress) * 18.6f * scale + exit * 27.9f * scale
        val titleBaseY = Math.max(37.2f * scale, layoutHeight * 0.095f)
        val titleY = titleBaseY + (1.0f - titleProgress) * 4.65f * scale
        val subtitleScale = 1.705f * scale
        val subtitle = "COLUMBINA / " + Constants.VERSION
        val subtitleWidth = metrics.getWidth(subtitle, subtitleScale)
        val subtitleX = titleCenterX - subtitleWidth * 0.5f + (1.0f - subtitleProgress) * 18.6f * scale + exit * 27.9f * scale
        val subtitleBaseY = titleBaseY + titleHeight - 6.2f * scale
        val subtitleY = subtitleBaseY + (1.0f - subtitleProgress) * 4.65f * scale
        val menuTop = Math.max(layoutHeight * 0.315f, subtitleBaseY + 52.7f * scale)
        val footerReserve = 52.7f * scale
        val availableRows = Math.max(1.0f, layoutHeight - menuTop - footerReserve)
        val rowStride = Math.min(80.6f * scale, availableRows / entries.size)
        val rowHeight = Math.min(58.9f * scale, rowStride * 0.78f)

        scene.beginFrame()
        val tree = UiTree.build { scope ->
            scope.pushAbsolute(layoutX, layoutY) { content ->
                if (AssetManager.INSTANCE.isReisaReady) {
                    if (!reisaShutdownTexturesPrewarmed) {
                        prewarmReisaShutdownTextures(content)
                        reisaShutdownTexturesPrewarmed = true
                    }
                    if (reisaShutdownStartMs >= 0L) {
                        drawReisaShutdown(content, layoutWidth, layoutHeight, scale, now, -layoutX.toFloat(), -layoutY.toFloat(), width, height)
                    } else {
                        drawReisaGreeting(content, layoutWidth, layoutHeight, scale)
                    }
                }

                val titleStart = applyAlpha(Color(158, 181, 222), titleVisibility * 0.98f)
                val titleEnd = applyAlpha(Color(242, 247, 255), titleVisibility)
                val titleOuterGlow = applyAlpha(Color(112, 158, 235), titleVisibility * 0.66f)
                val titleInnerGlow = applyAlpha(Color(205, 226, 255), titleVisibility * 0.90f)
                val subtitleStart = applyAlpha(Color(180, 198, 226), subtitleVisibility * 0.92f)
                val subtitleEnd = applyAlpha(Color(224, 233, 246), subtitleVisibility * 0.96f)
                val subtitleGlow = applyAlpha(Color(184, 207, 239), subtitleVisibility * 0.52f)
                val secondary = applyAlpha(Color(178, 197, 224), subtitleVisibility * 0.88f)

                content.layer(-50) { layer ->
                    layer.blurredText(
                        clientName, titleX, titleY, titleScale, titleOuterGlow, titleScale * 2.0f, 3,
                        StaticFontLoader.CINZEL_DECORATIVE
                    )
                    layer.blurredText(
                        clientName, titleX, titleY, titleScale, titleInnerGlow, titleScale * 0.90f, 4,
                        StaticFontLoader.CINZEL_DECORATIVE
                    )
                    layer.gradientText(clientName, titleX, titleY, titleScale, titleStart, titleEnd, StaticFontLoader.CINZEL_DECORATIVE)
                    layer.blurredText(subtitle, subtitleX, subtitleY, subtitleScale, subtitleScale * 1.25f, 2, subtitleGlow)
                    layer.gradientText(subtitle, subtitleX, subtitleY, subtitleScale, subtitleStart, subtitleEnd)
                }

                for (index in entries.indices) {
                    val entry = entries[index]
                    val itemDelay = COLUMBINA_MENU_DELAY_MS + index * COLUMBINA_ITEM_STAGGER_MS
                    val itemProgress = Easing.EASE_OUT_CUBIC.function.apply(
                        Mth.clamp((introElapsed - itemDelay) / COLUMBINA_ITEM_DURATION_MS.toFloat(), 0.0f, 1.0f)
                    )
                    val itemVisibility = itemProgress * menuVisibility
                    val rowY = menuTop + index * rowStride + (1.0f - itemProgress) * COLUMBINA_ITEM_ENTER_RISE * scale
                    val offsets = floatArrayOf(-31.0f, 57.35f, 90.0f, 80.0f, 48.0f, -15.0f)
                    val rowX = menuX + menuWidth * 0.20f + offsets[index] * scale +
                            (1.0f - itemProgress) * COLUMBINA_ITEM_ENTER_OFFSET * scale
                    if (itemProgress <= 0.001f || menuVisibility <= 0.001f) {
                        entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f)
                    } else {
                        entry.setBounds(
                            rowX - 12.4f * scale, rowY - 6.2f * scale, menuWidth * 0.78f, rowHeight + 12.4f * scale
                        )
                    }
                    val hovered = entry.isHovered(layoutMouseX.toDouble(), layoutMouseY.toDouble())
                    entry.hoverProgress = Mth.lerp(if (hovered) 0.26f else 0.16f, entry.hoverProgress, if (hovered) 1.0f else 0.0f)
                    val hover = entry.hoverProgress
                    val animatedRowX = rowX + hover * 7.75f * scale
                    val labelStart = MD3Theme.lerp(
                        applyAlpha(Color(226, 234, 247), itemVisibility * 0.96f),
                        applyAlpha(Color(243, 247, 255), itemVisibility), hover
                    )
                    val labelEnd = MD3Theme.lerp(
                        applyAlpha(Color(177, 199, 232), itemVisibility * 0.94f),
                        applyAlpha(Color(213, 226, 246), itemVisibility * 0.98f), hover
                    )
                    val numberStart = MD3Theme.lerp(
                        applyAlpha(Color(202, 216, 238), itemVisibility * 0.88f),
                        applyAlpha(Color(232, 240, 252), itemVisibility * 0.96f), hover
                    )
                    val numberEnd = MD3Theme.lerp(
                        applyAlpha(Color(151, 177, 216), itemVisibility * 0.86f),
                        applyAlpha(Color(195, 216, 242), itemVisibility * 0.94f), hover
                    )
                    val labelGlow = MD3Theme.lerp(
                        applyAlpha(Color(163, 196, 241), itemVisibility * 0.50f),
                        applyAlpha(Color(205, 225, 252), itemVisibility * 0.76f), hover
                    )
                    val numberGlow = MD3Theme.lerp(
                        applyAlpha(Color(146, 181, 231), itemVisibility * 0.34f),
                        applyAlpha(Color(191, 215, 247), itemVisibility * 0.56f), hover
                    )
                    val numberShadow = applyAlpha(Color(3, 7, 20), itemVisibility * (0.30f + hover * 0.06f))
                    val labelScale = (2.976f + hover * 0.124f) * scale
                    val textHeight = metrics.getHeight(labelScale)
                    val textY = rowY + (rowHeight - textHeight) * 0.5f
                    val numberScale = (1.829f + hover * 0.0775f) * scale
                    val numberHeight = metrics.getHeight(numberScale)
                    val numberY = rowY + (rowHeight - numberHeight) * 0.5f
                    val numberShadowBlur = 4.65f * scale

                    val numberLabel = "0" + (index + 1)
                    val labelText = localizedTitle(entry.title)
                    val labelX = animatedRowX + 46.5f * scale
                    val labelWidth = metrics.getWidth(labelText, labelScale)
                    content.layer(-50) { layer ->
                        layer.blurredText(numberLabel, animatedRowX, numberY + 1.55f * scale, numberScale, numberShadowBlur, 1, numberShadow)
                        layer.blurredText(numberLabel, animatedRowX, numberY, numberScale, numberScale * 0.85f, 1, numberGlow)
                        layer.gradientText(numberLabel, animatedRowX, numberY, numberScale, numberStart, numberEnd)
                        layer.blurredText(labelText, labelX, textY, labelScale, labelScale * 1.35f, 1, labelGlow)
                        layer.gradientText(labelText, labelX, textY, labelScale, labelStart, labelEnd)
                        if (hover > 0.001f) {
                            layer.roundRect(
                                labelX, rowY + rowHeight - 2.3f * scale,
                                labelWidth * (0.18f + hover * 0.28f), Math.max(1.0f, 1.55f * scale),
                                Math.max(0.8f, 0.78f * scale),
                                applyAlpha(Color(210, 229, 255), itemVisibility * hover * 0.78f)
                            )
                        }
                    }
                }

                val footerY = layoutHeight - Math.max(27.9f * scale, layoutHeight * 0.045f)
                val user = this.minecraft!!.user.name
                content.layer(-50) { layer ->
                    val userWidth = metrics.getWidth(user, 1.55f * scale)
                    val userX = layoutWidth - rightMargin - userWidth + 50.0f * scale - menuCenterCorrectionX
                    layer.blurredText(
                        user, userX, footerY - 6.2f * scale, 1.55f * scale, 3.1f * scale, 2,
                        applyAlpha(secondary, 0.42f)
                    )
                    layer.text(user, userX, footerY - 6.2f * scale, 1.55f * scale, secondary)
                }
            }
        }
        scene.submit(UiLayer.CONTENT, tree)
        scene.endFrame()
    }

    private fun drawClassicMenu(mouseX: Int, mouseY: Int) {
        val now = Util.getMillis()
        val introProgress = Easing.EASE_OUT_CUBIC.function.apply(Mth.clamp((now - introStartMs) / 650.0f, 0.0f, 1.0f))
        val width = LuminRenderSystem.getScaledWidthInt()
        val height = LuminRenderSystem.getScaledHeightInt()
        val shutdownElapsed = if (reisaShutdownStartMs < 0L) 0.0f else (now - reisaShutdownStartMs).toFloat()
        val menuVisibility = 1.0f - Easing.EASE_OUT_CUBIC.function.apply(
            Mth.clamp(shutdownElapsed / REISA_SHUTDOWN_MENU_FADE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        )
        val scale = resolutionScale(width, height)
        val layoutWidth = Math.round(MENU_REFERENCE_WIDTH * scale)
        val layoutHeight = Math.round(MENU_REFERENCE_HEIGHT * scale)
        val layoutX = (width - layoutWidth) * 0.5f
        val layoutY = (height - layoutHeight) * 0.5f
        val layoutMouseX = mouseX - layoutX
        val layoutMouseY = mouseY - layoutY

        // LOGO 仍以左上角为锚点，整体等比放大，标题、副标题、强调线与间距一起缩放。
        val logoScale = 1.5f
        val titleX = Math.max(18.6f * scale, layoutWidth / 15.0f)
        val titleY = Math.max(12.4f * scale, titleX * 0.5f)
        val titleScale = 3.658f * logoScale * scale
        val subtitleScale = 0.961f * logoScale * scale
        val titleSubtitleGap = 18.6f * logoScale * scale
        val titleAccentGap = 9.3f * logoScale * scale
        val titleAccentWidth = 105.4f * logoScale * scale
        val titleAccentHeight = Math.max(1.6f, 2.79f * logoScale * scale)

        val titleColor = applyAlpha(Color(230, 224, 233), 0.96f * menuVisibility)
        val subtitleColor = applyAlpha(Color(202, 196, 208), 0.90f * menuVisibility)
        val accentColor = applyAlpha(Color(208, 188, 255), 0.95f * menuVisibility)

        val title = Constants.NAME.uppercase()
        val subtitle = Constants.VERSION

        val metrics = scene.scheduler().textMetrics()
        val titleHeight = metrics.getHeight(titleScale, StaticFontLoader.JURA_LIGHT)
        val subtitleY = titleY + titleHeight + titleSubtitleGap

        // Classic 菜单入口在左下角纵向排列：文字在上、横条在下，与标题左对齐。
        val buttonColumnX = titleX
        val buttonWidth = 230.0f * scale
        val buttonLineHeight = Math.max(2.0f, 3.875f * scale)
        val buttonStride = 70.0f * scale
        val buttonHitPaddingX = 12.4f * scale
        val buttonHitPaddingTop = 12.4f * scale
        val buttonRevealDistance = 34.1f * scale
        val buttonHoverShiftX = 6.2f * scale
        val preferredButtonTextScale = 2.55f * scale
        val buttonTextHeight = metrics.getHeight(preferredButtonTextScale)
        val buttonTextToLineGap = 5.0f * scale
        val buttonListHeight = Math.max(0, entries.size - 1) * buttonStride +
                buttonTextHeight + buttonTextToLineGap + buttonLineHeight
        val buttonsY = layoutHeight - 74.4f * scale - buttonListHeight

        val tree = UiTree.build { scope ->
            scope.pushAbsolute(layoutX, layoutY) { content ->
                if (!reisaShutdownTexturesPrewarmed) {
                    prewarmReisaShutdownTextures(content)
                    reisaShutdownTexturesPrewarmed = true
                }
                if (reisaShutdownStartMs >= 0L) {
                    drawReisaShutdown(content, layoutWidth, layoutHeight, scale, now, -layoutX, -layoutY, width, height)
                } else {
                    drawReisaGreeting(content, layoutWidth, layoutHeight, scale)
                }
                content.layer(0) { layer ->
                    layer.rect(titleX, titleY + titleHeight + titleAccentGap, titleAccentWidth, titleAccentHeight, accentColor)
                }
                content.layer(10) { layer ->
                    layer.text(title, titleX, titleY, titleScale, titleColor, StaticFontLoader.JURA_LIGHT)
                    layer.text(subtitle, titleX, subtitleY, subtitleScale, subtitleColor)
                }
                for (index in entries.indices) {
                    val entry = entries[index]
                    val staged = Mth.clamp((introProgress - index * 0.08f) / 0.52f, 0.0f, 1.0f)
                    val appear = Easing.EASE_OUT_CUBIC.function.apply(staged) * menuVisibility
                    if (appear <= 0.001f) {
                        entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f)
                        continue
                    }

                    val drawX = buttonColumnX - (1.0f - appear) * buttonRevealDistance
                    val buttonY = buttonsY + index * buttonStride
                    val hovered = entry.isHovered(layoutMouseX.toDouble(), layoutMouseY.toDouble())
                    entry.hoverProgress = Mth.lerp(if (hovered) 0.24f else 0.16f, entry.hoverProgress, if (hovered) 1.0f else 0.0f)

                    val hover = entry.hoverProgress
                    val hoveredX = drawX + hover * buttonHoverShiftX
                    entry.setBounds(
                        drawX - buttonHitPaddingX,
                        buttonY - buttonHitPaddingTop,
                        buttonWidth + buttonHitPaddingX * 2.0f,
                        buttonStride
                    )

                    val lineBase = applyAlpha(Color(147, 143, 153), 0.70f * appear)
                    val lineHover = applyAlpha(Color(208, 188, 255), 0.98f * appear)
                    val labelColor = MD3Theme.lerp(
                        applyAlpha(Color(230, 224, 233), 0.94f * appear),
                        applyAlpha(Color(234, 221, 255), 0.98f * appear),
                        hover * 0.68f
                    )

                    val lineY = buttonY + buttonTextHeight + buttonTextToLineGap
                    content.layer(0) { layer ->
                        layer.rect(
                            hoveredX + 1.55f * scale, lineY + 1.55f * scale, buttonWidth + scale * 0.775f,
                            buttonLineHeight + 1.55f * scale, applyAlpha(MD3Theme.SURFACE, 0.70f * appear)
                        )
                        layer.rect(hoveredX, lineY, buttonWidth, buttonLineHeight, MD3Theme.lerp(lineBase, lineHover, hover))
                    }

                    val label = localizedTitle(entry.title)
                    val labelWidth = metrics.getWidth(label, preferredButtonTextScale)
                    val buttonTextScale = if (labelWidth > buttonWidth && labelWidth > 0.0f) {
                        preferredButtonTextScale * buttonWidth / labelWidth
                    } else {
                        preferredButtonTextScale
                    }
                    content.layer(10) { layer -> layer.text(label, hoveredX, buttonY, buttonTextScale, labelColor) }
                }
            }
        }

        scene.submit(UiLayer.CONTENT, tree)
    }

    private fun drawReisaGreeting(scope: UiTree.Scope, width: Int, height: Int, scale: Float) {
        if (reisaGreetingStartMs < 0L) return

        val now = Util.getMillis()
        val elapsed = Math.max(0L, now - reisaGreetingStartMs)
        val exitElapsed = if (reisaExitStartMs < 0L) -1L else Math.max(0L, now - reisaExitStartMs)
        val entrance = Mth.clamp(elapsed / REISA_ENTRANCE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        val slide = Easing.EASE_OUT_BACK.function.apply(entrance)
        val entranceUnfold = 0.04f + 0.96f * Easing.EASE_OUT_CUBIC.function.apply(
            Mth.clamp((elapsed - 60.0f) / 760.0f, 0.0f, 1.0f)
        )
        val poseProgress = if (exitElapsed < 0L) 0.0f
        else Mth.clamp((exitElapsed - REISA_EXIT_POSE_DELAY_MS) / REISA_EXIT_POSE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        val poseEase = Easing.EASE_IN_OUT_CUBIC.function.apply(poseProgress)
        val moveProgress = if (exitElapsed < 0L) 0.0f
        else Mth.clamp((exitElapsed - REISA_EXIT_MOVE_DELAY_MS) / REISA_EXIT_MOVE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        val moveEase = Easing.EASE_IN_CUBIC.function.apply(moveProgress)
        val liftEase = Easing.EASE_IN_OUT_CUBIC.function.apply(moveProgress)
        val fadeProgress = Mth.clamp((moveProgress - 0.25f) / 0.75f, 0.0f, 1.0f)
        val fadeEase = Easing.EASE_OUT_CUBIC.function.apply(fadeProgress)
        val imageHeight = Math.min(height * 0.78f, width * 0.58f)
        val imageWidth = imageHeight * REISA_ASPECT_RATIO
        val targetX = width - imageWidth - Math.max(4.0f, 15.5f * scale)
        val entranceX = Mth.lerp(slide, width + imageWidth * 0.08f, targetX)
        val exitDistance = Math.max(111.6f * scale, imageWidth * 0.34f)
        val imageX = entranceX + exitDistance * moveEase
        val bob = if (entrance >= 1.0f && exitElapsed < 0L) {
            Math.sin(((elapsed - REISA_ENTRANCE_DURATION_MS) * 0.0024f).toDouble()).toFloat() * 2.17f * scale
        } else {
            0.0f
        }
        val baseImageY = height - imageHeight + 6.2f * scale
        val imageY = baseImageY + bob - 18.6f * scale * liftEase
        val entranceAlpha = Easing.EASE_OUT_CUBIC.function.apply(Mth.clamp(elapsed / 240.0f, 0.0f, 1.0f))
        val imageAlpha = entranceAlpha * (1.0f - fadeEase)
        val uniformScale = Mth.lerp(liftEase, 1.0f, 0.97f)
        val drawWidth = imageWidth * uniformScale
        val drawHeight = imageHeight * uniformScale
        val drawX = imageX + (imageWidth - drawWidth) * 0.5f
        val drawY = imageY + (imageHeight - drawHeight) * 0.5f

        val baseProgress = if (exitElapsed < 0L) 0.0f
        else Mth.clamp((exitElapsed - REISA_EXIT_BASE_DELAY_MS) / REISA_EXIT_BASE_DURATION_MS.toFloat(), 0.0f, 1.0f)
        val baseWidth = 1.0f - Easing.EASE_IN_CUBIC.function.apply(baseProgress)
        val baseAlpha = entranceAlpha * if (exitElapsed < 0L) {
            entranceUnfold
        } else {
            1.0f - Easing.EASE_OUT_CUBIC.function.apply(baseProgress)
        }

        drawReisaFloatingBase(scope, height, drawX, drawWidth, baseAlpha, baseWidth, scale)

        if (exitElapsed >= 0L) {
            drawReisaExitAfterimages(scope, drawX, drawY, drawWidth, drawHeight, imageAlpha, poseEase, moveProgress, scale)
            if (poseEase < 0.999f) {
                drawReisa(scope, reisaWelcomeTexture(), drawX, drawY, drawWidth, drawHeight, imageAlpha)
                if (poseEase > 0.001f) {
                    drawReisaPoseOverlay(scope, drawX, drawY, drawWidth, drawHeight, imageAlpha * poseEase)
                }
            } else {
                drawReisa(scope, reisaExitTexture(), drawX, drawY, drawWidth, drawHeight, imageAlpha)
            }
        } else {
            if (elapsed <= REISA_ENTRANCE_DURATION_MS) {
                prewarmReisaExitTexture(scope)
            }
            drawReisaFoldedPage(scope, imageX, imageY, imageWidth, imageHeight, entranceUnfold, imageAlpha, scale)
        }

        drawReisaGreetingBubble(scope, elapsed, exitElapsed, width, drawX, drawY, drawHeight, scale)
    }

    private fun drawReisaShutdown(
        scope: UiTree.Scope,
        width: Int,
        height: Int,
        scale: Float,
        now: Long,
        maskX: Float,
        maskY: Float,
        maskWidth: Int,
        maskHeight: Int,
    ) {
        val elapsed = Math.max(0L, now - reisaShutdownStartMs)
        val exitElapsed = if (reisaShutdownExitStartMs < 0L) -1L else Math.max(0L, now - reisaShutdownExitStartMs)
        val entranceProgress = Mth.clamp(
            (elapsed - REISA_SHUTDOWN_ENTRANCE_DELAY_MS) / REISA_SHUTDOWN_ENTRANCE_DURATION_MS.toFloat(), 0.0f, 1.0f
        )
        val entranceEase = Easing.EASE_OUT_BACK.function.apply(entranceProgress)
        val exitProgress = if (exitElapsed < 0L) 0.0f
        else Mth.clamp(exitElapsed / REISA_SHUTDOWN_EXIT_DURATION_MS.toFloat(), 0.0f, 1.0f)
        val exitEase = Easing.EASE_IN_CUBIC.function.apply(exitProgress)
        val imageHeight = Math.min(height * 0.80f, width * 0.60f)
        val imageWidth = imageHeight * REISA_ASPECT_RATIO
        val targetX = width - imageWidth - Math.max(4.0f, 15.5f * scale)
        val imageX = Mth.lerp(entranceEase, width + imageWidth * 0.12f, targetX) + exitEase * (imageWidth * 0.56f + 65.1f * scale)
        val bob = if (entranceProgress >= 1.0f && exitProgress <= 0.0f) {
            Math.sin(((elapsed - REISA_SHUTDOWN_ENTRANCE_DURATION_MS) * 0.0031f).toDouble()).toFloat() * 2.48f * scale
        } else {
            0.0f
        }
        val imageY = height - imageHeight + 6.2f * scale + bob - exitEase * 43.4f * scale
        val imageScale = Mth.lerp(exitEase, 1.0f, 0.94f)
        val drawWidth = imageWidth * imageScale
        val drawHeight = imageHeight * imageScale
        val drawX = imageX + (imageWidth - drawWidth) * 0.5f
        val drawY = imageY + (imageHeight - drawHeight) * 0.5f
        val imageAlpha = Easing.EASE_OUT_CUBIC.function.apply(entranceProgress) * (1.0f - exitEase)

        scope.layer(-40) { layer ->
            layer.rect(maskX, maskY, maskWidth.toFloat(), maskHeight.toFloat(), applyAlpha(Color(8, 9, 16), imageAlpha * 0.62f))
        }

        drawReisaShutdownTrails(scope, drawX, drawY, drawWidth, drawHeight, exitProgress, scale)
        drawReisa(scope, reisaShutdownTexture(elapsed, exitElapsed >= 0L), drawX, drawY, drawWidth, drawHeight, imageAlpha)
        drawReisaShutdownBubble(scope, elapsed, exitElapsed, width, drawX, drawY, drawHeight, scale)
    }

    private fun drawReisaShutdownTrails(
        scope: UiTree.Scope,
        imageX: Float,
        imageY: Float,
        imageWidth: Float,
        imageHeight: Float,
        exitProgress: Float,
        scale: Float,
    ) {
        val trailEnvelope = Math.sin((exitProgress * Math.PI).toDouble()).toFloat()
        if (trailEnvelope <= 0.001f) return
        val startX = imageX + imageWidth * 0.34f
        val maxWidth = Math.max(0.0f, LuminRenderSystem.getScaledWidth() - startX)
        val bright = applyAlpha(Color(235, 211, 255), trailEnvelope * 0.62f)
        val soft = applyAlpha(Color(183, 146, 245), 0.0f)
        scope.layer(-22) { layer ->
            layer.rectHorizontalGradient(startX, imageY + imageHeight * 0.24f, maxWidth, Math.max(1.0f, 2.325f * scale), bright, soft)
            layer.rectHorizontalGradient(
                startX + imageWidth * 0.08f, imageY + imageHeight * 0.49f, maxWidth * 0.78f,
                Math.max(1.0f, 1.55f * scale), applyAlpha(bright, 0.68f), soft
            )
            layer.rectHorizontalGradient(
                startX - imageWidth * 0.03f, imageY + imageHeight * 0.71f, maxWidth * 0.88f,
                Math.max(1.0f, 1.86f * scale), applyAlpha(bright, 0.48f), soft
            )
        }
    }

    private fun drawReisaShutdownBubble(
        scope: UiTree.Scope,
        elapsed: Long,
        exitElapsed: Long,
        width: Int,
        imageX: Float,
        imageY: Float,
        imageHeight: Float,
        scale: Float,
    ) {
        val bubbleIn = Easing.EASE_OUT_CUBIC.function.apply(
            Mth.clamp((elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS) / 320.0f, 0.0f, 1.0f)
        )
        val bubbleOut = if (exitElapsed < 0L) 1.0f
        else 1.0f - Easing.EASE_OUT_CUBIC.function.apply(Mth.clamp(exitElapsed / 300.0f, 0.0f, 1.0f))
        val visibility = Math.min(bubbleIn, bubbleOut)
        if (visibility <= 0.001f) return

        val name = "UZAWA REISA"
        val farewell = EpsilonTranslations.Gui.MAINMENU_REISA_FAREWELL.getTranslatedName()
        val metrics = scene.scheduler().textMetrics()
        val contentInset = 20.15f * scale
        val maxBubbleWidth = Math.clamp(348.75f * scale, 161.2f * scale, width * 0.44f)
        val availableTextWidth = Math.max(1.0f, maxBubbleWidth - contentInset * 2.0f)
        val nameScale = 0.961f * scale
        val messageScale = fitTextScale(farewell, 1.054f * scale, availableTextWidth)
        val bubbleWidth = Math.min(
            maxBubbleWidth,
            Math.max(
                metrics.getWidth(name, nameScale, StaticFontLoader.JURA_LIGHT),
                metrics.getWidth(farewell, messageScale)
            ) + contentInset * 2.0f
        )
        val topPadding = 11.625f * scale
        val rowGap = 3.875f * scale
        val bottomPadding = 13.95f * scale
        val nameHeight = metrics.getHeight(nameScale, StaticFontLoader.JURA_LIGHT)
        val messageHeight = metrics.getHeight(messageScale)
        val bubbleHeight = topPadding + nameHeight + rowGap + messageHeight + bottomPadding
        val bubbleX = Mth.clamp(imageX - bubbleWidth * 0.72f, 18.6f * scale, width - bubbleWidth - 18.6f * scale)
        val bubbleY = imageY + imageHeight * 0.22f + (1.0f - bubbleIn) * 18.6f * scale
        val radius = 9.3f * scale
        val speechProgress = Mth.clamp(
            (elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS) /
                    (REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS - REISA_SHUTDOWN_BUBBLE_DELAY_MS).toFloat(), 0.0f, 1.0f
        )

        val surface = applyAlpha(Color(29, 31, 42), visibility * 0.97f)
        val outline = applyAlpha(Color(229, 194, 255), visibility * 0.54f)
        val nameColor = applyAlpha(Color(252, 224, 255), visibility)
        val accentColor = applyAlpha(Color(222, 169, 255), visibility * 0.98f)
        val textColor = applyAlpha(Color(244, 241, 250), visibility)
        val textX = bubbleX + contentInset
        val contentOffsetY = -3.1f * scale
        val nameY = bubbleY + topPadding + contentOffsetY
        val messageY = nameY + nameHeight + rowGap
        val progressBarHeight = Math.max(3.0f, 4.65f * scale)
        val progressBarY = bubbleY + bubbleHeight - 12.4f * scale

        scope.layer(20) { layer ->
            layer.shadow(
                bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, REISA_BUBBLE_SHADOW_BLUR * scale,
                applyAlpha(Color(0, 0, 0), visibility * REISA_SHUTDOWN_BUBBLE_SHADOW_ALPHA)
            )
            layer.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, surface)
            layer.outline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, Math.max(1.0f, 1.55f * scale), outline)
            layer.roundRect(bubbleX + 10.075f * scale, nameY, 3.1f * scale, nameHeight, 1.55f * scale, accentColor)
            layer.roundRect(
                bubbleX + contentInset, progressBarY, (bubbleWidth - contentInset * 2.0f) * speechProgress,
                progressBarHeight, progressBarHeight * 0.5f, applyAlpha(accentColor, 0.62f)
            )
        }
        scope.layer(21) { layer ->
            layer.text(name, textX, nameY, nameScale, nameColor, StaticFontLoader.JURA_LIGHT)
            layer.text(farewell, textX, messageY, messageScale, textColor)
        }
    }

    private fun reisaShutdownTexture(elapsed: Long, exiting: Boolean): Identifier? {
        if (exiting) return reisaShutdownFinalTexture()
        if (elapsed < REISA_SHUTDOWN_BUBBLE_DELAY_MS) return reisaShutdownEntranceTexture()

        val speechElapsed = elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS
        return if ((speechElapsed / 145L and 1L) == 0L) reisaWelcomeTexture() else reisaExitTexture()
    }

    private fun prewarmReisaShutdownTextures(scope: UiTree.Scope) {
        val transparent = applyAlpha(Color.WHITE, 0.0f)
        val entrance = reisaShutdownEntranceTexture()
        val last = reisaShutdownFinalTexture()
        scope.layer(-60) { layer ->
            if (entrance != null) {
                layer.texture(entrance, -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, transparent, true)
            }
            if (last != null) {
                layer.texture(last, -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, transparent, true)
            }
        }
    }

    private fun drawReisaFloatingBase(
        scope: UiTree.Scope,
        height: Int,
        imageX: Float,
        imageWidth: Float,
        alpha: Float,
        widthProgress: Float,
        scale: Float,
    ) {
        if (alpha <= 0.001f || widthProgress <= 0.001f) return

        val centerX = imageX + imageWidth * 0.55f
        val lineWidth = imageWidth * widthProgress
        val lineHeight = Math.max(1.0f, 1.7825f * scale)
        val lineY = height - lineHeight
        val glowHeight = 52.7f * scale

        val transparent = applyAlpha(Color(213, 177, 255), 0.0f)
        val glow = applyAlpha(Color(205, 162, 255), alpha * 0.30f)
        val line = applyAlpha(Color(239, 220, 255), alpha * 0.78f)

        scope.layer(-24) { layer ->
            layer.rectVerticalGradient(centerX - lineWidth * 0.5f, lineY - glowHeight, lineWidth, glowHeight, transparent, glow)
        }
        scope.layer(-23) { layer ->
            layer.roundRect(centerX - lineWidth * 0.5f, lineY, lineWidth, lineHeight, lineHeight * 0.5f, line)
        }
    }

    private fun drawReisaExitAfterimages(
        scope: UiTree.Scope,
        imageX: Float,
        imageY: Float,
        imageWidth: Float,
        imageHeight: Float,
        alpha: Float,
        poseProgress: Float,
        moveProgress: Float,
        scale: Float,
    ) {
        val trailEnvelope = Math.sin((moveProgress * Math.PI).toDouble()).toFloat()
        val trailAlpha = alpha * poseProgress * trailEnvelope
        if (trailAlpha <= 0.001f) return

        val farTrail = applyAlpha(Color(216, 185, 255), trailAlpha * 0.08f)
        val nearTrail = applyAlpha(Color(228, 205, 255), trailAlpha * 0.14f)
        val exit = reisaExitTexture() ?: return
        scope.layer(-22) { layer ->
            layer.texture(
                exit, imageX - 24.8f * scale, imageY + 2.325f * scale, imageWidth, imageHeight,
                0.0f, 0.0f, 1.0f, 1.0f, farTrail, true
            )
            layer.texture(
                exit, imageX - 12.4f * scale, imageY + 1.1625f * scale, imageWidth, imageHeight,
                0.0f, 0.0f, 1.0f, 1.0f, nearTrail, true
            )
        }
    }

    private fun drawReisaPoseOverlay(scope: UiTree.Scope, imageX: Float, imageY: Float, imageWidth: Float, imageHeight: Float, alpha: Float) {
        val exit = reisaExitTexture() ?: return
        scope.layer(-20) { layer ->
            layer.texture(
                exit, imageX, imageY, imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f,
                applyAlpha(Color.WHITE, alpha), true
            )
        }
    }

    private fun prewarmReisaExitTexture(scope: UiTree.Scope) {
        val exit = reisaExitTexture() ?: return
        scope.layer(-30) { layer ->
            layer.texture(exit, -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, applyAlpha(Color.WHITE, 0.0f), true)
        }
    }

    private fun drawReisa(scope: UiTree.Scope, texture: Identifier?, imageX: Float, imageY: Float, imageWidth: Float, imageHeight: Float, alpha: Float) {
        if (texture == null) return
        scope.layer(-21) { layer ->
            layer.texture(texture, imageX, imageY, imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, applyAlpha(Color.WHITE, alpha), true)
        }
    }

    private fun drawReisaFoldedPage(
        scope: UiTree.Scope,
        imageX: Float,
        imageY: Float,
        imageWidth: Float,
        imageHeight: Float,
        unfold: Float,
        alpha: Float,
        scale: Float,
    ) {
        val welcome = reisaWelcomeTexture() ?: return
        if (unfold >= 0.999f) {
            drawReisa(scope, welcome, imageX, imageY, imageWidth, imageHeight, alpha)
            return
        }

        val hingeX = imageX + imageWidth
        for (index in 0 until REISA_PAGE_SLICES) {
            val u0 = index / REISA_PAGE_SLICES.toFloat()
            val u1 = (index + 1.0f) / REISA_PAGE_SLICES
            val center = (u0 + u1) * 0.5f
            val sliceX = hingeX - imageWidth * (1.0f - u0) * unfold
            val sliceRight = hingeX - imageWidth * (1.0f - u1) * unfold
            val curl = Math.sin((center * Math.PI).toDouble()).toFloat() * (1.0f - unfold) * 20.15f * scale
            val shade = 1.0f - (1.0f - unfold) *
                    (0.18f + 0.38f * Math.sin((center * Math.PI).toDouble()).toFloat())
            val sliceColor = applyAlpha(Color.WHITE, alpha * shade)
            scope.layer(-20) { layer ->
                layer.texture(
                    welcome, sliceX, imageY + curl,
                    Math.max(0.5f, sliceRight - sliceX + 0.35f), imageHeight - curl * 0.25f,
                    u0, 0.0f, u1, 1.0f, sliceColor, true
                )
            }
        }

        val foldEdgeX = hingeX - imageWidth * unfold
        val edgeAlpha = alpha * (1.0f - unfold) * 0.72f
        scope.layer(-19) { layer ->
            layer.rect(
                foldEdgeX, imageY + imageHeight * 0.05f, Math.max(1.0f, 3.875f * scale), imageHeight * 0.90f,
                applyAlpha(Color(245, 224, 255), edgeAlpha)
            )
        }
    }

    private fun drawReisaGreetingBubble(
        scope: UiTree.Scope,
        elapsed: Long,
        exitElapsed: Long,
        width: Int,
        imageX: Float,
        imageY: Float,
        imageHeight: Float,
        scale: Float,
    ) {
        val bubbleIn = Easing.EASE_OUT_CUBIC.function.apply(Mth.clamp((elapsed - REISA_BUBBLE_DELAY_MS) / 380.0f, 0.0f, 1.0f))
        val bubbleExit = if (exitElapsed < 0L) 0.0f
        else Easing.EASE_OUT_CUBIC.function.apply(Mth.clamp(exitElapsed / 260.0f, 0.0f, 1.0f))
        val bubbleOut = 1.0f - bubbleExit
        val visibility = Math.min(bubbleIn, bubbleOut)
        if (visibility <= 0.001f) return

        val name = "UZAWA REISA"
        val greeting = EpsilonTranslations.Gui.MAINMENU_REISA_GREETING.getTranslatedName()
        val metrics = scene.scheduler().textMetrics()
        val contentInset = 20.15f * scale
        val maxBubbleWidth = Math.clamp(317.75f * scale, 148.8f * scale, width * 0.40f)
        val minBubbleWidth = Math.min(224.75f * scale, maxBubbleWidth)
        val availableTextWidth = Math.max(1.0f, maxBubbleWidth - contentInset * 2.0f)
        val nameScale = 0.961f * scale
        val messageScale = fitTextScale(greeting, 1.054f * scale, availableTextWidth)
        val nameWidth = metrics.getWidth(name, nameScale, StaticFontLoader.JURA_LIGHT)
        val messageWidth = metrics.getWidth(greeting, messageScale)
        val bubbleWidth = Mth.clamp(Math.max(nameWidth, messageWidth) + contentInset * 2.0f, minBubbleWidth, maxBubbleWidth)
        val topPadding = 11.625f * scale
        val rowGap = 3.875f * scale
        val bottomPadding = 12.4f * scale
        val nameHeight = metrics.getHeight(nameScale, StaticFontLoader.JURA_LIGHT)
        val messageHeight = metrics.getHeight(messageScale)
        val bubbleHeight = topPadding + nameHeight + rowGap + messageHeight + bottomPadding
        val bubbleX = Mth.clamp(imageX - bubbleWidth * 0.72f, 18.6f * scale, width - bubbleWidth - 18.6f * scale)
        val bubbleY = imageY + imageHeight * 0.23f + (1.0f - bubbleIn) * 18.6f * scale - bubbleExit * 15.5f * scale
        val radius = 9.3f * scale
        val alpha = visibility * 0.96f

        val surface = applyAlpha(Color(29, 31, 42), alpha)
        val outline = applyAlpha(Color(229, 194, 255), visibility * 0.48f)
        val nameColor = applyAlpha(Color(252, 224, 255), visibility)
        val accentColor = applyAlpha(Color(222, 169, 255), visibility * 0.96f)
        val textColor = applyAlpha(Color(244, 241, 250), visibility)

        val textX = bubbleX + contentInset
        val nameY = bubbleY + topPadding
        val messageY = nameY + nameHeight + rowGap

        scope.layer(20) { layer ->
            layer.shadow(
                bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, REISA_BUBBLE_SHADOW_BLUR * scale,
                applyAlpha(Color(0, 0, 0), visibility * REISA_GREETING_BUBBLE_SHADOW_ALPHA)
            )
            layer.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, surface)
            layer.outline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, Math.max(1.0f, 1.55f * scale), outline)
            layer.roundRect(bubbleX + 10.075f * scale, nameY, 3.1f * scale, nameHeight, 1.55f * scale, accentColor)
            layer.roundRect(
                bubbleX + bubbleWidth - 20.15f * scale, bubbleY + bubbleHeight - 3.1f * scale,
                15.5f * scale, 13.95f * scale, 3.875f * scale, surface
            )
        }
        scope.layer(21) { layer ->
            layer.text(name, textX, nameY, nameScale, nameColor, StaticFontLoader.JURA_LIGHT)
            layer.text(greeting, textX, messageY, messageScale, textColor)
        }
    }

    private fun fitTextScale(text: String, preferredScale: Float, maxWidth: Float): Float {
        val textWidth = scene.scheduler().textMetrics().getWidth(text, preferredScale)
        return if (textWidth > maxWidth && textWidth > 0.0f) preferredScale * maxWidth / textWidth else preferredScale
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (reisaShutdownStartMs >= 0L) return true
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
            val width = LuminRenderSystem.getScaledWidthInt()
            val height = LuminRenderSystem.getScaledHeightInt()
            val scale = resolutionScale(width, height)
            val layoutWidth = Math.round(MENU_REFERENCE_WIDTH * scale)
            val layoutHeight = Math.round(MENU_REFERENCE_HEIGHT * scale)
            val columbinaStyle = ClientSetting.mainMenuStyle.`is`(ClientSetting.MainMenuStyle.Columbina)
            val layoutX = if (columbinaStyle) width - layoutWidth else (width - layoutWidth) * 0.5f
            val layoutY = if (columbinaStyle) height - layoutHeight else (height - layoutHeight) * 0.5f
            for (entry in entries) {
                if (entry.isHovered(epsilonEvent.x() - layoutX, epsilonEvent.y() - layoutY)) {
                    entry.action.run()
                    return true
                }
            }
        }
        return super.mouseClicked(LuminRenderSystem.toEpsilonMouseEvent(event), doubleClick)
    }

    override fun removed() {
        super.removed()
        val minecraft = this.minecraft
        if (!VideoPlayer.isStopped()) {
            VideoPlayer.pause()
        }
        initialized = false
        if (reisaWelcomeSound != null) {
            minecraft?.soundManager?.stop(reisaWelcomeSound!!)
        }
        if (reisaShutdownSound != null) {
            minecraft?.soundManager?.stop(reisaShutdownSound!!)
        }
        clearReisaGreeting()
        clearReisaShutdown()
        if (backgroundRenderTarget != null) {
            backgroundRenderTarget!!.close()
            backgroundRenderTarget = null
        }
        if (uiRenderTarget != null) {
            uiRenderTarget!!.close()
            uiRenderTarget = null
        }
        scene.close()
    }

    private fun clearReisaGreeting() {
        reisaGreetingStartMs = -1L
        reisaExitStartMs = -1L
        reisaWelcomeSound = null
    }

    private fun clearReisaShutdown() {
        reisaShutdownStartMs = -1L
        reisaShutdownExitStartMs = -1L
        reisaShutdownSound = null
        reisaShutdownCommitted = false
        reisaShutdownTexturesPrewarmed = false
    }

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    private fun resolutionScale(width: Int, height: Int): Float {
        return Math.min(width / MENU_REFERENCE_WIDTH, height / MENU_REFERENCE_HEIGHT)
    }

    private fun localizedTitle(title: String): String {
        return when (title) {
            "Singleplayer" -> EpsilonTranslations.Gui.MAINMENU_SINGLEPLAYER.getTranslatedName()
            "Multiplayer" -> EpsilonTranslations.Gui.MAINMENU_MULTIPLAYER.getTranslatedName()
            "Accounts" -> EpsilonTranslations.Gui.MAINMENU_ACCOUNTS.getTranslatedName()
            "Options" -> EpsilonTranslations.Gui.MAINMENU_OPTIONS.getTranslatedName()
            "Quit" -> EpsilonTranslations.Gui.MAINMENU_QUIT.getTranslatedName()
            else -> title
        }
    }

    private fun applyAlpha(color: Color, alphaFactor: Float): Color {
        val factor = Mth.clamp(alphaFactor, 0.0f, 1.0f)
        return Color(color.red, color.green, color.blue, Math.round(color.alpha * factor))
    }

    private class MenuEntry(val title: String, val action: Runnable) {

        private var x: Float = 0f
        private var y: Float = 0f
        private var width: Float = 0f
        private var height: Float = 0f

        var hoverProgress: Float = 0f

        fun setBounds(x: Float, y: Float, width: Float, height: Float) {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }

        fun isHovered(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        }

    }

}
