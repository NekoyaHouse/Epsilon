package com.github.epsilon.gui.screen;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.shaders.GlslSandBox;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.video.VideoPlayer;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.screen.accounts.AccountsScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.AssetManager;
import com.github.epsilon.managers.VideoManager;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    public static final MainMenuScreen INSTANCE = new MainMenuScreen();

    public enum Background {
        SEA_LEVEL,
        CLOUDS,
        ALIEN_TERRAIN,
        INFERNO,
        PLANET,
        BLACK_HOLE,
        MINECRAFT
    }

    private static final float MENU_REFERENCE_WIDTH = 1600.0f;
    private static final float MENU_REFERENCE_HEIGHT = 900.0f;
    private static final float REISA_ASPECT_RATIO = 710.0f / 1280.0f;
    private static final int REISA_PAGE_SLICES = 12;
    private static final long REISA_ENTRANCE_DURATION_MS = 900L;
    private static final long REISA_BUBBLE_DELAY_MS = 620L;
    private static final long REISA_FALLBACK_VISIBLE_MS = 3_800L;
    private static final long REISA_EXIT_POSE_DELAY_MS = 60L;
    private static final long REISA_EXIT_POSE_DURATION_MS = 240L;
    private static final long REISA_EXIT_MOVE_DELAY_MS = 180L;
    private static final long REISA_EXIT_MOVE_DURATION_MS = 670L;
    private static final long REISA_EXIT_BASE_DELAY_MS = 120L;
    private static final long REISA_EXIT_BASE_DURATION_MS = 500L;
    private static final long REISA_EXIT_DURATION_MS = 900L;

    private static final long REISA_SHUTDOWN_ENTRANCE_DELAY_MS = 220L;
    private static final long REISA_SHUTDOWN_ENTRANCE_DURATION_MS = 760L;
    private static final long REISA_SHUTDOWN_BUBBLE_DELAY_MS = 1_150L;
    private static final long REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS = 7_666L;
    private static final long REISA_SHUTDOWN_EXIT_DURATION_MS = 900L;
    private static final long REISA_SHUTDOWN_MENU_FADE_DURATION_MS = 2_200L;
    private static final float REISA_BUBBLE_SHADOW_BLUR = 21.7f;
    private static final float REISA_SHUTDOWN_BUBBLE_SHADOW_ALPHA = 0.60f;
    private static final float REISA_GREETING_BUBBLE_SHADOW_ALPHA = 0.58f;

    private static final String REISA_WELCOME_SUFFIX = "00";
    private static final String REISA_EXIT_SUFFIX = "09";
    private static final String REISA_SHUTDOWN_ENTRANCE_SUFFIX = "10";
    private static final String REISA_SHUTDOWN_FINAL_SUFFIX = "18";

    /**
     * 玲纱立绘改为按需下载，纹理标识在渲染线程上惰性注册；未下载时返回 {@code null}。
     */
    private static Identifier reisaWelcomeTexture() {
        return AssetManager.INSTANCE.reisaTexture(REISA_WELCOME_SUFFIX);
    }

    private static Identifier reisaExitTexture() {
        return AssetManager.INSTANCE.reisaTexture(REISA_EXIT_SUFFIX);
    }

    private static Identifier reisaShutdownEntranceTexture() {
        return AssetManager.INSTANCE.reisaTexture(REISA_SHUTDOWN_ENTRANCE_SUFFIX);
    }

    private static Identifier reisaShutdownFinalTexture() {
        return AssetManager.INSTANCE.reisaTexture(REISA_SHUTDOWN_FINAL_SUFFIX);
    }

    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);

    private final List<MenuEntry> entries = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget backgroundRenderTarget;
    private LuminRenderSystem.LuminRenderTarget uiRenderTarget;

    private long introStartMs;
    private boolean initialized;
    private boolean reisaGreetingQueued;
    private long reisaGreetingStartMs = -1L;
    private long reisaExitStartMs = -1L;
    private SoundInstance reisaWelcomeSound;
    private long reisaShutdownStartMs = -1L;
    private long reisaShutdownExitStartMs = -1L;
    private SoundInstance reisaShutdownSound;
    private boolean reisaShutdownCommitted;
    private boolean reisaShutdownTexturesPrewarmed;

    private MainMenuScreen() {
        super(Component.literal("MainMenuScreen"));
        entries.add(new MenuEntry("Singleplayer", () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        entries.add(new MenuEntry("Multiplayer", () -> {
            Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.gui.setScreen(screen);
        }));
        entries.add(new MenuEntry("Accounts", () -> minecraft.gui.setScreen(AccountsScreen.INSTANCE)));
        entries.add(new MenuEntry("GUI", () -> minecraft.gui.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        })));
        entries.add(new MenuEntry("Options", () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false))));
        entries.add(new MenuEntry("Quit", () -> {
            if (!requestShutdown()) minecraft.stop();
        }));
    }

    @Override
    protected void init() {
        super.init();
        AssetManager assets = AssetManager.INSTANCE;
        boolean columbinaStyle = ClientSetting.INSTANCE.mainMenuStyle.is(ClientSetting.MainMenuStyle.Columbina);
        if (columbinaStyle && assets.isVideoSupported()) {
            if (VideoPlayer.isStopped()) {
                if (assets.isVideoReady() && assets.isFfmpegReady()) {
                    try {
                        VideoManager.INSTANCE.loadBackground();
                    } catch (Exception exception) {
                        Constants.LOGGER.error("Unable to load the Columbina main-menu video", exception);
                    }
                }
            } else {
                VideoPlayer.resume();
            }
            // 视频叠层光效可以独立缺失，缺失时补提示；已就绪或本会话已拒绝时不会重复弹窗。
            assets.requestDownload(List.of(
                    AssetManager.Asset.VIDEO,
                    AssetManager.Asset.LIGHT_TRAILS,
                    AssetManager.Asset.FFMPEG
            ));
        } else if (!VideoPlayer.isStopped()) {
            VideoPlayer.stop();
        }
        requestReisaAssetsIfNeeded();
        if (!initialized) {
            initialized = true;
            introStartMs = Util.getMillis();
            GlslSandBox.INSTANCE.resetTime();
            for (MenuEntry entry : entries) {
                entry.hoverProgress = 0.0f;
                entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
            }
        }
        if (reisaGreetingQueued) {
            startReisaGreeting();
        }
    }

    public void queueReisaGreeting() {
        if (!ClientSetting.INSTANCE.showReisaOnStartup.getValue() || !AssetManager.INSTANCE.isReisaReady()) {
            reisaGreetingQueued = false;
            return;
        }
        reisaGreetingQueued = true;
        if (initialized && minecraft.gui.screen() == this) {
            startReisaGreeting();
        }
    }

    /**
     * 玲纱立绘缺失时提示下载，仅在启用了启动问候或关机动画时需要。
     */
    private void requestReisaAssetsIfNeeded() {
        AssetManager assets = AssetManager.INSTANCE;
        if (assets.isReisaReady()) {
            return;
        }
        boolean needed = ClientSetting.INSTANCE.showReisaOnStartup.getValue()
                || ClientSetting.INSTANCE.showReisaOnShutdown.getValue();
        if (needed) {
            assets.requestDownload(List.of(AssetManager.Asset.REISA));
        }
    }

    private void startReisaGreeting() {
        if (!ClientSetting.INSTANCE.showReisaOnStartup.getValue() || !AssetManager.INSTANCE.isReisaReady()) {
            clearReisaGreeting();
            return;
        }
        reisaGreetingQueued = false;
        reisaGreetingStartMs = Util.getMillis();
        reisaExitStartMs = -1L;
        reisaWelcomeSound = SoundManager.INSTANCE.playTracked(
                SoundKey.REISA_WELCOME,
                ClientSetting.INSTANCE.reisaVolume.getValue().floatValue()
        ).orElse(null);
    }

    public boolean requestShutdown() {
        if (!ClientSetting.INSTANCE.showReisaOnShutdown.getValue() || !AssetManager.INSTANCE.isReisaReady())
            return false;
        if (!initialized || minecraft.gui.screen() != this || reisaShutdownCommitted) return false;
        if (reisaShutdownStartMs >= 0L) return true;

        if (reisaWelcomeSound != null) {
            minecraft.getSoundManager().stop(reisaWelcomeSound);
        }
        clearReisaGreeting();
        minecraft.getSoundManager().stop();
        reisaShutdownStartMs = Util.getMillis();
        reisaShutdownExitStartMs = -1L;
        reisaShutdownSound = SoundManager.INSTANCE.playTracked(
                SoundKey.REISA_BYE,
                ClientSetting.INSTANCE.reisaVolume.getValue().floatValue()
        ).orElse(null);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (reisaShutdownStartMs >= 0L) {
            long now = Util.getMillis();
            if (reisaShutdownExitStartMs < 0L) {
                boolean soundFinished = reisaShutdownSound != null && !minecraft.getSoundManager().isActive(reisaShutdownSound);
                boolean fallbackFinished = reisaShutdownSound == null && now - reisaShutdownStartMs >= REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS;
                if (soundFinished || fallbackFinished) {
                    reisaShutdownSound = null;
                    reisaShutdownExitStartMs = now;
                }
                return;
            }

            if (now - reisaShutdownExitStartMs >= REISA_SHUTDOWN_EXIT_DURATION_MS) {
                reisaShutdownCommitted = true;
                minecraft.stop();
            }
            return;
        }
        if (reisaGreetingStartMs < 0L) return;

        long now = Util.getMillis();
        if (reisaExitStartMs >= 0L) {
            if (now - reisaExitStartMs >= REISA_EXIT_DURATION_MS) {
                clearReisaGreeting();
            }
            return;
        }

        boolean welcomeFinished = reisaWelcomeSound != null && !minecraft.getSoundManager().isActive(reisaWelcomeSound);
        boolean fallbackFinished = reisaWelcomeSound == null && now - reisaGreetingStartMs >= REISA_FALLBACK_VISIBLE_MS;
        if (welcomeFinished || fallbackFinished) {
            reisaWelcomeSound = null;
            reisaExitStartMs = now;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (backgroundRenderTarget == null) {
            backgroundRenderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu-background", window.getWidth(), window.getHeight());
        }

        backgroundRenderTarget.resize(window.getWidth(), window.getHeight());
        backgroundRenderTarget.clear();
        LuminRenderSystem.setActiveTarget(backgroundRenderTarget);

        if (ClientSetting.INSTANCE.mainMenuStyle.is(ClientSetting.MainMenuStyle.Columbina) && drawColumbinaBackground()) {
            LuminRenderSystem.setActiveTarget(null);
            graphics.blit(backgroundRenderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        } else {
            final var background = switch (ClientSetting.INSTANCE.mainMenuBackground.getValue()) {
                case SEA_LEVEL -> GlslSandBox.SEA_LEVEL;
                case CLOUDS -> GlslSandBox.CLOUDS;
                case ALIEN_TERRAIN -> GlslSandBox.ALIEN_TERRAIN;
                case INFERNO -> GlslSandBox.INFERNO;
                case PLANET -> GlslSandBox.PLANET;
                case BLACK_HOLE -> GlslSandBox.BLACK_HOLE;
                case MINECRAFT -> GlslSandBox.MINECRAFT;
            };

            GlslSandBox.INSTANCE.render(background, LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));

            LuminRenderSystem.setActiveTarget(null);
            graphics.blit(backgroundRenderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (uiRenderTarget == null) {
            uiRenderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu-ui", window.getWidth(), window.getHeight());
        }

        uiRenderTarget.resize(window.getWidth(), window.getHeight());
        uiRenderTarget.clear();
        LuminRenderSystem.setActiveTarget(uiRenderTarget);

        if (ClientSetting.INSTANCE.mainMenuStyle.is(ClientSetting.MainMenuStyle.Columbina)) {
            drawColumbinaMenu(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
        } else {
            scene.beginFrame();
            drawClassicMenu(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
            scene.endFrame();
        }

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(uiRenderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private boolean drawColumbinaBackground() {
        Identifier texture = VideoPlayer.getGuiTexture();
        int textureWidth = VideoPlayer.getGuiTextureWidth();
        int textureHeight = VideoPlayer.getGuiTextureHeight();
        if (texture == null || textureWidth <= 0 || textureHeight <= 0) {
            return false;
        }

        int width = LuminRenderSystem.getScaledWidthInt();
        int height = LuminRenderSystem.getScaledHeightInt();
        float sourceAspect = textureWidth / (float) textureHeight;
        float targetAspect = width / (float) Math.max(1, height);
        float u0 = 0.0f;
        float u1 = 1.0f;
        float v0 = 0.0f;
        float v1 = 1.0f;
        if (sourceAspect > targetAspect) {
            float visible = targetAspect / sourceAspect;
            u0 = (1.0f - visible) * 0.5f;
            u1 = u0 + visible;
        } else {
            float visible = sourceAspect / targetAspect;
            v0 = (1.0f - visible) * 0.5f;
            v1 = v0 + visible;
        }

        float finalU0 = u0;
        float finalU1 = u1;
        float finalV0 = v0;
        float finalV1 = v1;
        Identifier lightTrails = AssetManager.INSTANCE.videoOverlayTexture();
        UiTree backgroundTree = UiTree.build(scope -> {
            scope.layer(0, layer -> {
                layer.texture(texture, 0.0f, 0.0f, width, height, finalU0, finalV0, finalU1, finalV1, Color.WHITE, true);
                if (lightTrails != null) {
                    layer.texture(lightTrails, 0.0f, 0.0f, width, height, 0.0f, 0.0f, 1.0f, 1.0f, Color.WHITE, true);
                }
            });
            scope.layer(1, layer -> {
                layer.rectHorizontalGradient(width * 0.42f, 0.0f, width * 0.58f, height, new Color(14, 20, 45, 0), new Color(7, 11, 30, 150));
                layer.rectVerticalGradient(0.0f, 0.0f, width, height, new Color(7, 10, 25, 20), new Color(4, 7, 18, 88));
            });
        });
        scene.beginFrame();
        scene.submit(UiLayer.CONTENT, backgroundTree);
        scene.endFrame();
        return true;
    }

    private void drawColumbinaMenu(int mouseX, int mouseY) {
        String clientName = Constants.NAME.toUpperCase();
        long now = Util.getMillis();
        int width = LuminRenderSystem.getScaledWidthInt();
        int height = LuminRenderSystem.getScaledHeightInt();
        float scale = resolutionScale(width, height);
        int layoutWidth = Math.round(MENU_REFERENCE_WIDTH * scale);
        int layoutHeight = Math.round(MENU_REFERENCE_HEIGHT * scale);
        float layoutX = width - layoutWidth;
        float layoutY = height - layoutHeight;
        float layoutMouseX = mouseX - layoutX;
        float layoutMouseY = mouseY - layoutY;
        float menuCenterCorrectionX = (width - layoutWidth) * 0.2f;
        float intro = Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp((now - introStartMs) / 850.0f, 0.0f, 1.0f));
        float shutdownElapsed = reisaShutdownStartMs < 0L ? 0.0f : now - reisaShutdownStartMs;
        float exit = Easing.EASE_IN_OUT_CUBIC.getFunction().apply(Mth.clamp(shutdownElapsed / REISA_SHUTDOWN_MENU_FADE_DURATION_MS, 0.0f, 1.0f));
        float menuVisibility = 1.0f - exit;
        float visibility = intro * menuVisibility;

        float rightMargin = Math.max(46.5f * scale, layoutWidth * 0.055f);
        float menuWidth = Mth.clamp(layoutWidth * 0.25f, 294.5f * scale, 418.5f * scale);
        float restingMenuX = layoutWidth - rightMargin - menuWidth - menuCenterCorrectionX;
        float menuX = restingMenuX + (1.0f - intro) * 43.4f * scale + exit * 34.1f * scale;
        float titleCenterX = restingMenuX + menuWidth * 0.53f;
        float titleScale = 8.6025f * scale;
        float titleWidth = scene.scheduler().textMetrics().getWidth(clientName, titleScale, StaticFontLoader.CINZEL_DECORATIVE);
        float titleHeight = scene.scheduler().textMetrics().getHeight(titleScale, StaticFontLoader.CINZEL_DECORATIVE);
        float titleX = titleCenterX - titleWidth * 0.5f + (1.0f - intro) * 31.0f * scale + exit * 27.9f * scale;
        float titleY = Math.max(37.2f * scale, layoutHeight * 0.095f);
        float subtitleScale = 1.705f * scale;
        String subtitle = "COLUMBINA / " + Constants.VERSION;
        float subtitleWidth = scene.scheduler().textMetrics().getWidth(subtitle, subtitleScale);
        float subtitleX = titleCenterX - subtitleWidth * 0.5f + (1.0f - intro) * 31.0f * scale + exit * 27.9f * scale;
        float subtitleY = titleY + titleHeight - 6.2f * scale;
        float menuTop = Math.max(layoutHeight * 0.315f, subtitleY + 52.7f * scale);
        float footerReserve = 52.7f * scale;
        float availableRows = Math.max(1.0f, layoutHeight - menuTop - footerReserve);
        float rowStride = Math.min(80.6f * scale, availableRows / entries.size());
        float rowHeight = Math.min(58.9f * scale, rowStride * 0.78f);

        scene.beginFrame();
        UiTree tree = UiTree.build(scope -> scope.pushAbsolute(layoutX, layoutY, content -> {
            if (AssetManager.INSTANCE.isReisaReady()) {
                if (!reisaShutdownTexturesPrewarmed) {
                    prewarmReisaShutdownTextures(content);
                    reisaShutdownTexturesPrewarmed = true;
                }
                if (reisaShutdownStartMs >= 0L) {
                    drawReisaShutdown(content, layoutWidth, layoutHeight, scale, now, -layoutX, -layoutY, width, height);
                } else {
                    drawReisaGreeting(content, layoutWidth, layoutHeight, scale);
                }
            }

            Color titleStart = applyAlpha(new Color(158, 181, 222), visibility * 0.98f);
            Color titleEnd = applyAlpha(new Color(242, 247, 255), visibility);
            Color titleOuterGlow = applyAlpha(new Color(112, 158, 235), visibility * 0.66f);
            Color titleInnerGlow = applyAlpha(new Color(205, 226, 255), visibility * 0.90f);
            Color subtitleStart = applyAlpha(new Color(180, 198, 226), visibility * 0.92f);
            Color subtitleEnd = applyAlpha(new Color(224, 233, 246), visibility * 0.96f);
            Color subtitleGlow = applyAlpha(new Color(184, 207, 239), visibility * 0.52f);
            Color secondary = applyAlpha(new Color(178, 197, 224), visibility * 0.88f);

            content.layer(-50, layer -> {
                layer.blurredText(clientName, titleX, titleY, titleScale, titleOuterGlow, titleScale * 2.0f, 3, StaticFontLoader.CINZEL_DECORATIVE);
                layer.blurredText(clientName, titleX, titleY, titleScale, titleInnerGlow, titleScale * 0.90f, 4, StaticFontLoader.CINZEL_DECORATIVE);
                layer.gradientText(clientName, titleX, titleY, titleScale, titleStart, titleEnd, StaticFontLoader.CINZEL_DECORATIVE);
                layer.blurredText(subtitle, subtitleX, subtitleY, subtitleScale, subtitleScale * 1.25f, 2, subtitleGlow);
                layer.gradientText(subtitle, subtitleX, subtitleY, subtitleScale, subtitleStart, subtitleEnd);
            });

            for (int index = 0; index < entries.size(); index++) {
                MenuEntry entry = entries.get(index);
                float staged = Mth.clamp((intro - index * 0.065f) / 0.62f, 0.0f, 1.0f);
                float appear = Easing.EASE_OUT_CUBIC.getFunction().apply(staged);
                float rowY = menuTop + index * rowStride;
                float[] offsets = {-31.0f, 57.35f, 90.0f, 80.0f, 48.0f, -15.0f};
                float rowX = menuX + menuWidth * 0.20f + offsets[index] * scale + (1.0f - appear) * 27.9f * scale;
                entry.setBounds(rowX - 12.4f * scale, rowY - 6.2f * scale, menuWidth * 0.78f, rowHeight + 12.4f * scale);
                boolean hovered = entry.isHovered(layoutMouseX, layoutMouseY);
                entry.hoverProgress = Mth.lerp(hovered ? 0.26f : 0.16f, entry.hoverProgress, hovered ? 1.0f : 0.0f);
                float hover = entry.hoverProgress;
                Color labelStart = MD3Theme.lerp(
                        applyAlpha(new Color(226, 234, 247), appear * visibility * 0.96f),
                        applyAlpha(new Color(243, 247, 255), appear * visibility), hover);
                Color labelEnd = MD3Theme.lerp(
                        applyAlpha(new Color(177, 199, 232), appear * visibility * 0.94f),
                        applyAlpha(new Color(213, 226, 246), appear * visibility * 0.98f), hover);
                Color numberStart = MD3Theme.lerp(
                        applyAlpha(new Color(202, 216, 238), appear * visibility * 0.88f),
                        applyAlpha(new Color(232, 240, 252), appear * visibility * 0.96f), hover);
                Color numberEnd = MD3Theme.lerp(
                        applyAlpha(new Color(151, 177, 216), appear * visibility * 0.86f),
                        applyAlpha(new Color(195, 216, 242), appear * visibility * 0.94f), hover);
                Color labelGlow = MD3Theme.lerp(
                        applyAlpha(new Color(163, 196, 241), appear * visibility * 0.8f),
                        applyAlpha(new Color(205, 225, 252), appear * visibility * 1.0f), hover);
                Color numberGlow = MD3Theme.lerp(
                        applyAlpha(new Color(146, 181, 231), appear * visibility * 0.42f),
                        applyAlpha(new Color(191, 215, 247), appear * visibility * 0.64f), hover);
                Color numberShadow = applyAlpha(new Color(3, 7, 20), appear * visibility * (0.48f + hover * 0.06f));
                float labelScale = (2.976f + hover * 0.124f) * scale;
                float textHeight = scene.scheduler().textMetrics().getHeight(labelScale);
                float textY = rowY + (rowHeight - textHeight) * 0.5f;
                float numberScale = (1.829f + hover * 0.0775f) * scale;
                float numberHeight = scene.scheduler().textMetrics().getHeight(numberScale);
                float numberY = rowY + (rowHeight - numberHeight) * 0.5f;
                float numberShadowOffset = 6.2f * scale;
                float numberShadowBlur = 8.9125f * scale;

                String numberLabel = "0" + (index + 1);
                content.layer(-50, layer -> {
                    layer.blurredText(numberLabel, rowX - numberShadowOffset, numberY, numberScale, numberShadowBlur, 2, numberShadow);
                    layer.blurredText(numberLabel, rowX + numberShadowOffset, numberY, numberScale, numberShadowBlur, 2, numberShadow);
                    layer.blurredText(numberLabel, rowX, numberY - numberShadowOffset, numberScale, numberShadowBlur, 2, numberShadow);
                    layer.blurredText(numberLabel, rowX, numberY + numberShadowOffset, numberScale, numberShadowBlur, 2, numberShadow);
                    layer.blurredText(numberLabel, rowX, numberY, numberScale, numberShadowBlur, 2, numberShadow);
                    layer.blurredText(numberLabel, rowX, numberY, numberScale, numberScale * 1.15f, 2, numberGlow);
                    layer.gradientText(numberLabel, rowX, numberY, numberScale, numberStart, numberEnd);
                    String labelText = localizedTitle(entry.title);
                    layer.blurredText(labelText, rowX + 46.5f * scale, textY, labelScale, labelScale * 1.8f, 2, labelGlow);
                    layer.gradientText(labelText, rowX + 46.5f * scale, textY, labelScale, labelStart, labelEnd);
                });
            }

            float footerY = layoutHeight - Math.max(27.9f * scale, layoutHeight * 0.045f);
            String user = minecraft.getUser().getName();
            content.layer(-50, layer -> {
                float userWidth = scene.scheduler().textMetrics().getWidth(user, 1.55f * scale);
                float userX = layoutWidth - rightMargin - userWidth + 50.0f * scale - menuCenterCorrectionX;
                layer.blurredText(user, userX, footerY - 6.2f * scale, 1.55f * scale, 3.1f * scale, 2, applyAlpha(secondary, 0.42f));
                layer.text(user, userX, footerY - 6.2f * scale, 1.55f * scale, secondary);
            });
        }));
        scene.submit(UiLayer.CONTENT, tree);
        scene.endFrame();
    }

    private void drawClassicMenu(int mouseX, int mouseY) {
        long now = Util.getMillis();
        float introProgress = Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp((now - introStartMs) / 650.0f, 0.0f, 1.0f));
        int width = LuminRenderSystem.getScaledWidthInt();
        int height = LuminRenderSystem.getScaledHeightInt();
        float shutdownElapsed = reisaShutdownStartMs < 0L
                ? 0.0f
                : now - reisaShutdownStartMs;
        float menuVisibility = 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp(shutdownElapsed / REISA_SHUTDOWN_MENU_FADE_DURATION_MS, 0.0f, 1.0f));
        int buttonCount = entries.size();
        int gapCount = Math.max(0, buttonCount - 1);
        float scale = resolutionScale(width, height);
        int layoutWidth = Math.round(MENU_REFERENCE_WIDTH * scale);
        int layoutHeight = Math.round(MENU_REFERENCE_HEIGHT * scale);
        float layoutX = (width - layoutWidth) * 0.5f;
        float layoutY = (height - layoutHeight) * 0.5f;
        float layoutMouseX = mouseX - layoutX;
        float layoutMouseY = mouseY - layoutY;

        float titleX = Math.max(18.6f * scale, layoutWidth / 15.0f);
        float titleY = Math.max(12.4f * scale, titleX * 0.5f);
        float titleScale = 3.658f * scale;
        float subtitleScale = 0.961f * scale;
        float titleSubtitleGap = 18.6f * scale;
        float titleAccentGap = 9.3f * scale;
        float titleAccentWidth = 105.4f * scale;
        float titleAccentHeight = Math.max(1.6f, 2.79f * scale);

        float rowInset = Math.clamp(21.7f * scale, layoutWidth / 12.0f, layoutWidth * 0.5f);
        float availableRowWidth = Math.max(0.0f, layoutWidth - rowInset * 2.0f);
        float minButtonWidth = 65.1f * scale;
        float buttonGap = gapCount == 0 ? 0.0f : Math.clamp((availableRowWidth - buttonCount * minButtonWidth) / gapCount, 0.0f, 15.5f * scale);
        float maxButtonWidth = Math.max(0.0f, (availableRowWidth - gapCount * buttonGap) / Math.max(1, buttonCount));
        float buttonWidth = Math.min(173.6f * scale, maxButtonWidth);
        float totalButtonsWidth = buttonCount * buttonWidth + gapCount * buttonGap;
        float buttonsStartX = (layoutWidth - totalButtonsWidth) * 0.5f;
        float buttonLineHeight = Math.max(2.0f, 3.1f * scale);
        float buttonHitPaddingX = 12.4f * scale;
        float buttonHitPaddingTop = 9.3f * scale;
        float buttonHitHeight = 40.3f * scale;
        float buttonRevealDistance = 27.9f * scale;
        float preferredButtonTextScale = 1.395f * scale;
        float buttonTextOffsetY = 8.525f * scale;
        float targetButtonsY = layoutHeight - Math.min((layoutWidth + layoutHeight * 2.0f) / 25.0f, 83.7f * scale);
        float buttonsY = Math.min(targetButtonsY, layoutHeight - buttonHitHeight + buttonHitPaddingTop);

        Color titleColor = applyAlpha(new Color(230, 224, 233), 0.96f * menuVisibility);
        Color subtitleColor = applyAlpha(new Color(202, 196, 208), 0.90f * menuVisibility);
        Color accentColor = applyAlpha(new Color(208, 188, 255), 0.95f * menuVisibility);

        String title = Constants.NAME.toUpperCase();
        String subtitle = Constants.VERSION;

        float titleHeight = scene.scheduler().textMetrics().getHeight(titleScale, StaticFontLoader.JURA_LIGHT);
        float subtitleY = titleY + titleHeight + titleSubtitleGap;

        UiTree tree = UiTree.build(scope -> scope.pushAbsolute(layoutX, layoutY, content -> {
            if (!reisaShutdownTexturesPrewarmed) {
                prewarmReisaShutdownTextures(content);
                reisaShutdownTexturesPrewarmed = true;
            }
            if (reisaShutdownStartMs >= 0L) {
                drawReisaShutdown(content, layoutWidth, layoutHeight, scale, now, -layoutX, -layoutY, width, height);
            } else {
                drawReisaGreeting(content, layoutWidth, layoutHeight, scale);
            }
            content.layer(0, layer -> layer.rect(titleX, titleY + titleHeight + titleAccentGap, titleAccentWidth, titleAccentHeight, accentColor));
            content.layer(10, layer -> {
                layer.text(title, titleX, titleY, titleScale, titleColor, StaticFontLoader.JURA_LIGHT);
                layer.text(subtitle, titleX, subtitleY, subtitleScale, subtitleColor);
            });
            for (int index = 0; index < entries.size(); index++) {
                MenuEntry entry = entries.get(index);
                float staged = Mth.clamp((introProgress - index * 0.08f) / 0.52f, 0.0f, 1.0f);
                float appear = Easing.EASE_OUT_CUBIC.getFunction().apply(staged) * menuVisibility;
                if (appear <= 0.001f) {
                    entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
                    continue;
                }

                float drawX = buttonsStartX + index * (buttonWidth + buttonGap);
                float drawY = buttonsY + (1.0f - appear) * buttonRevealDistance;
                boolean hovered = entry.isHovered(layoutMouseX, layoutMouseY);
                entry.hoverProgress = Mth.lerp(hovered ? 0.24f : 0.16f, entry.hoverProgress, hovered ? 1.0f : 0.0f);

                float hover = entry.hoverProgress;
                float buttonY = drawY - hover * 3.875f * scale;
                entry.setBounds(
                        drawX - buttonHitPaddingX,
                        buttonY - buttonHitPaddingTop,
                        buttonWidth + buttonHitPaddingX * 2.0f,
                        buttonHitHeight
                );

                Color lineBase = applyAlpha(new Color(147, 143, 153), 0.70f * appear);
                Color lineHover = applyAlpha(new Color(208, 188, 255), 0.98f * appear);
                Color labelColor = MD3Theme.lerp(
                        applyAlpha(new Color(230, 224, 233), 0.94f * appear),
                        applyAlpha(new Color(234, 221, 255), 0.98f * appear),
                        hover * 0.68f
                );

                content.layer(0, layer -> {
                    layer.rect(drawX + 1.55f * scale, buttonY + 1.55f * scale, buttonWidth + scale * 0.775f, buttonLineHeight + 1.55f * scale, applyAlpha(MD3Theme.SURFACE, 0.70f * appear));
                    layer.rect(drawX, buttonY, buttonWidth, buttonLineHeight, MD3Theme.lerp(lineBase, lineHover, hover));
                });

                String label = localizedTitle(entry.title);
                float labelWidth = scene.scheduler().textMetrics().getWidth(label, preferredButtonTextScale);
                float buttonTextScale = labelWidth > buttonWidth && labelWidth > 0.0f
                        ? preferredButtonTextScale * buttonWidth / labelWidth
                        : preferredButtonTextScale;
                float textY = buttonY + buttonTextOffsetY;
                content.layer(10, layer -> layer.text(label, drawX, textY, buttonTextScale, labelColor));
            }
        }));

        scene.submit(UiLayer.CONTENT, tree);
    }

    private void drawReisaGreeting(UiTree.Scope scope, int width, int height, float scale) {
        if (reisaGreetingStartMs < 0L) return;

        long now = Util.getMillis();
        long elapsed = Math.max(0L, now - reisaGreetingStartMs);
        long exitElapsed = reisaExitStartMs < 0L ? -1L : Math.max(0L, now - reisaExitStartMs);
        float entrance = Mth.clamp(elapsed / (float) REISA_ENTRANCE_DURATION_MS, 0.0f, 1.0f);
        float slide = Easing.EASE_OUT_BACK.getFunction().apply(entrance);
        float entranceUnfold = 0.04f + 0.96f * Easing.EASE_OUT_CUBIC.getFunction()
                .apply(Mth.clamp((elapsed - 60.0f) / 760.0f, 0.0f, 1.0f));
        float poseProgress = exitElapsed < 0L
                ? 0.0f
                : Mth.clamp((exitElapsed - REISA_EXIT_POSE_DELAY_MS) / (float) REISA_EXIT_POSE_DURATION_MS, 0.0f, 1.0f);
        float poseEase = Easing.EASE_IN_OUT_CUBIC.getFunction().apply(poseProgress);
        float moveProgress = exitElapsed < 0L
                ? 0.0f
                : Mth.clamp((exitElapsed - REISA_EXIT_MOVE_DELAY_MS) / (float) REISA_EXIT_MOVE_DURATION_MS, 0.0f, 1.0f);
        float moveEase = Easing.EASE_IN_CUBIC.getFunction().apply(moveProgress);
        float liftEase = Easing.EASE_IN_OUT_CUBIC.getFunction().apply(moveProgress);
        float fadeProgress = Mth.clamp((moveProgress - 0.25f) / 0.75f, 0.0f, 1.0f);
        float fadeEase = Easing.EASE_OUT_CUBIC.getFunction().apply(fadeProgress);
        float imageHeight = Math.min(height * 0.78f, width * 0.58f);
        float imageWidth = imageHeight * REISA_ASPECT_RATIO;
        float targetX = width - imageWidth - Math.max(4.0f, 15.5f * scale);
        float entranceX = Mth.lerp(slide, width + imageWidth * 0.08f, targetX);
        float exitDistance = Math.max(111.6f * scale, imageWidth * 0.34f);
        float imageX = entranceX + exitDistance * moveEase;
        float bob = entrance >= 1.0f && exitElapsed < 0L
                ? (float) Math.sin((elapsed - REISA_ENTRANCE_DURATION_MS) * 0.0024f) * 2.17f * scale
                : 0.0f;
        float baseImageY = height - imageHeight + 6.2f * scale;
        float imageY = baseImageY + bob - 18.6f * scale * liftEase;
        float entranceAlpha = Easing.EASE_OUT_CUBIC.getFunction()
                .apply(Mth.clamp(elapsed / 240.0f, 0.0f, 1.0f));
        float imageAlpha = entranceAlpha * (1.0f - fadeEase);
        float uniformScale = Mth.lerp(liftEase, 1.0f, 0.97f);
        float drawWidth = imageWidth * uniformScale;
        float drawHeight = imageHeight * uniformScale;
        float drawX = imageX + (imageWidth - drawWidth) * 0.5f;
        float drawY = imageY + (imageHeight - drawHeight) * 0.5f;

        float baseProgress = exitElapsed < 0L
                ? 0.0f
                : Mth.clamp((exitElapsed - REISA_EXIT_BASE_DELAY_MS) / (float) REISA_EXIT_BASE_DURATION_MS, 0.0f, 1.0f);
        float baseWidth = 1.0f - Easing.EASE_IN_CUBIC.getFunction().apply(baseProgress);
        float baseAlpha = entranceAlpha * (exitElapsed < 0L
                ? entranceUnfold
                : 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(baseProgress));

        drawReisaFloatingBase(scope, height, drawX, drawWidth, baseAlpha, baseWidth, scale);

        if (exitElapsed >= 0L) {
            drawReisaExitAfterimages(scope, drawX, drawY, drawWidth, drawHeight, imageAlpha, poseEase, moveProgress, scale);
            if (poseEase < 0.999f) {
                drawReisa(scope, reisaWelcomeTexture(), drawX, drawY, drawWidth, drawHeight, imageAlpha);
                if (poseEase > 0.001f) {
                    drawReisaPoseOverlay(scope, drawX, drawY, drawWidth, drawHeight, imageAlpha * poseEase);
                }
            } else {
                drawReisa(scope, reisaExitTexture(), drawX, drawY, drawWidth, drawHeight, imageAlpha);
            }
        } else {
            if (elapsed <= REISA_ENTRANCE_DURATION_MS) {
                prewarmReisaExitTexture(scope);
            }
            drawReisaFoldedPage(scope, imageX, imageY, imageWidth, imageHeight, entranceUnfold, imageAlpha, scale);
        }

        drawReisaGreetingBubble(scope, elapsed, exitElapsed, width, drawX, drawY, drawHeight, scale);
    }

    private void drawReisaShutdown(UiTree.Scope scope, int width, int height, float scale, long now, float maskX, float maskY, int maskWidth, int maskHeight) {
        long elapsed = Math.max(0L, now - reisaShutdownStartMs);
        long exitElapsed = reisaShutdownExitStartMs < 0L ? -1L : Math.max(0L, now - reisaShutdownExitStartMs);
        float entranceProgress = Mth.clamp((elapsed - REISA_SHUTDOWN_ENTRANCE_DELAY_MS) / (float) REISA_SHUTDOWN_ENTRANCE_DURATION_MS, 0.0f, 1.0f);
        float entranceEase = Easing.EASE_OUT_BACK.getFunction().apply(entranceProgress);
        float exitProgress = exitElapsed < 0L ? 0.0f : Mth.clamp(exitElapsed / (float) REISA_SHUTDOWN_EXIT_DURATION_MS, 0.0f, 1.0f);
        float exitEase = Easing.EASE_IN_CUBIC.getFunction().apply(exitProgress);
        float imageHeight = Math.min(height * 0.80f, width * 0.60f);
        float imageWidth = imageHeight * REISA_ASPECT_RATIO;
        float targetX = width - imageWidth - Math.max(4.0f, 15.5f * scale);
        float imageX = Mth.lerp(entranceEase, width + imageWidth * 0.12f, targetX) + exitEase * (imageWidth * 0.56f + 65.1f * scale);
        float bob = entranceProgress >= 1.0f && exitProgress <= 0.0f ? (float) Math.sin((elapsed - REISA_SHUTDOWN_ENTRANCE_DURATION_MS) * 0.0031f) * 2.48f * scale : 0.0f;
        float imageY = height - imageHeight + 6.2f * scale + bob - exitEase * 43.4f * scale;
        float imageScale = Mth.lerp(exitEase, 1.0f, 0.94f);
        float drawWidth = imageWidth * imageScale;
        float drawHeight = imageHeight * imageScale;
        float drawX = imageX + (imageWidth - drawWidth) * 0.5f;
        float drawY = imageY + (imageHeight - drawHeight) * 0.5f;
        float imageAlpha = Easing.EASE_OUT_CUBIC.getFunction().apply(entranceProgress) * (1.0f - exitEase);

        scope.layer(-40, layer -> layer.rect(maskX, maskY, maskWidth, maskHeight, applyAlpha(new Color(8, 9, 16), imageAlpha * 0.62f)));

        drawReisaShutdownTrails(scope, drawX, drawY, drawWidth, drawHeight, exitProgress, scale);
        drawReisa(scope, reisaShutdownTexture(elapsed, exitElapsed >= 0L), drawX, drawY, drawWidth, drawHeight, imageAlpha);
        drawReisaShutdownBubble(scope, elapsed, exitElapsed, width, drawX, drawY, drawHeight, scale);
    }

    private void drawReisaShutdownTrails(UiTree.Scope scope, float imageX, float imageY, float imageWidth, float imageHeight, float exitProgress, float scale) {
        float trailEnvelope = (float) Math.sin(exitProgress * Math.PI);
        if (trailEnvelope <= 0.001f) return;
        float startX = imageX + imageWidth * 0.34f;
        float maxWidth = Math.max(0.0f, LuminRenderSystem.getScaledWidth() - startX);
        Color bright = applyAlpha(new Color(235, 211, 255), trailEnvelope * 0.62f);
        Color soft = applyAlpha(new Color(183, 146, 245), 0.0f);
        scope.layer(-22, layer -> {
            layer.rectHorizontalGradient(startX, imageY + imageHeight * 0.24f, maxWidth, Math.max(1.0f, 2.325f * scale), bright, soft);
            layer.rectHorizontalGradient(startX + imageWidth * 0.08f, imageY + imageHeight * 0.49f, maxWidth * 0.78f, Math.max(1.0f, 1.55f * scale), applyAlpha(bright, 0.68f), soft);
            layer.rectHorizontalGradient(startX - imageWidth * 0.03f, imageY + imageHeight * 0.71f, maxWidth * 0.88f, Math.max(1.0f, 1.86f * scale), applyAlpha(bright, 0.48f), soft);
        });
    }

    private void drawReisaShutdownBubble(UiTree.Scope scope, long elapsed, long exitElapsed, int width, float imageX, float imageY, float imageHeight, float scale) {
        float bubbleIn = Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp((elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS) / 320.0f, 0.0f, 1.0f));
        float bubbleOut = exitElapsed < 0L ? 1.0f : 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp(exitElapsed / 300.0f, 0.0f, 1.0f));
        float visibility = Math.min(bubbleIn, bubbleOut);
        if (visibility <= 0.001f) return;

        String name = "UZAWA REISA";
        String farewell = EpsilonTranslations.Gui.MAINMENU_REISA_FAREWELL.getTranslatedName();
        float contentInset = 20.15f * scale;
        float maxBubbleWidth = Math.clamp(348.75f * scale, 161.2f * scale, width * 0.44f);
        float availableTextWidth = Math.max(1.0f, maxBubbleWidth - contentInset * 2.0f);
        float nameScale = 0.961f * scale;
        float messageScale = fitTextScale(farewell, 1.054f * scale, availableTextWidth);
        float bubbleWidth = Math.min(maxBubbleWidth, Math.max(scene.scheduler().textMetrics().getWidth(name, nameScale, StaticFontLoader.JURA_LIGHT), scene.scheduler().textMetrics().getWidth(farewell, messageScale)) + contentInset * 2.0f);
        float topPadding = 11.625f * scale;
        float rowGap = 3.875f * scale;
        float bottomPadding = 13.95f * scale;
        float nameHeight = scene.scheduler().textMetrics().getHeight(nameScale, StaticFontLoader.JURA_LIGHT);
        float messageHeight = scene.scheduler().textMetrics().getHeight(messageScale);
        float bubbleHeight = topPadding + nameHeight + rowGap + messageHeight + bottomPadding;
        float bubbleX = Mth.clamp(imageX - bubbleWidth * 0.72f, 18.6f * scale, width - bubbleWidth - 18.6f * scale);
        float bubbleY = imageY + imageHeight * 0.22f + (1.0f - bubbleIn) * 18.6f * scale;
        float radius = 9.3f * scale;
        float speechProgress = Mth.clamp((elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS) / (float) (REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS - REISA_SHUTDOWN_BUBBLE_DELAY_MS), 0.0f, 1.0f);

        Color surface = applyAlpha(new Color(29, 31, 42), visibility * 0.97f);
        Color outline = applyAlpha(new Color(229, 194, 255), visibility * 0.54f);
        Color nameColor = applyAlpha(new Color(252, 224, 255), visibility);
        Color accentColor = applyAlpha(new Color(222, 169, 255), visibility * 0.98f);
        Color textColor = applyAlpha(new Color(244, 241, 250), visibility);
        float textX = bubbleX + contentInset;
        float contentOffsetY = -3.1f * scale;
        float nameY = bubbleY + topPadding + contentOffsetY;
        float messageY = nameY + nameHeight + rowGap;
        float progressBarHeight = Math.max(3.0f, 4.65f * scale);
        float progressBarY = bubbleY + bubbleHeight - 12.4f * scale;

        scope.layer(20, layer -> {
            layer.shadow(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, REISA_BUBBLE_SHADOW_BLUR * scale, applyAlpha(new Color(0, 0, 0), visibility * REISA_SHUTDOWN_BUBBLE_SHADOW_ALPHA));
            layer.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, surface);
            layer.outline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, Math.max(1.0f, 1.55f * scale), outline);
            layer.roundRect(bubbleX + 10.075f * scale, nameY, 3.1f * scale, nameHeight, 1.55f * scale, accentColor);
            layer.roundRect(bubbleX + contentInset, progressBarY, (bubbleWidth - contentInset * 2.0f) * speechProgress, progressBarHeight, progressBarHeight * 0.5f, applyAlpha(accentColor, 0.62f));
        });
        scope.layer(21, layer -> {
            layer.text(name, textX, nameY, nameScale, nameColor, StaticFontLoader.JURA_LIGHT);
            layer.text(farewell, textX, messageY, messageScale, textColor);
        });
    }

    private Identifier reisaShutdownTexture(long elapsed, boolean exiting) {
        if (exiting) return reisaShutdownFinalTexture();
        if (elapsed < REISA_SHUTDOWN_BUBBLE_DELAY_MS) return reisaShutdownEntranceTexture();

        long speechElapsed = elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS;
        return (speechElapsed / 145L & 1L) == 0L ? reisaWelcomeTexture() : reisaExitTexture();
    }

    private void prewarmReisaShutdownTextures(UiTree.Scope scope) {
        Color transparent = applyAlpha(Color.WHITE, 0.0f);
        Identifier entrance = reisaShutdownEntranceTexture();
        Identifier last = reisaShutdownFinalTexture();
        scope.layer(-60, layer -> {
            if (entrance != null) {
                layer.texture(entrance, -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, transparent, true);
            }
            if (last != null) {
                layer.texture(last, -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, transparent, true);
            }
        });
    }

    private void drawReisaFloatingBase(UiTree.Scope scope, int height, float imageX, float imageWidth, float alpha, float widthProgress, float scale) {
        if (alpha <= 0.001f || widthProgress <= 0.001f) return;

        float centerX = imageX + imageWidth * 0.55f;
        float lineWidth = imageWidth * widthProgress;
        float lineHeight = Math.max(1.0f, 1.7825f * scale);
        float lineY = height - lineHeight;
        float glowHeight = 52.7f * scale;

        Color transparent = applyAlpha(new Color(213, 177, 255), 0.0f);
        Color glow = applyAlpha(new Color(205, 162, 255), alpha * 0.30f);
        Color line = applyAlpha(new Color(239, 220, 255), alpha * 0.78f);

        scope.layer(-24, layer -> layer.rectVerticalGradient(
                centerX - lineWidth * 0.5f, lineY - glowHeight,
                lineWidth, glowHeight, transparent, glow
        ));
        scope.layer(-23, layer -> layer.roundRect(
                centerX - lineWidth * 0.5f, lineY,
                lineWidth, lineHeight, lineHeight * 0.5f, line
        ));
    }

    private void drawReisaExitAfterimages(UiTree.Scope scope, float imageX, float imageY, float imageWidth, float imageHeight, float alpha, float poseProgress, float moveProgress, float scale) {
        float trailEnvelope = (float) Math.sin(moveProgress * Math.PI);
        float trailAlpha = alpha * poseProgress * trailEnvelope;
        if (trailAlpha <= 0.001f) return;

        Color farTrail = applyAlpha(new Color(216, 185, 255), trailAlpha * 0.08f);
        Color nearTrail = applyAlpha(new Color(228, 205, 255), trailAlpha * 0.14f);
        Identifier exit = reisaExitTexture();
        if (exit == null) return;
        scope.layer(-22, layer -> {
            layer.texture(exit, imageX - 24.8f * scale, imageY + 2.325f * scale,
                    imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, farTrail, true);
            layer.texture(exit, imageX - 12.4f * scale, imageY + 1.1625f * scale,
                    imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, nearTrail, true);
        });
    }

    private void drawReisaPoseOverlay(UiTree.Scope scope, float imageX, float imageY, float imageWidth, float imageHeight, float alpha) {
        Identifier exit = reisaExitTexture();
        if (exit == null) return;
        scope.layer(-20, layer -> layer.texture(exit, imageX, imageY,
                imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f,
                applyAlpha(Color.WHITE, alpha), true));
    }

    private void prewarmReisaExitTexture(UiTree.Scope scope) {
        Identifier exit = reisaExitTexture();
        if (exit == null) return;
        scope.layer(-30, layer -> layer.texture(exit,
                -1.0f, -1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                applyAlpha(Color.WHITE, 0.0f), true));
    }

    private void drawReisa(UiTree.Scope scope, Identifier texture, float imageX, float imageY, float imageWidth, float imageHeight, float alpha) {
        if (texture == null) return;
        scope.layer(-21, layer -> layer.texture(texture, imageX, imageY, imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, applyAlpha(Color.WHITE, alpha), true));
    }

    private void drawReisaFoldedPage(UiTree.Scope scope, float imageX, float imageY, float imageWidth, float imageHeight, float unfold, float alpha, float scale) {
        Identifier welcome = reisaWelcomeTexture();
        if (welcome == null) return;
        if (unfold >= 0.999f) {
            drawReisa(scope, welcome, imageX, imageY, imageWidth, imageHeight, alpha);
            return;
        }

        float hingeX = imageX + imageWidth;
        for (int index = 0; index < REISA_PAGE_SLICES; index++) {
            float u0 = index / (float) REISA_PAGE_SLICES;
            float u1 = (index + 1.0f) / REISA_PAGE_SLICES;
            float center = (u0 + u1) * 0.5f;
            float sliceX = hingeX - imageWidth * (1.0f - u0) * unfold;
            float sliceRight = hingeX - imageWidth * (1.0f - u1) * unfold;
            float curl = (float) Math.sin(center * Math.PI) * (1.0f - unfold) * 20.15f * scale;
            float shade = 1.0f - (1.0f - unfold) * (0.18f + 0.38f * (float) Math.sin(center * Math.PI));
            Color sliceColor = applyAlpha(Color.WHITE, alpha * shade);
            scope.layer(-20, layer -> layer.texture(welcome, sliceX, imageY + curl,
                    Math.max(0.5f, sliceRight - sliceX + 0.35f), imageHeight - curl * 0.25f,
                    u0, 0.0f, u1, 1.0f, sliceColor, true));
        }

        float foldEdgeX = hingeX - imageWidth * unfold;
        float edgeAlpha = alpha * (1.0f - unfold) * 0.72f;
        scope.layer(-19, layer -> layer.rect(foldEdgeX, imageY + imageHeight * 0.05f,
                Math.max(1.0f, 3.875f * scale), imageHeight * 0.90f,
                applyAlpha(new Color(245, 224, 255), edgeAlpha)));
    }

    private void drawReisaGreetingBubble(UiTree.Scope scope, long elapsed, long exitElapsed, int width, float imageX, float imageY, float imageHeight, float scale) {
        float bubbleIn = Easing.EASE_OUT_CUBIC.getFunction()
                .apply(Mth.clamp((elapsed - REISA_BUBBLE_DELAY_MS) / 380.0f, 0.0f, 1.0f));
        float bubbleExit = exitElapsed < 0L
                ? 0.0f
                : Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp(exitElapsed / 260.0f, 0.0f, 1.0f));
        float bubbleOut = 1.0f - bubbleExit;
        float visibility = Math.min(bubbleIn, bubbleOut);
        if (visibility <= 0.001f) return;

        String name = "UZAWA REISA";
        String greeting = EpsilonTranslations.Gui.MAINMENU_REISA_GREETING.getTranslatedName();
        float contentInset = 20.15f * scale;
        float maxBubbleWidth = Math.clamp(317.75f * scale, 148.8f * scale, width * 0.40f);
        float minBubbleWidth = Math.min(224.75f * scale, maxBubbleWidth);
        float availableTextWidth = Math.max(1.0f, maxBubbleWidth - contentInset * 2.0f);
        float nameScale = 0.961f * scale;
        float messageScale = fitTextScale(greeting, 1.054f * scale, availableTextWidth);
        float nameWidth = scene.scheduler().textMetrics().getWidth(name, nameScale, StaticFontLoader.JURA_LIGHT);
        float messageWidth = scene.scheduler().textMetrics().getWidth(greeting, messageScale);
        float bubbleWidth = Mth.clamp(Math.max(nameWidth, messageWidth) + contentInset * 2.0f, minBubbleWidth, maxBubbleWidth);
        float topPadding = 11.625f * scale;
        float rowGap = 3.875f * scale;
        float bottomPadding = 12.4f * scale;
        float nameHeight = scene.scheduler().textMetrics().getHeight(nameScale, StaticFontLoader.JURA_LIGHT);
        float messageHeight = scene.scheduler().textMetrics().getHeight(messageScale);
        float bubbleHeight = topPadding + nameHeight + rowGap + messageHeight + bottomPadding;
        float bubbleX = Mth.clamp(imageX - bubbleWidth * 0.72f, 18.6f * scale, width - bubbleWidth - 18.6f * scale);
        float bubbleY = imageY + imageHeight * 0.23f + (1.0f - bubbleIn) * 18.6f * scale - bubbleExit * 15.5f * scale;
        float radius = 9.3f * scale;
        float alpha = visibility * 0.96f;

        Color surface = applyAlpha(new Color(29, 31, 42), alpha);
        Color outline = applyAlpha(new Color(229, 194, 255), visibility * 0.48f);
        Color nameColor = applyAlpha(new Color(252, 224, 255), visibility);
        Color accentColor = applyAlpha(new Color(222, 169, 255), visibility * 0.96f);
        Color textColor = applyAlpha(new Color(244, 241, 250), visibility);

        float textX = bubbleX + contentInset;
        float nameY = bubbleY + topPadding;
        float messageY = nameY + nameHeight + rowGap;

        scope.layer(20, layer -> {
            layer.shadow(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, REISA_BUBBLE_SHADOW_BLUR * scale, applyAlpha(new Color(0, 0, 0), visibility * REISA_GREETING_BUBBLE_SHADOW_ALPHA));
            layer.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, surface);
            layer.outline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, Math.max(1.0f, 1.55f * scale), outline);
            layer.roundRect(bubbleX + 10.075f * scale, nameY, 3.1f * scale, nameHeight, 1.55f * scale, accentColor);
            layer.roundRect(bubbleX + bubbleWidth - 20.15f * scale, bubbleY + bubbleHeight - 3.1f * scale, 15.5f * scale, 13.95f * scale, 3.875f * scale, surface);
        });
        scope.layer(21, layer -> {
            layer.text(name, textX, nameY, nameScale, nameColor, StaticFontLoader.JURA_LIGHT);
            layer.text(greeting, textX, messageY, messageScale, textColor);
        });
    }

    private float fitTextScale(String text, float preferredScale, float maxWidth) {
        float textWidth = scene.scheduler().textMetrics().getWidth(text, preferredScale);
        return textWidth > maxWidth && textWidth > 0.0f ? preferredScale * maxWidth / textWidth : preferredScale;
    }

    private static float resolutionScale(int width, int height) {
        return Math.min(width / MENU_REFERENCE_WIDTH, height / MENU_REFERENCE_HEIGHT);
    }

    private static String localizedTitle(String title) {
        return switch (title) {
            case "Singleplayer" -> EpsilonTranslations.Gui.MAINMENU_SINGLEPLAYER.getTranslatedName();
            case "Multiplayer" -> EpsilonTranslations.Gui.MAINMENU_MULTIPLAYER.getTranslatedName();
            case "Accounts" -> EpsilonTranslations.Gui.MAINMENU_ACCOUNTS.getTranslatedName();
            case "Options" -> EpsilonTranslations.Gui.MAINMENU_OPTIONS.getTranslatedName();
            case "Quit" -> EpsilonTranslations.Gui.MAINMENU_QUIT.getTranslatedName();
            default -> title;
        };
    }

    private static Color applyAlpha(Color color, float alphaFactor) {
        float factor = Mth.clamp(alphaFactor, 0.0f, 1.0f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * factor));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (reisaShutdownStartMs >= 0L) return true;
        if (event.button() == 0) {
            MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
            int width = LuminRenderSystem.getScaledWidthInt();
            int height = LuminRenderSystem.getScaledHeightInt();
            float scale = resolutionScale(width, height);
            int layoutWidth = Math.round(MENU_REFERENCE_WIDTH * scale);
            int layoutHeight = Math.round(MENU_REFERENCE_HEIGHT * scale);
            boolean columbia = ClientSetting.INSTANCE.mainMenuStyle.is(ClientSetting.MainMenuStyle.Columbina);
            float layoutX = columbia ? width - layoutWidth : (width - layoutWidth) * 0.5f;
            float layoutY = columbia ? height - layoutHeight : (height - layoutHeight) * 0.5f;
            for (MenuEntry entry : entries) {
                if (entry.isHovered(epsilonEvent.x() - layoutX, epsilonEvent.y() - layoutY)) {
                    entry.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(LuminRenderSystem.toEpsilonMouseEvent(event), doubleClick);
    }

    @Override
    public void removed() {
        super.removed();
        if (!VideoPlayer.isStopped()) {
            VideoPlayer.pause();
        }
        initialized = false;
        if (reisaWelcomeSound != null) {
            minecraft.getSoundManager().stop(reisaWelcomeSound);
        }
        if (reisaShutdownSound != null) {
            minecraft.getSoundManager().stop(reisaShutdownSound);
        }
        clearReisaGreeting();
        clearReisaShutdown();
        if (backgroundRenderTarget != null) {
            backgroundRenderTarget.close();
            backgroundRenderTarget = null;
        }
        if (uiRenderTarget != null) {
            uiRenderTarget.close();
            uiRenderTarget = null;
        }
        scene.close();
    }

    private void clearReisaGreeting() {
        reisaGreetingStartMs = -1L;
        reisaExitStartMs = -1L;
        reisaWelcomeSound = null;
    }

    private void clearReisaShutdown() {
        reisaShutdownStartMs = -1L;
        reisaShutdownExitStartMs = -1L;
        reisaShutdownSound = null;
        reisaShutdownCommitted = false;
        reisaShutdownTexturesPrewarmed = false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class MenuEntry {
        private final String title;
        private final Runnable action;

        private float x;
        private float y;
        private float width;
        private float height;
        private float hoverProgress;

        private MenuEntry(String title, Runnable action) {
            this.title = title;
            this.action = action;
        }

        private void setBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

}
