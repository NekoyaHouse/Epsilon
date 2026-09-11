package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.QQAvatarManager;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class VictoryBanner extends HudModule {

    public static final VictoryBanner INSTANCE = new VictoryBanner();

    private VictoryBanner() {
        super("Victory Banner", 0.0f, 72.0f, IDEAL_WIDTH, BASE_HEIGHT);
        setAnchorState(HorizontalAnchor.Center, VerticalAnchor.Top, 0.0f, 72.0f);
    }

    private enum TriggerMode {
        Off,
        Title,
        Kill,
        Chat
    }

    private static final long FRAME_IN_DURATION = 240L;
    private static final long CONTENT_DELAY = 110L;
    private static final long CONTENT_IN_DURATION = 180L;
    private static final long CONTENT_OUT_DURATION = 110L;
    private static final long FRAME_OUT_DURATION = 180L;
    private static final long KILL_CONFIRMATION_WINDOW_MS = 4_000L;

    private static final float MIN_WIDTH = 340.0f;
    private static final float IDEAL_WIDTH = 500.0f;
    private static final float MAX_WIDTH = 620.0f;
    private static final float BASE_HEIGHT = 66.0f;

    private static final Color TEXT = new Color(244, 245, 240, 255);
    private static final Color MUTED = new Color(128, 132, 135, 255);
    private static final Color HAIRLINE = new Color(224, 226, 220, 45);

    private final Map<Integer, TrackedTarget> recentTargets = new HashMap<>();

    private final SettingGroup generalGroup = settingGroup("General");
    private final SettingGroup triggerGroup = settingGroup("Trigger");
    private final SettingGroup appearanceGroup = settingGroup("Appearance");
    private final EnumSetting<TriggerMode> triggerMode = enumSetting("Trigger Mode", TriggerMode.Kill, ignored -> recentTargets.clear()).group(triggerGroup);
    private final StringListSetting titleTriggers = stringListSetting("Title Triggers", List.of("胜利", "挑战成功", "Victory"), () -> triggerMode.is(TriggerMode.Title));
    private final StringListSetting chatTriggers = stringListSetting("Chat Triggers", List.of("获胜", "胜利", "挑战成功", "Victory"), () -> triggerMode.is(TriggerMode.Chat));
    private final StringSetting title = stringSetting("Title", "TARGET ELIMINATED").group(generalGroup);
    private final StringSetting subtitle = stringSetting("Subtitle", "ELIMINATION CONFIRMED").group(generalGroup);
    private final DoubleSetting holdTime = doubleSetting("Hold Time", 1.9, 0.2, 10.0, 0.1).group(generalGroup);
    private final DoubleSetting scale = doubleSetting("Scale", 0.75, 0.5, 2.0, 0.05).group(appearanceGroup);
    private final DoubleSetting titleScale = doubleSetting("Title Scale", 1.5, 0.8, 1.8, 0.05).group(appearanceGroup);
    private final DoubleSetting subtitleScale = doubleSetting("Subtitle Scale", 0.7, 0.6, 1.2, 0.05).group(appearanceGroup);
    private final ColorSetting accentColor = colorSetting("Accent Color", new Color(244, 244, 20, 255)).group(appearanceGroup);
    private final ColorSetting subtitleColor = colorSetting("Subtitle Color", new Color(141, 145, 148, 255)).group(appearanceGroup);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(14, 15, 17, 158)).group(appearanceGroup);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true).group(appearanceGroup);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 8.0, 1.0, 20.0, 1.0, backgroundBlur::getValue).group(appearanceGroup);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private volatile long animationStartMs;
    private volatile boolean active;
    private volatile String currentTitle;

    public static boolean isPlaying() {
        return INSTANCE.active;
    }

    public static void show(String title) {
        INSTANCE.play(title);
    }

    @Override
    public void reset() {
        super.reset();
        setAnchorState(HorizontalAnchor.Center, VerticalAnchor.Top, 0.0f, 72.0f);
    }

    @Override
    protected void onEnable() {
        clearRuntimeState();
        QQAvatarManager.INSTANCE.requestLoad();
    }

    @Override
    protected void onDisable() {
        clearRuntimeState();
        QQAvatarManager.INSTANCE.release();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        boolean preview = mc.gui.screen() instanceof HudEditorScreen;
        if (preview) {
            renderBanner(true, 0L);
        } else {
            updateKillTrigger();
            if (!active) return;

            long elapsed = Math.max(0L, System.currentTimeMillis() - animationStartMs);
            if (elapsed >= totalDuration()) {
                active = false;
                currentTitle = null;
                return;
            }

            renderBanner(false, elapsed);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        switch (triggerMode.getValue()) {
            case Title -> {
                Component text = switch (packet) {
                    case ClientboundSetTitleTextPacket titlePacket -> titlePacket.text();
                    case ClientboundSetSubtitleTextPacket subtitlePacket -> subtitlePacket.text();
                    default -> null;
                };
                if (text != null && matches(text.getString(), titleTriggers.getValue())) {
                    play(null);
                }
            }
            case Chat -> {
                String message = switch (packet) {
                    case ClientboundSystemChatPacket systemPacket when !systemPacket.overlay() ->
                            systemPacket.content().getString();
                    case ClientboundDisguisedChatPacket disguisedPacket -> disguisedPacket.message().getString();
                    case ClientboundPlayerChatPacket playerPacket -> visiblePlayerChat(playerPacket);
                    default -> null;
                };
                if (matches(message, chatTriggers.getValue())) {
                    play(null);
                }
            }
            case Kill -> {
                if (packet instanceof ClientboundEntityEventPacket entityPacket && entityPacket.getEventId() == EntityEvent.DEATH) {
                    mc.execute(() -> confirmDeathEvent(entityPacket));
                }
            }
        }
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (triggerMode.is(TriggerMode.Kill) && event.getPlayer() == mc.player && event.getEntity() instanceof Player player && player != mc.player && player.isAlive()) {
            recentTargets.put(player.getId(), new TrackedTarget(player, System.currentTimeMillis()));
        }
    }

    private void updateKillTrigger() {
        if (triggerMode.is(TriggerMode.Kill) && !nullCheck()) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, TrackedTarget>> iterator = recentTargets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, TrackedTarget> entry = iterator.next();
                TrackedTarget target = entry.getValue();
                Player player = target.player();
                if (!target.isRecent(now)) {
                    iterator.remove();
                    continue;
                }

                if (player.level() != mc.level) {
                    iterator.remove();
                    continue;
                }

                boolean dead = player.isDeadOrDying() || player.deathTime > 0 || player.getHealth() <= 0.0f;
                boolean removed = player.isRemoved() || mc.level.getEntity(entry.getKey()) != player;
                if (dead || removed) {
                    iterator.remove();
                    play(null);
                }
            }
        } else {
            recentTargets.clear();
        }
    }

    private void confirmTrackedDeath(int entityId) {
        if (!isEnabled() || !triggerMode.is(TriggerMode.Kill)) return;
        TrackedTarget target = recentTargets.get(entityId);
        if (target == null || target.player().level() != mc.level || !target.isRecent(System.currentTimeMillis()))
            return;
        recentTargets.remove(entityId);
        play(null);
    }

    private void confirmDeathEvent(ClientboundEntityEventPacket packet) {
        if (!isEnabled() || !triggerMode.is(TriggerMode.Kill) || mc.level == null) return;
        Entity entity = packet.getEntity(mc.level);
        if (entity != null) {
            confirmTrackedDeath(entity.getId());
        }
    }

    private void play(String overrideTitle) {
        if (!isEnabled() || active) return;
        String requested = overrideTitle == null ? title.getValue() : overrideTitle;
        currentTitle = requested == null || requested.isBlank() ? "TARGET ELIMINATED" : requested;
        animationStartMs = System.currentTimeMillis();
        active = true;
    }

    private void renderBanner(boolean preview, long elapsed) {
        QQAvatarManager.INSTANCE.requestLoad();
        TextRenderer textRenderer = textRendererSupplier.get();
        float uiScale = scale.getValue().floatValue();
        String bannerTitle = preview ? displayTitle(title.getValue()) : displayTitle(currentTitle);
        String bannerSubtitle = subtitle.getValue() == null ? "" : subtitle.getValue();
        Layout layout = createLayout(textRenderer, bannerTitle, bannerSubtitle, uiScale);
        setBounds(layout.width(), layout.height());

        float frameProgress = preview ? 1.0f : frameProgress(elapsed);
        float contentAlpha = preview ? 1.0f : contentAlpha(elapsed);
        float visibleWidth = Math.max(1.0f, layout.width() * frameProgress);
        float visibleX = x + (layout.width() - visibleWidth) * 0.5f;
        float frameAlpha = Math.min(1.0f, frameProgress * 1.8f);
        float offset = (1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(contentAlpha)) * 5.0f * uiScale;

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(visibleX, y, visibleWidth, layout.height(), 0.0f, blurStrength.getValue().floatValue());
        }

        UiTree.Scope scope = renderScope();
        scope.rect(visibleX, y, visibleWidth, layout.height(), withAlpha(backgroundColor.getValue(), frameAlpha));
        scope.rect(visibleX, y, visibleWidth, 2.0f * uiScale, withAlpha(accentColor.getValue(), frameAlpha));

        if (contentAlpha <= 0.0f || frameProgress < 0.72f) return;

        scope.scissor(new UiRect(visibleX, y, visibleWidth, layout.height()), clipped -> {
            drawIdentityModule(clipped, layout, contentAlpha, offset, uiScale);
            drawMessage(clipped, textRenderer, layout, bannerTitle, bannerSubtitle, contentAlpha, offset, uiScale, preview);
            drawStatusModule(clipped, textRenderer, layout, contentAlpha, offset, uiScale, preview);
        });
    }

    private Layout createLayout(TextRenderer renderer, String bannerTitle, String bannerSubtitle, float uiScale) {
        float requestedTitleScale = titleScale.getValue().floatValue() * uiScale;
        float requestedSubtitleScale = subtitleScale.getValue().floatValue() * uiScale;
        float titleWidth = renderer.getWidth(bannerTitle, requestedTitleScale);
        float subtitleWidth = renderer.getWidth(bannerSubtitle, requestedSubtitleScale);
        float desired = Math.max(IDEAL_WIDTH * uiScale, Math.max(titleWidth, subtitleWidth) + 230.0f * uiScale);
        float available = Math.max(1.0f, LuminRenderSystem.getScaledWidth() - 24.0f);
        float width = Math.min(available, Mth.clamp(desired, MIN_WIDTH * uiScale, MAX_WIDTH * uiScale));
        return new Layout(width, BASE_HEIGHT * uiScale);
    }

    private void drawIdentityModule(UiTree.Scope scope, Layout layout, float alpha, float offset, float uiScale) {
        float dividerX = x + 70.0f * uiScale;
        scope.rect(dividerX, y + 10.0f * uiScale, uiScale, layout.height() - 20.0f * uiScale, withAlpha(HAIRLINE, alpha));
        float avatarX = x + 18.0f * uiScale;
        float avatarY = y + 13.0f * uiScale + offset;
        float avatarSize = 40.0f * uiScale;
        float avatarRadius = 2.0f * uiScale;
        scope.texture(QQAvatarManager.INSTANCE.texture(), avatarX, avatarY, avatarSize, avatarSize, 0.0f, 0.0f, 1.0f, 1.0f, withAlpha(Color.WHITE, alpha), QQAvatarManager.INSTANCE.linearFilter());
        scope.outline(avatarX, avatarY, avatarSize, avatarSize, avatarRadius, uiScale, withAlpha(HAIRLINE, alpha));
    }

    private void drawMessage(UiTree.Scope scope, TextRenderer renderer, Layout layout, String bannerTitle, String bannerSubtitle, float alpha, float offset, float uiScale, boolean preview) {
        float left = x + 84.0f * uiScale;
        boolean showStatus = layout.width() >= 360.0f * uiScale;
        float right = x + layout.width() - (showStatus ? 88.0f : 18.0f) * uiScale;
        float available = Math.max(80.0f * uiScale, right - left);

        String eyebrow = preview ? "SYSTEM // VISUAL PREVIEW" : "COMBAT // ELIMINATION CONFIRMED";
        float eyebrowScale = 0.55f * uiScale;
        float fittedTitleScale = fitScale(renderer, bannerTitle, titleScale.getValue().floatValue() * uiScale, available, null);
        boolean hasSubtitle = !bannerSubtitle.isBlank();
        float fittedSubtitleScale = fitScale(renderer, bannerSubtitle.toUpperCase(Locale.ROOT), subtitleScale.getValue().floatValue() * uiScale, available, null);

        float eyebrowHeight = renderer.getHeight(eyebrowScale);
        float titleHeight = renderer.getHeight(fittedTitleScale);
        float subtitleHeight = hasSubtitle ? renderer.getHeight(fittedSubtitleScale) : 0.0f;
        float blockHeight = eyebrowHeight + 3.0f * uiScale + titleHeight + (hasSubtitle ? 2.0f * uiScale + subtitleHeight : 0.0f);
        float lineY = y + (layout.height() - blockHeight) * 0.5f + offset;

        scope.text(eyebrow, left, lineY, eyebrowScale, withAlpha(MUTED, alpha));
        lineY += eyebrowHeight + 2.0f * uiScale;
        scope.text(bannerTitle, left, lineY, fittedTitleScale, withAlpha(TEXT, alpha));

        if (hasSubtitle) {
            lineY += titleHeight + 1.0f * uiScale;
            scope.text(bannerSubtitle.toUpperCase(Locale.ROOT), left, lineY, fittedSubtitleScale, withAlpha(subtitleColor.getValue(), alpha));
        }
    }

    private void drawStatusModule(UiTree.Scope scope, TextRenderer renderer, Layout layout, float alpha, float offset, float uiScale, boolean preview) {
        if (layout.width() < 360.0f * uiScale) return;

        float moduleX = x + layout.width() - 76.0f * uiScale;
        scope.rect(moduleX, y + 10.0f * uiScale, uiScale, layout.height() - 20.0f * uiScale, withAlpha(HAIRLINE, alpha));

        float numberScale = 1.0f * uiScale;
        float labelScale = 0.8f * uiScale;
        float statusScale = 0.6f * uiScale;
        float numberHeight = renderer.getHeight(numberScale);
        float labelHeight = renderer.getHeight(labelScale);
        float statusHeight = renderer.getHeight(statusScale);
        float primaryHeight = Math.max(numberHeight, labelHeight);
        float groupHeight = primaryHeight + 3.0f * uiScale + statusHeight;
        float groupY = y + (layout.height() - groupHeight) * 0.5f + offset;

        scope.text("01", moduleX + 9.0f * uiScale, groupY, numberScale, withAlpha(TEXT, alpha));
        scope.text(preview ? "TEST" : "KILL", moduleX + 30.0f * uiScale, groupY + (primaryHeight - labelHeight) * 0.5f, labelScale, withAlpha(accentColor.getValue(), alpha));
        scope.text("STATUS / OK", moduleX + 9.0f * uiScale, groupY + primaryHeight + 3.0f * uiScale, statusScale, withAlpha(MUTED, alpha));
    }

    private float frameProgress(long elapsed) {
        if (elapsed < FRAME_IN_DURATION) {
            return Easing.EASE_OUT_QUINT.getFunction().apply(elapsed / (float) FRAME_IN_DURATION);
        } else {
            long frameOutStart = FRAME_IN_DURATION + holdDurationMs() + CONTENT_OUT_DURATION;
            if (elapsed < frameOutStart) return 1.0f;
            return 1.0f - Easing.EASE_IN_CUBIC.getFunction().apply((elapsed - frameOutStart) / (float) FRAME_OUT_DURATION);
        }
    }

    private float contentAlpha(long elapsed) {
        if (elapsed < CONTENT_DELAY) return 0.0f;
        if (elapsed < CONTENT_DELAY + CONTENT_IN_DURATION) {
            return Easing.EASE_OUT_CUBIC.getFunction().apply((elapsed - CONTENT_DELAY) / (float) CONTENT_IN_DURATION);
        } else {
            long contentOutStart = FRAME_IN_DURATION + holdDurationMs();
            if (elapsed < contentOutStart) return 1.0f;
            return 1.0f - Easing.EASE_IN_CUBIC.getFunction().apply((elapsed - contentOutStart) / (float) CONTENT_OUT_DURATION);
        }
    }

    private long totalDuration() {
        return FRAME_IN_DURATION + holdDurationMs() + CONTENT_OUT_DURATION + FRAME_OUT_DURATION;
    }

    private long holdDurationMs() {
        return Math.round(holdTime.getValue() * 1_000.0);
    }

    private void clearRuntimeState() {
        active = false;
        animationStartMs = 0L;
        currentTitle = null;
        recentTargets.clear();
    }

    private static boolean matches(String text, List<String> triggers) {
        if (text == null || text.isEmpty() || triggers == null) return false;
        for (String candidate : triggers) {
            if (candidate != null && !candidate.isEmpty() && text.contains(candidate)) return true;
        }
        return false;
    }

    private static String visiblePlayerChat(ClientboundPlayerChatPacket packet) {
        if (packet.unsignedContent() != null) return packet.unsignedContent().getString();
        return packet.filterMask().apply(packet.body().content());
    }

    private static String displayTitle(String value) {
        return value == null || value.isBlank() ? "TARGET ELIMINATED" : value;
    }

    private static float fitScale(TextRenderer renderer, String text, float requested, float maxWidth, TtfFontLoader font) {
        if (text == null || text.isEmpty()) return requested;
        float natural = font == null ? renderer.getWidth(text, 1.0f) : renderer.getWidth(text, 1.0f, font);
        if (natural <= 0.0f) return requested;
        return Mth.clamp(maxWidth / natural, 0.1f, requested);
    }

    private static Color withAlpha(Color color, float alphaMultiplier) {
        Color source = color == null ? Color.WHITE : color;
        int alpha = Mth.clamp(Math.round(source.getAlpha() * Mth.clamp(alphaMultiplier, 0.0f, 1.0f)), 0, 255);
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
    }

    private record Layout(float width, float height) {
    }

    private record TrackedTarget(Player player, long attackedAtMs) {
        private boolean isRecent(long now) {
            return now - attackedAtMs <= KILL_CONFIRMATION_WINDOW_MS;
        }
    }

}
