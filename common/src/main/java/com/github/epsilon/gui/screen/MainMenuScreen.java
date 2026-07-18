package com.github.epsilon.gui.screen;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.shaders.GlslSandBox;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
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
    private static final float REISA_BUBBLE_SHADOW_BLUR = 14.0f;
    private static final float REISA_SHUTDOWN_BUBBLE_SHADOW_ALPHA = 0.60f;
    private static final float REISA_GREETING_BUBBLE_SHADOW_ALPHA = 0.58f;

    private static final Identifier REISA_WELCOME_TEXTURE = ResourceLocationUtils.getIdentifier("textures/gui/galgame/reisa_00.png");
    private static final Identifier REISA_EXIT_TEXTURE = ResourceLocationUtils.getIdentifier("textures/gui/galgame/reisa_09.png");
    private static final Identifier REISA_SHUTDOWN_ENTRANCE_TEXTURE = ResourceLocationUtils.getIdentifier("textures/gui/galgame/reisa_10.png");
    private static final Identifier REISA_SHUTDOWN_FINAL_TEXTURE = ResourceLocationUtils.getIdentifier("textures/gui/galgame/reisa_18.png");

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
        entries.add(new MenuEntry("Singleplayer", () -> minecraft.setScreen(new SelectWorldScreen(this))));
        entries.add(new MenuEntry("Multiplayer", () -> {
            Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.setScreen(screen);
        }));
        entries.add(new MenuEntry("GUI", () -> minecraft.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        })));
        entries.add(new MenuEntry("Options", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options, false))));
        entries.add(new MenuEntry("Quit", minecraft::stop));
    }

    @Override
    protected void init() {
        super.init();
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
        if (!ClientSetting.INSTANCE.showReisaOnStartup.getValue()) {
            reisaGreetingQueued = false;
            return;
        }
        reisaGreetingQueued = true;
        if (initialized && minecraft.screen == this) {
            startReisaGreeting();
        }
    }

    private void startReisaGreeting() {
        if (!ClientSetting.INSTANCE.showReisaOnStartup.getValue()) {
            clearReisaGreeting();
            return;
        }
        reisaGreetingQueued = false;
        reisaGreetingStartMs = Util.getMillis();
        reisaExitStartMs = -1L;
        reisaWelcomeSound = Managers.SOUND.playTracked(
                SoundKey.REISA_WELCOME,
                ClientSetting.INSTANCE.reisaVolume.getValue().floatValue()
        ).orElse(null);
    }

    public boolean requestShutdown() {
        if (!ClientSetting.INSTANCE.showReisaOnShutdown.getValue()) return false;
        if (!initialized || minecraft.screen != this || reisaShutdownCommitted) return false;
        if (reisaShutdownStartMs >= 0L) return true;

        if (reisaWelcomeSound != null) {
            minecraft.getSoundManager().stop(reisaWelcomeSound);
        }
        clearReisaGreeting();
        minecraft.getSoundManager().stop();
        reisaShutdownStartMs = Util.getMillis();
        reisaShutdownExitStartMs = -1L;
        reisaShutdownSound = Managers.SOUND.playTracked(
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
                boolean soundFinished = reisaShutdownSound != null
                        && !minecraft.getSoundManager().isActive(reisaShutdownSound);
                boolean fallbackFinished = reisaShutdownSound == null
                        && now - reisaShutdownStartMs >= REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS;
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

        backgroundRenderTarget.clear();
        backgroundRenderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(backgroundRenderTarget);

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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (uiRenderTarget == null) {
            uiRenderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu-ui", window.getWidth(), window.getHeight());
        }

        uiRenderTarget.clear();
        uiRenderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(uiRenderTarget);

        scene.beginFrame();
        drawMenu(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
        scene.endFrame();

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(uiRenderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawMenu(int mouseX, int mouseY) {
        long now = Util.getMillis();
        float introProgress = Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp((now - introStartMs) / 650.0f, 0.0f, 1.0f));
        int width = LuminRenderSystem.getScaledWidthInt();
        int height = LuminRenderSystem.getScaledHeightInt();
        float shutdownElapsed = reisaShutdownStartMs < 0L
                ? 0.0f
                : now - reisaShutdownStartMs;
        float menuVisibility = 1.0f - Easing.EASE_OUT_CUBIC.getFunction()
                .apply(Mth.clamp(shutdownElapsed / REISA_SHUTDOWN_MENU_FADE_DURATION_MS, 0.0f, 1.0f));
        int buttonCount = entries.size();
        int gapCount = Math.max(0, buttonCount - 1);
        float scale = Mth.clamp((width * 2.0f + height) / 900.0f + 0.08f, 0.72f, 1.24f);

        float titleX = Math.max(12.0f * scale, width / 15.0f);
        float titleY = Math.max(8.0f * scale, titleX * 0.5f);
        float titleScale = 2.36f * scale;
        float subtitleScale = 0.62f * scale;
        float titleSubtitleGap = 12.0f * scale;
        float titleAccentGap = 6.0f * scale;
        float titleAccentWidth = 68.0f * scale;
        float titleAccentHeight = Math.max(1.6f, 1.8f * scale);

        float rowInset = Math.clamp(14.0f * scale, width / 12.0f, width * 0.5f);
        float availableRowWidth = Math.max(0.0f, width - rowInset * 2.0f);
        float minButtonWidth = 42.0f * scale;
        float buttonGap = gapCount == 0 ? 0.0f : Math.clamp((availableRowWidth - buttonCount * minButtonWidth) / gapCount, 0.0f, 10.0f * scale);
        float maxButtonWidth = Math.max(0.0f, (availableRowWidth - gapCount * buttonGap) / Math.max(1, buttonCount));
        float buttonWidth = Math.min(112.0f * scale, maxButtonWidth);
        float totalButtonsWidth = buttonCount * buttonWidth + gapCount * buttonGap;
        float buttonsStartX = (width - totalButtonsWidth) * 0.5f;
        float buttonLineHeight = Math.max(2.0f, 2.0f * scale);
        float buttonHitPaddingX = 8.0f * scale;
        float buttonHitPaddingTop = 6.0f * scale;
        float buttonHitHeight = 26.0f * scale;
        float buttonRevealDistance = 18.0f * scale;
        float preferredButtonTextScale = 0.90f * scale;
        float buttonTextOffsetY = 5.5f * scale;
        float targetButtonsY = height - Math.min((width + height * 2.0f) / 25.0f, 54.0f * scale);
        float buttonsY = Math.min(targetButtonsY, height - buttonHitHeight + buttonHitPaddingTop);

        Color titleColor = applyAlpha(new Color(230, 224, 233), 0.96f * menuVisibility);
        Color subtitleColor = applyAlpha(new Color(202, 196, 208), 0.90f * menuVisibility);
        Color accentColor = applyAlpha(new Color(208, 188, 255), 0.95f * menuVisibility);

        String title = "EPSILON";
        String subtitle = Constants.VERSION;

        float titleHeight = scene.scheduler().textMetrics().getHeight(titleScale, StaticFontLoader.JURA_LIGHT);
        float subtitleY = titleY + titleHeight + titleSubtitleGap;

        UiTree tree = UiTree.build(scope -> {
            if (!reisaShutdownTexturesPrewarmed) {
                prewarmReisaShutdownTextures(scope);
                reisaShutdownTexturesPrewarmed = true;
            }
            if (reisaShutdownStartMs >= 0L) {
                drawReisaShutdown(scope, width, height, scale, now);
            } else {
                drawReisaGreeting(scope, width, height, scale);
            }
            scope.layer(0, layer -> layer.rect(titleX, titleY + titleHeight + titleAccentGap, titleAccentWidth, titleAccentHeight, accentColor));
            scope.layer(10, layer -> {
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
                boolean hovered = entry.isHovered(mouseX, mouseY);
                entry.hoverProgress = Mth.lerp(hovered ? 0.24f : 0.16f, entry.hoverProgress, hovered ? 1.0f : 0.0f);

                float hover = entry.hoverProgress;
                float buttonY = drawY - hover * 2.5f * scale;
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

                scope.layer(0, layer -> {
                    layer.rect(drawX + scale, buttonY + scale, buttonWidth + scale * 0.5f, buttonLineHeight + scale, applyAlpha(MD3Theme.SURFACE, 0.70f * appear));
                    layer.rect(drawX, buttonY, buttonWidth, buttonLineHeight, MD3Theme.lerp(lineBase, lineHover, hover));
                });

                String label = localizedTitle(entry.title);
                float labelWidth = scene.scheduler().textMetrics().getWidth(label, preferredButtonTextScale);
                float buttonTextScale = labelWidth > buttonWidth && labelWidth > 0.0f
                        ? preferredButtonTextScale * buttonWidth / labelWidth
                        : preferredButtonTextScale;
                float textY = buttonY + buttonTextOffsetY;
                scope.layer(10, layer -> layer.text(label, drawX, textY, buttonTextScale, labelColor));
            }
        });

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
        float imageHeight = Math.min(height * 0.96f, width * 0.72f);
        float imageWidth = imageHeight * REISA_ASPECT_RATIO;
        float targetX = width - imageWidth - Math.max(4.0f, 10.0f * scale);
        float entranceX = Mth.lerp(slide, width + imageWidth * 0.08f, targetX);
        float exitDistance = Math.max(72.0f * scale, imageWidth * 0.34f);
        float imageX = entranceX + exitDistance * moveEase;
        float bob = entrance >= 1.0f && exitElapsed < 0L
                ? (float) Math.sin((elapsed - REISA_ENTRANCE_DURATION_MS) * 0.0024f) * 1.4f * scale
                : 0.0f;
        float baseImageY = height - imageHeight + 4.0f * scale;
        float imageY = baseImageY + bob - 12.0f * scale * liftEase;
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
            drawReisaExitAfterimages(scope, drawX, drawY, drawWidth, drawHeight,
                    imageAlpha, poseEase, moveProgress, scale);
            if (poseEase < 0.999f) {
                drawReisa(scope, REISA_WELCOME_TEXTURE, drawX, drawY, drawWidth, drawHeight, imageAlpha);
                if (poseEase > 0.001f) {
                    drawReisaPoseOverlay(scope, drawX, drawY, drawWidth, drawHeight, imageAlpha * poseEase);
                }
            } else {
                drawReisa(scope, REISA_EXIT_TEXTURE, drawX, drawY, drawWidth, drawHeight, imageAlpha);
            }
        } else {
            if (elapsed <= REISA_ENTRANCE_DURATION_MS) {
                prewarmReisaExitTexture(scope);
            }
            drawReisaFoldedPage(scope, imageX, imageY, imageWidth, imageHeight, entranceUnfold, imageAlpha, scale);
        }

        drawReisaGreetingBubble(scope, elapsed, exitElapsed, width, drawX, drawY, drawWidth, drawHeight, scale);
    }

    private void drawReisaShutdown(UiTree.Scope scope, int width, int height, float scale, long now) {
        long elapsed = Math.max(0L, now - reisaShutdownStartMs);
        long exitElapsed = reisaShutdownExitStartMs < 0L ? -1L : Math.max(0L, now - reisaShutdownExitStartMs);
        float entranceProgress = Mth.clamp((elapsed - REISA_SHUTDOWN_ENTRANCE_DELAY_MS)
                / (float) REISA_SHUTDOWN_ENTRANCE_DURATION_MS, 0.0f, 1.0f);
        float entranceEase = Easing.EASE_OUT_BACK.getFunction().apply(entranceProgress);
        float exitProgress = exitElapsed < 0L
                ? 0.0f
                : Mth.clamp(exitElapsed / (float) REISA_SHUTDOWN_EXIT_DURATION_MS, 0.0f, 1.0f);
        float exitEase = Easing.EASE_IN_CUBIC.getFunction().apply(exitProgress);
        float dim = Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp(elapsed / 620.0f, 0.0f, 1.0f));

        scope.layer(-40, layer -> layer.rect(0.0f, 0.0f, width, height,
                applyAlpha(new Color(8, 9, 16), dim * 0.62f)));

        float imageHeight = Math.min(height * 0.98f, width * 0.74f);
        float imageWidth = imageHeight * REISA_ASPECT_RATIO;
        float targetX = width - imageWidth - Math.max(4.0f, 10.0f * scale);
        float imageX = Mth.lerp(entranceEase, width + imageWidth * 0.12f, targetX)
                + exitEase * (imageWidth * 0.56f + 42.0f * scale);
        float bob = entranceProgress >= 1.0f && exitProgress <= 0.0f
                ? (float) Math.sin((elapsed - REISA_SHUTDOWN_ENTRANCE_DURATION_MS) * 0.0031f) * 1.6f * scale
                : 0.0f;
        float imageY = height - imageHeight + 4.0f * scale + bob - exitEase * 28.0f * scale;
        float imageScale = Mth.lerp(exitEase, 1.0f, 0.94f);
        float drawWidth = imageWidth * imageScale;
        float drawHeight = imageHeight * imageScale;
        float drawX = imageX + (imageWidth - drawWidth) * 0.5f;
        float drawY = imageY + (imageHeight - drawHeight) * 0.5f;
        float imageAlpha = Easing.EASE_OUT_CUBIC.getFunction().apply(entranceProgress) * (1.0f - exitEase);

        drawReisaShutdownTrails(scope, drawX, drawY, drawWidth, drawHeight, exitProgress, scale);
        drawReisa(scope, reisaShutdownTexture(elapsed, exitElapsed >= 0L), drawX, drawY, drawWidth, drawHeight, imageAlpha);
        drawReisaShutdownBubble(scope, elapsed, exitElapsed, width, drawX, drawY, drawWidth, drawHeight, scale);
    }

    private void drawReisaShutdownTrails(UiTree.Scope scope, float imageX, float imageY,
                                         float imageWidth, float imageHeight, float exitProgress, float scale) {
        float trailEnvelope = (float) Math.sin(exitProgress * Math.PI);
        if (trailEnvelope <= 0.001f) return;

        float startX = imageX + imageWidth * 0.34f;
        float maxWidth = Math.max(0.0f, LuminRenderSystem.getScaledWidth() - startX);
        Color bright = applyAlpha(new Color(235, 211, 255), trailEnvelope * 0.62f);
        Color soft = applyAlpha(new Color(183, 146, 245), 0.0f);
        scope.layer(-22, layer -> {
            layer.rectHorizontalGradient(startX, imageY + imageHeight * 0.24f,
                    maxWidth, Math.max(1.0f, 1.5f * scale), bright, soft);
            layer.rectHorizontalGradient(startX + imageWidth * 0.08f, imageY + imageHeight * 0.49f,
                    maxWidth * 0.78f, Math.max(1.0f, scale), applyAlpha(bright, 0.68f), soft);
            layer.rectHorizontalGradient(startX - imageWidth * 0.03f, imageY + imageHeight * 0.71f,
                    maxWidth * 0.88f, Math.max(1.0f, 1.2f * scale), applyAlpha(bright, 0.48f), soft);
        });
    }

    private void drawReisaShutdownBubble(UiTree.Scope scope, long elapsed, long exitElapsed, int width, float imageX,
                                         float imageY, float imageWidth, float imageHeight, float scale) {
        float bubbleIn = Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp(
                (elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS) / 320.0f, 0.0f, 1.0f));
        float bubbleOut = exitElapsed < 0L
                ? 1.0f
                : 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.clamp(exitElapsed / 300.0f, 0.0f, 1.0f));
        float visibility = Math.min(bubbleIn, bubbleOut);
        if (visibility <= 0.001f) return;

        String name = "UZAWA REISA";
        String farewell = EpsilonTranslations.Gui.MAINMENU_REISA_FAREWELL.getTranslatedName();
        float contentInset = 13.0f * scale;
        float maxBubbleWidth = Math.clamp(225.0f * scale, 104.0f * scale, width * 0.44f);
        float availableTextWidth = Math.max(1.0f, maxBubbleWidth - contentInset * 2.0f);
        float nameScale = 0.62f * scale;
        float messageScale = fitTextScale(farewell, 0.68f * scale, availableTextWidth);
        float bubbleWidth = Math.min(maxBubbleWidth, Math.max(
                scene.scheduler().textMetrics().getWidth(name, nameScale, StaticFontLoader.JURA_LIGHT),
                scene.scheduler().textMetrics().getWidth(farewell, messageScale)) + contentInset * 2.0f);

        float topPadding = 7.5f * scale;
        float rowGap = 5.0f * scale;
        float bottomPadding = 9.0f * scale;
        float nameHeight = scene.scheduler().textMetrics().getHeight(nameScale, StaticFontLoader.JURA_LIGHT);
        float messageHeight = scene.scheduler().textMetrics().getHeight(messageScale);
        float bubbleHeight = topPadding + nameHeight + rowGap + messageHeight + bottomPadding;
        float bubbleX = Mth.clamp(imageX - bubbleWidth * 0.72f,
                12.0f * scale, width - bubbleWidth - 12.0f * scale);
        float bubbleY = imageY + imageHeight * 0.22f + (1.0f - bubbleIn) * 12.0f * scale;
        float radius = 6.0f * scale;
        float speechProgress = Mth.clamp((elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS)
                / (float) (REISA_SHUTDOWN_SOUND_FALLBACK_DURATION_MS - REISA_SHUTDOWN_BUBBLE_DELAY_MS), 0.0f, 1.0f);

        Color surface = applyAlpha(new Color(29, 31, 42), visibility * 0.97f);
        Color outline = applyAlpha(new Color(229, 194, 255), visibility * 0.54f);
        Color nameColor = applyAlpha(new Color(252, 224, 255), visibility);
        Color accentColor = applyAlpha(new Color(222, 169, 255), visibility * 0.98f);
        Color textColor = applyAlpha(new Color(244, 241, 250), visibility);
        float textX = bubbleX + contentInset;
        float contentOffsetY = -2.0f * scale;
        float nameY = bubbleY + topPadding + contentOffsetY;
        float messageY = nameY + nameHeight + rowGap;
        float progressBarHeight = Math.max(3.0f, 3.0f * scale);
        float progressBarY = bubbleY + bubbleHeight - 8.0f * scale;

        scope.layer(20, layer -> {
            layer.shadow(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius,
                    REISA_BUBBLE_SHADOW_BLUR * scale,
                    applyAlpha(new Color(0, 0, 0), visibility * REISA_SHUTDOWN_BUBBLE_SHADOW_ALPHA));
            layer.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, surface);
            layer.outline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius,
                    Math.max(1.0f, scale), outline);
            layer.roundRect(bubbleX + 6.5f * scale, nameY, 2.0f * scale, nameHeight,
                    1.0f * scale, accentColor);
            layer.roundRect(bubbleX + contentInset, progressBarY,
                    (bubbleWidth - contentInset * 2.0f) * speechProgress, progressBarHeight,
                    progressBarHeight * 0.5f, applyAlpha(accentColor, 0.62f));
        });
        scope.layer(21, layer -> {
            layer.text(name, textX, nameY, nameScale, nameColor, StaticFontLoader.JURA_LIGHT);
            layer.text(farewell, textX, messageY, messageScale, textColor);
        });
    }

    private Identifier reisaShutdownTexture(long elapsed, boolean exiting) {
        if (exiting) return REISA_SHUTDOWN_FINAL_TEXTURE;
        if (elapsed < REISA_SHUTDOWN_BUBBLE_DELAY_MS) return REISA_SHUTDOWN_ENTRANCE_TEXTURE;

        long speechElapsed = elapsed - REISA_SHUTDOWN_BUBBLE_DELAY_MS;
        return (speechElapsed / 145L & 1L) == 0L ? REISA_WELCOME_TEXTURE : REISA_EXIT_TEXTURE;
    }

    private void prewarmReisaShutdownTextures(UiTree.Scope scope) {
        Color transparent = applyAlpha(Color.WHITE, 0.0f);
        scope.layer(-60, layer -> {
            layer.texture(REISA_SHUTDOWN_ENTRANCE_TEXTURE, -1.0f, -1.0f, 1.0f, 1.0f,
                    0.0f, 0.0f, 1.0f, 1.0f, transparent, true);
            layer.texture(REISA_SHUTDOWN_FINAL_TEXTURE, -1.0f, -1.0f, 1.0f, 1.0f,
                    0.0f, 0.0f, 1.0f, 1.0f, transparent, true);
        });
    }

    private void drawReisaFloatingBase(UiTree.Scope scope, int height, float imageX, float imageWidth,
                                       float alpha, float widthProgress, float scale) {
        if (alpha <= 0.001f || widthProgress <= 0.001f) return;

        float centerX = imageX + imageWidth * 0.55f;
        float lineWidth = imageWidth * widthProgress;
        float lineHeight = Math.max(1.0f, 1.15f * scale);
        float lineY = height - lineHeight;
        float glowHeight = 34.0f * scale;

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
        scope.layer(-22, layer -> {
            layer.texture(REISA_EXIT_TEXTURE, imageX - 16.0f * scale, imageY + 1.5f * scale,
                    imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, farTrail, true);
            layer.texture(REISA_EXIT_TEXTURE, imageX - 8.0f * scale, imageY + 0.75f * scale,
                    imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, nearTrail, true);
        });
    }

    private void drawReisaPoseOverlay(UiTree.Scope scope, float imageX, float imageY, float imageWidth, float imageHeight, float alpha) {
        scope.layer(-20, layer -> layer.texture(REISA_EXIT_TEXTURE, imageX, imageY,
                imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f,
                applyAlpha(Color.WHITE, alpha), true));
    }

    private void prewarmReisaExitTexture(UiTree.Scope scope) {
        scope.layer(-30, layer -> layer.texture(REISA_EXIT_TEXTURE,
                -1.0f, -1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                applyAlpha(Color.WHITE, 0.0f), true));
    }

    private void drawReisa(UiTree.Scope scope, Identifier texture, float imageX, float imageY, float imageWidth, float imageHeight, float alpha) {
        scope.layer(-21, layer -> layer.texture(texture, imageX, imageY, imageWidth, imageHeight, 0.0f, 0.0f, 1.0f, 1.0f, applyAlpha(Color.WHITE, alpha), true));
    }

    private void drawReisaFoldedPage(UiTree.Scope scope, float imageX, float imageY, float imageWidth, float imageHeight, float unfold, float alpha, float scale) {
        if (unfold >= 0.999f) {
            drawReisa(scope, MainMenuScreen.REISA_WELCOME_TEXTURE, imageX, imageY, imageWidth, imageHeight, alpha);
            return;
        }

        float hingeX = imageX + imageWidth;
        for (int index = 0; index < REISA_PAGE_SLICES; index++) {
            float u0 = index / (float) REISA_PAGE_SLICES;
            float u1 = (index + 1.0f) / REISA_PAGE_SLICES;
            float center = (u0 + u1) * 0.5f;
            float sliceX = hingeX - imageWidth * (1.0f - u0) * unfold;
            float sliceRight = hingeX - imageWidth * (1.0f - u1) * unfold;
            float curl = (float) Math.sin(center * Math.PI) * (1.0f - unfold) * 13.0f * scale;
            float shade = 1.0f - (1.0f - unfold) * (0.18f + 0.38f * (float) Math.sin(center * Math.PI));
            Color sliceColor = applyAlpha(Color.WHITE, alpha * shade);
            scope.layer(-20, layer -> layer.texture(MainMenuScreen.REISA_WELCOME_TEXTURE, sliceX, imageY + curl,
                    Math.max(0.5f, sliceRight - sliceX + 0.35f), imageHeight - curl * 0.25f,
                    u0, 0.0f, u1, 1.0f, sliceColor, true));
        }

        float foldEdgeX = hingeX - imageWidth * unfold;
        float edgeAlpha = alpha * (1.0f - unfold) * 0.72f;
        scope.layer(-19, layer -> layer.rect(foldEdgeX, imageY + imageHeight * 0.05f,
                Math.max(1.0f, 2.5f * scale), imageHeight * 0.90f,
                applyAlpha(new Color(245, 224, 255), edgeAlpha)));
    }

    private void drawReisaGreetingBubble(UiTree.Scope scope, long elapsed, long exitElapsed, int width, float imageX,
                                         float imageY, float imageWidth, float imageHeight, float scale) {
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
        float contentInset = 13.0f * scale;
        float maxBubbleWidth = Math.clamp(205.0f * scale, 96.0f * scale, width * 0.40f);
        float minBubbleWidth = Math.min(145.0f * scale, maxBubbleWidth);
        float availableTextWidth = Math.max(1.0f, maxBubbleWidth - contentInset * 2.0f);
        float nameScale = 0.62f * scale;
        float messageScale = fitTextScale(greeting, 0.68f * scale, availableTextWidth);
        float nameWidth = scene.scheduler().textMetrics().getWidth(name, nameScale, StaticFontLoader.JURA_LIGHT);
        float messageWidth = scene.scheduler().textMetrics().getWidth(greeting, messageScale);
        float bubbleWidth = Mth.clamp(Math.max(nameWidth, messageWidth) + contentInset * 2.0f,
                minBubbleWidth, maxBubbleWidth);

        float topPadding = 7.5f * scale;
        float rowGap = 5.0f * scale;
        float bottomPadding = 8.0f * scale;
        float nameHeight = scene.scheduler().textMetrics().getHeight(nameScale, StaticFontLoader.JURA_LIGHT);
        float messageHeight = scene.scheduler().textMetrics().getHeight(messageScale);
        float bubbleHeight = topPadding + nameHeight + rowGap + messageHeight + bottomPadding;
        float bubbleX = Mth.clamp(imageX - bubbleWidth * 0.72f, 12.0f * scale, width - bubbleWidth - 12.0f * scale);
        float bubbleY = imageY + imageHeight * 0.23f + (1.0f - bubbleIn) * 12.0f * scale
                - bubbleExit * 10.0f * scale;
        float radius = 6.0f * scale;
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
            layer.shadow(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius,
                    REISA_BUBBLE_SHADOW_BLUR * scale,
                    applyAlpha(new Color(0, 0, 0), visibility * REISA_GREETING_BUBBLE_SHADOW_ALPHA));
            layer.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, surface);
            layer.outline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, radius, Math.max(1.0f, scale), outline);
            layer.roundRect(bubbleX + 6.5f * scale, nameY, 2.0f * scale, nameHeight, 1.0f * scale, accentColor);
            layer.roundRect(bubbleX + bubbleWidth - 13.0f * scale, bubbleY + bubbleHeight - 2.0f * scale, 10.0f * scale, 9.0f * scale, 2.5f * scale, surface);
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

    private static String localizedTitle(String title) {
        return switch (title) {
            case "Singleplayer" -> EpsilonTranslations.Gui.MAINMENU_SINGLEPLAYER.getTranslatedName();
            case "Multiplayer" -> EpsilonTranslations.Gui.MAINMENU_MULTIPLAYER.getTranslatedName();
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
            for (MenuEntry entry : entries) {
                MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
                if (entry.isHovered(epsilonEvent.x(), epsilonEvent.y())) {
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

    public enum Background {
        SEA_LEVEL,
        CLOUDS,
        ALIEN_TERRAIN,
        INFERNO,
        PLANET,
        BLACK_HOLE,
        MINECRAFT
    }

}
