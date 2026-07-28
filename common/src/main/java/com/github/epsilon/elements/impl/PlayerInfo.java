package com.github.epsilon.elements.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
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
    private final DoubleSetting playerRange = doubleSetting("Range", 128.0, 8.0, 256.0, 1.0);
    private final IntSetting maxPlayers = intSetting("Max Players", 8, 1, 20, 1);
    private final BoolSetting showSelf = boolSetting("Show Self", false);
    private final BoolSetting showHeads = boolSetting("Show Heads", true);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 6.0, 0.0, 20.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(18, 24, 21, 170));
    private final ColorSetting headerColor = colorSetting("Header Color", new Color(255, 255, 255, 240));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(240, 243, 241, 230));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 10.0, 2.0, 32.0, 1.0, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 110), drawShadow::getValue);
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

    private static final Color FRIEND_COLOR = new Color(112, 225, 168, 235);
    private static final Color HEALTH_HIGH_COLOR = new Color(116, 228, 135, 235);
    private static final Color HEALTH_MEDIUM_COLOR = new Color(238, 201, 47, 235);
    private static final Color HEALTH_LOW_COLOR = new Color(244, 93, 94, 235);

    private final Map<UUID, Integer> popCounts = new ConcurrentHashMap<>();
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    protected void onEnable() {
        popCounts.clear();
    }

    @Override
    protected void onDisable() {
        popCounts.clear();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        List<PlayerRow> rows = collectRows();
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
            String emptyText = trimToWidth(EpsilonTranslations.PlayerInfo.EMPTY.getTranslatedName(), layout.textScale(), layout.tableWidth(), textRenderer);
            float emptyX = panelX + (layout.panelWidth() - textRenderer.getWidth(emptyText, layout.textScale())) * 0.5f;
            float emptyY = rowY + (layout.rowHeight() - textRenderer.getHeight(layout.textScale())) * 0.5f;
            scope.text(emptyText, emptyX, emptyY, layout.textScale(), withAlpha(textColor.getValue(), 0.72f));
            return;
        }

        for (PlayerRow row : rows) {
            drawRow(scope, textRenderer, layout, row, panelX, rowY);
            rowY += layout.rowHeight();
        }
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
            float health = Managers.HEALTH != null
                    ? Managers.HEALTH.getHealth(player)
                    : player.getHealth() + player.getAbsorptionAmount();
            float maxHealth = Math.max(1.0f, player.getMaxHealth() + Math.max(0.0f, player.getAbsorptionAmount()));
            int distance = Math.round(mc.player.distanceTo(player));
            int direction = Math.round(Mth.wrapDegrees(player.getYRot()));

            rows.add(new PlayerRow(
                    player,
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

        String playersText = trimToWidth(EpsilonTranslations.PlayerInfo.PLAYERS.getTranslatedName(), layout.headerScale(), layout.nameWidth(), textRenderer);
        scope.text(playersText, x, y, layout.headerScale(), color);
        x += layout.nameWidth() + layout.columnGap();
        scope.text(EpsilonTranslations.PlayerInfo.DISTANCE.getTranslatedName(), x, y, layout.headerScale(), color);
        x += layout.distanceWidth() + layout.columnGap();
        scope.text(EpsilonTranslations.PlayerInfo.HEALTH.getTranslatedName(), x, y, layout.headerScale(), color);
        x += layout.healthWidth() + layout.columnGap();
        scope.text(EpsilonTranslations.PlayerInfo.POPS.getTranslatedName(), x, y, layout.headerScale(), color);
        x += layout.popsWidth() + layout.columnGap();
        scope.text(EpsilonTranslations.PlayerInfo.DIRECTION.getTranslatedName(), x, y, layout.headerScale(), color);
    }

    private void drawRow(UiTree.Scope scope, TextRenderer textRenderer, Layout layout, PlayerRow row, float panelX, float rowY) {
        float x = panelX + layout.paddingX();
        float textY = rowY + (layout.rowHeight() - textRenderer.getHeight(layout.textScale())) * 0.5f;

        if (showHeads.getValue()) {
            float headY = rowY + (layout.rowHeight() - layout.headSize()) * 0.5f;
            AbstractTexture texture = mc.getTextureManager().getTexture(row.player().getSkin().body().texturePath());
            scope.playerHead(
                    new LuminTexture(texture.getTexture(), texture.getTextureView(), texture.getSampler(), false, false),
                    x, headY, layout.headSize(), layout.headSize() * 0.12f, Color.WHITE
            );
            x += layout.headSize() + layout.headTextGap();
        }

        float nameTextWidth = Math.max(0.0f, layout.nameWidth() - layout.headSize() - layout.headTextGap());
        String name = trimToWidth(row.name(), layout.textScale(), nameTextWidth, textRenderer);
        scope.text(name, x, textY, layout.textScale(), row.nameColor());
        x = panelX + layout.paddingX() + layout.nameWidth() + layout.columnGap();
        scope.text(row.distance(), x, textY, layout.textScale(), textColor.getValue());
        x += layout.distanceWidth() + layout.columnGap();
        scope.text(row.health(), x, textY, layout.textScale(), row.healthColor());
        x += layout.healthWidth() + layout.columnGap();
        scope.text(row.pops(), x, textY, layout.textScale(), textColor.getValue());
        x += layout.popsWidth() + layout.columnGap();
        scope.text(row.direction(), x, textY, layout.textScale(), textColor.getValue());
    }

    private Color resolveNameColor(AbstractClientPlayer player) {
        return Managers.FRIEND != null && Managers.FRIEND.isFriend(player) ? FRIEND_COLOR : textColor.getValue();
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

    private String trimToWidth(String value, float scale, float maxWidth, TextRenderer textRenderer) {
        if (value == null || value.isEmpty() || maxWidth <= 0.0f) return "";
        if (textRenderer.getWidth(value, scale) <= maxWidth) return value;

        String ellipsis = "...";
        if (textRenderer.getWidth(ellipsis, scale) > maxWidth) return "";

        for (int length = value.length() - 1; length >= 0; length--) {
            String candidate = value.substring(0, length) + ellipsis;
            if (textRenderer.getWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
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
    private void onGameLeft(GameLeftEvent event) {
        popCounts.clear();
    }

    private record PlayerRow(
            AbstractClientPlayer player,
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
