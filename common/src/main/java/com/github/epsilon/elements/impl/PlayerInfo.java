package com.github.epsilon.elements.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.HealthManager;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PlayerInfo extends HudModule {

    public static final PlayerInfo INSTANCE = new PlayerInfo();

    private PlayerInfo() {
        super("Player Info", 4.0f, 48.0f, 250.0f, 70.0f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.05);
    private final DoubleSetting width = doubleSetting("Width", 250.0, 200.0, 300.0, 1.0);
    private final BoolSetting textGlow = boolSetting("Text Glow", true);
    private final DoubleSetting glowRadius = doubleSetting("Glow Radius", 3.0, 0.1, 6.0, 0.1, textGlow::getValue);
    private final IntSetting glowIntensity = intSetting("Glow Intensity", 2, 1, 5, 1, textGlow::getValue);
    private final DoubleSetting playerRange = doubleSetting("Range", 128.0, 8.0, 256.0, 1.0);
    private final IntSetting maxPlayers = intSetting("Max Players", 8, 1, 20, 1);
    private final BoolSetting showSelf = boolSetting("Show Self", false);
    private final BoolSetting showHeads = boolSetting("Show Heads", true);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 6.0, 0.0, 20.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(18, 24, 21, 150));
    private final ColorSetting headerColor = colorSetting("Header Color", new Color(255, 255, 255, 240));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(240, 243, 241, 230));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 9.0, 2.0, 32.0, 1.0, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(255, 255, 255, 90), drawShadow::getValue);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1, backgroundBlur::getValue);

    private static final float PANEL_PADDING_X = 8.0f;
    private static final float PANEL_PADDING_Y = 7.0f;
    private static final float HEADER_SCALE = 0.68f;
    private static final float TEXT_SCALE = 0.66f;
    private static final float ROW_HEIGHT = 19.0f;
    private static final float HEAD_SIZE = 14.0f;
    private static final float HEAD_TEXT_GAP = 5.0f;
    private static final float COLUMN_GAP = 12.0f;
    private static final float HEADER_BODY_GAP = 6.0f;
    private static final float NAME_SCROLL_SPEED = 18.0f;
    private static final long NAME_SCROLL_HOLD_MS = 800L;

    private static final Color FRIEND_COLOR = new Color(112, 225, 168, 235);
    private static final Color HEALTH_HIGH_COLOR = new Color(116, 228, 135, 235);
    private static final Color HEALTH_MEDIUM_COLOR = new Color(238, 201, 47, 235);
    private static final Color HEALTH_LOW_COLOR = new Color(244, 93, 94, 235);

    private final Map<UUID, Integer> popCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nameScrollStartTimes = new HashMap<>();
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private List<PlayerRow> cachedRows = List.of();
    private boolean rowsInitialized;

    @Override
    protected void onEnable() {
        popCounts.clear();
        nameScrollStartTimes.clear();
        cachedRows = List.of();
        rowsInitialized = false;
    }

    @Override
    protected void onDisable() {
        popCounts.clear();
        nameScrollStartTimes.clear();
        cachedRows = List.of();
        rowsInitialized = false;
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        if (!rowsInitialized) {
            refreshRows();
        }
        List<PlayerRow> rows = cachedRows;
        Layout layout = createLayout(textRenderer, rows);
        setBounds(layout.panelWidth(), layout.panelHeight());

        float panelX = this.x;
        float panelY = this.y;
        float panelRadius = cornerRadius.getValue().floatValue() * layout.scale();
        UiTree.Scope scope = renderScope();

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(panelX, panelY, layout.panelWidth(), layout.panelHeight(), panelRadius, blurStrength.getValue());
        }
        if (drawShadow.getValue()) {
            scope.shadow(panelX, panelY, layout.panelWidth(), layout.panelHeight(), panelRadius, shadowBlur.getValue().floatValue() * layout.scale(), shadowColor.getValue());
        }
        scope.roundRect(panelX, panelY, layout.panelWidth(), layout.panelHeight(), panelRadius, backgroundColor.getValue());

        drawHeaders(scope, textRenderer, layout, panelX, panelY);

        float dividerY = panelY + layout.paddingY() + layout.headerHeight() + layout.headerBodyGap() * 0.5f;
        scope.rect(panelX + layout.paddingX(), dividerY, layout.tableWidth(), Math.max(0.5f, layout.scale() * 0.75f), withAlpha(headerColor.getValue(), 0.14f));

        float rowY = panelY + layout.paddingY() + layout.headerHeight() + layout.headerBodyGap();
        if (rows.isEmpty()) {
            String emptyText = EpsilonTranslations.PlayerInfo.EMPTY.getTranslatedName();
            float emptyX = panelX + (layout.panelWidth() - textRenderer.getWidth(emptyText, layout.textScale())) * 0.5f;
            float emptyY = rowY + (layout.rowHeight() - textRenderer.getHeight(layout.textScale())) * 0.5f;
            text(scope, emptyText, emptyX, emptyY, layout.textScale(), withAlpha(textColor.getValue(), 0.72f));
            return;
        }

        for (PlayerRow row : rows) {
            drawRow(scope, textRenderer, layout, row, panelX, rowY);
            rowY += layout.rowHeight();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || !(event.getPacket() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) {
            return;
        }

        Entity entity = packet.getEntity(mc.level);
        if (entity instanceof AbstractClientPlayer player) {
            popCounts.merge(player.getUUID(), 1, Integer::sum);
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Post event) {
        refreshRows();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        popCounts.clear();
        nameScrollStartTimes.clear();
        cachedRows = List.of();
        rowsInitialized = false;
    }

    private void refreshRows() {
        cachedRows = collectRows();
        rowsInitialized = true;
        pruneNameScrollState(cachedRows);
    }

    private List<PlayerRow> collectRows() {
        double maxDistanceSquared = Mth.square(playerRange.getValue());
        List<AbstractClientPlayer> players = new ArrayList<>();

        for (AbstractClientPlayer player : mc.level.players()) {
            if ((!showSelf.getValue() && player == mc.player) || player.isRemoved()) continue;
            if (mc.player.distanceToSqr(player) > maxDistanceSquared) continue;
            players.add(player);
        }

        players.sort(Comparator.comparingDouble(mc.player::distanceToSqr));

        int count = Math.min(maxPlayers.getValue(), players.size());
        List<PlayerRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AbstractClientPlayer player = players.get(i);
            float health = HealthManager.INSTANCE != null
                    ? HealthManager.INSTANCE.getHealth(player)
                    : player.getHealth() + player.getAbsorptionAmount();
            float maxHealth = Math.max(1.0f, player.getMaxHealth() + Math.max(0.0f, player.getAbsorptionAmount()));
            int distance = Math.round(mc.player.distanceTo(player));
            int direction = Math.round(Mth.wrapDegrees(player.getYRot()));

            rows.add(new PlayerRow(
                    player.getUUID(),
                    player.getSkin().body().texturePath(),
                    player.getGameProfile().name(),
                    distance + "m",
                    String.format(Locale.ROOT, "%.1f", health),
                    Integer.toString(popCounts.getOrDefault(player.getUUID(), 0)),
                    Integer.toString(direction),
                    resolveNameColor(player),
                    resolveHealthColor(health / maxHealth)
            ));
        }
        return rows;
    }

    private void pruneNameScrollState(List<PlayerRow> rows) {
        if (nameScrollStartTimes.isEmpty()) return;

        Set<UUID> visiblePlayers = new HashSet<>(rows.size());
        for (PlayerRow row : rows) {
            visiblePlayers.add(row.playerId());
        }
        nameScrollStartTimes.keySet().retainAll(visiblePlayers);
    }

    private Layout createLayout(TextRenderer textRenderer, List<PlayerRow> rows) {
        float scale = this.scale.getValue().floatValue();
        float headerScale = HEADER_SCALE * scale;
        float textScale = TEXT_SCALE * scale;
        float paddingX = PANEL_PADDING_X * scale;
        float paddingY = PANEL_PADDING_Y * scale;
        float rowHeight = ROW_HEIGHT * scale;
        float headSize = showHeads.getValue() ? HEAD_SIZE * scale : 0.0f;
        float headTextGap = showHeads.getValue() ? HEAD_TEXT_GAP * scale : 0.0f;
        float columnGap = COLUMN_GAP * scale;
        float headerBodyGap = HEADER_BODY_GAP * scale;

        float distanceWidth = textRenderer.getWidth(EpsilonTranslations.PlayerInfo.DISTANCE.getTranslatedName(), headerScale);
        float healthWidth = textRenderer.getWidth(EpsilonTranslations.PlayerInfo.HEALTH.getTranslatedName(), headerScale);
        float popsWidth = textRenderer.getWidth(EpsilonTranslations.PlayerInfo.POPS.getTranslatedName(), headerScale);
        float directionWidth = textRenderer.getWidth(EpsilonTranslations.PlayerInfo.DIRECTION.getTranslatedName(), headerScale);

        for (PlayerRow row : rows) {
            distanceWidth = Math.max(distanceWidth, textRenderer.getWidth(row.distance(), textScale));
            healthWidth = Math.max(healthWidth, textRenderer.getWidth(row.health(), textScale));
            popsWidth = Math.max(popsWidth, textRenderer.getWidth(row.pops(), textScale));
            directionWidth = Math.max(directionWidth, textRenderer.getWidth(row.direction(), textScale));
        }

        float panelWidth = width.getValue().floatValue() * scale;
        float tableWidth = panelWidth - paddingX * 2.0f;
        float nameWidth = Math.max(0.0f, tableWidth - distanceWidth - healthWidth - popsWidth - directionWidth - columnGap * 4.0f);
        float headerHeight = textRenderer.getHeight(headerScale);
        float bodyHeight = Math.max(1, rows.size()) * rowHeight;
        float panelHeight = paddingY * 2.0f + headerHeight + headerBodyGap + bodyHeight;

        return new Layout(
                scale, headerScale, textScale, paddingX, paddingY, rowHeight, headSize, headTextGap,
                columnGap, headerBodyGap, headerHeight, nameWidth, distanceWidth, healthWidth,
                popsWidth, directionWidth, tableWidth, panelWidth, panelHeight
        );
    }

    private void drawHeaders(UiTree.Scope scope, TextRenderer textRenderer, Layout layout, float panelX, float panelY) {
        float x = panelX + layout.paddingX();
        float y = panelY + layout.paddingY();
        Color color = headerColor.getValue();

        String playersText = EpsilonTranslations.PlayerInfo.PLAYERS.getTranslatedName();
        text(scope, playersText, x, y, layout.headerScale(), color);
        x += layout.nameWidth() + layout.columnGap();
        text(scope, EpsilonTranslations.PlayerInfo.DISTANCE.getTranslatedName(), x, y, layout.headerScale(), color);
        x += layout.distanceWidth() + layout.columnGap();
        text(scope, EpsilonTranslations.PlayerInfo.HEALTH.getTranslatedName(), x, y, layout.headerScale(), color);
        x += layout.healthWidth() + layout.columnGap();
        text(scope, EpsilonTranslations.PlayerInfo.POPS.getTranslatedName(), x, y, layout.headerScale(), color);
        x += layout.popsWidth() + layout.columnGap();
        text(scope, EpsilonTranslations.PlayerInfo.DIRECTION.getTranslatedName(), x, y, layout.headerScale(), color);
    }

    private void drawRow(UiTree.Scope scope, TextRenderer textRenderer, Layout layout, PlayerRow row, float panelX, float rowY) {
        float x = panelX + layout.paddingX();
        float textY = rowY + (layout.rowHeight() - textRenderer.getHeight(layout.textScale())) * 0.5f;

        if (showHeads.getValue()) {
            float headY = rowY + (layout.rowHeight() - layout.headSize()) * 0.5f;
            scope.playerHead(row.skinTexture(), x, headY, layout.headSize(), layout.headSize() * 0.12f, Color.WHITE);
            x += layout.headSize() + layout.headTextGap();
        }

        float nameTextWidth = Math.max(0.0f, layout.nameWidth() - layout.headSize() - layout.headTextGap());
        float fullNameWidth = textRenderer.getWidth(row.name(), layout.textScale());
        UUID playerId = row.playerId();
        if (nameTextWidth > 0.0f && fullNameWidth > nameTextWidth) {
            float overflow = fullNameWidth - nameTextWidth;
            float scrollOffset = getNameScrollOffset(playerId, overflow, layout.scale());
            float nameX = x;
            scope.scissor(nameX, rowY, nameTextWidth, layout.rowHeight(), clipped ->
                    text(clipped, row.name(), nameX - scrollOffset, textY, layout.textScale(), row.nameColor())
            );
        } else {
            nameScrollStartTimes.remove(playerId);
            if (nameTextWidth > 0.0f) {
                text(scope, row.name(), x, textY, layout.textScale(), row.nameColor());
            }
        }
        x = panelX + layout.paddingX() + layout.nameWidth() + layout.columnGap();
        text(scope, row.distance(), x, textY, layout.textScale(), textColor.getValue());
        x += layout.distanceWidth() + layout.columnGap();
        text(scope, row.health(), x, textY, layout.textScale(), row.healthColor());
        x += layout.healthWidth() + layout.columnGap();
        text(scope, row.pops(), x, textY, layout.textScale(), textColor.getValue());
        x += layout.popsWidth() + layout.columnGap();
        text(scope, row.direction(), x, textY, layout.textScale(), textColor.getValue());
    }

    private Color resolveNameColor(AbstractClientPlayer player) {
        return FriendManager.INSTANCE != null && FriendManager.INSTANCE.isFriend(player) ? FRIEND_COLOR : textColor.getValue();
    }

    private Color resolveHealthColor(float ratio) {
        if (ratio > 0.75f) return HEALTH_HIGH_COLOR;
        if (ratio > 0.35f) return HEALTH_MEDIUM_COLOR;
        return HEALTH_LOW_COLOR;
    }

    private Color withAlpha(Color color, float multiplier) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * multiplier), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private float getNameScrollOffset(UUID playerId, float overflow, float scale) {
        long now = Util.getMillis();
        long startedAt = nameScrollStartTimes.computeIfAbsent(playerId, ignored -> now);
        double travelDurationMs = overflow / (NAME_SCROLL_SPEED * scale) * 1000.0;
        double cycleDurationMs = NAME_SCROLL_HOLD_MS * 2.0 + travelDurationMs * 2.0;
        double phase = (now - startedAt) % cycleDurationMs;

        if (phase < NAME_SCROLL_HOLD_MS) return 0.0f;
        phase -= NAME_SCROLL_HOLD_MS;
        if (phase < travelDurationMs) return (float) (overflow * phase / travelDurationMs);
        phase -= travelDurationMs;
        if (phase < NAME_SCROLL_HOLD_MS) return overflow;
        phase -= NAME_SCROLL_HOLD_MS;
        return overflow - (float) (overflow * phase / travelDurationMs);
    }

    private void text(UiTree.Scope scope, String text, float x, float y, float scale, Color color) {
        if (textGlow.getValue()) {
            scope.blurredText(text, x, y, scale, glowRadius.getValue().floatValue() * scale, glowIntensity.getValue(), color);
        }
        scope.text(text, x, y, scale, color);
    }

    private record PlayerRow(
            UUID playerId,
            Identifier skinTexture,
            String name,
            String distance,
            String health,
            String pops,
            String direction,
            Color nameColor,
            Color healthColor
    ) {
    }

    private record Layout(
            float scale,
            float headerScale,
            float textScale,
            float paddingX,
            float paddingY,
            float rowHeight,
            float headSize,
            float headTextGap,
            float columnGap,
            float headerBodyGap,
            float headerHeight,
            float nameWidth,
            float distanceWidth,
            float healthWidth,
            float popsWidth,
            float directionWidth,
            float tableWidth,
            float panelWidth,
            float panelHeight
    ) {
    }

}
