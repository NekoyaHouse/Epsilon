package com.github.epsilon.elements.impl.island;

import com.github.epsilon.Constants;
import com.github.epsilon.elements.impl.island.instance.LandController;
import com.github.epsilon.elements.impl.island.instance.LandInstance;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.text.TextGlitchEffect;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class LandRenderer {

    private static final float WATERMARK_HEIGHT = 24f;
    private static final float HORIZONTAL_PADDING = 7.5f;
    private static final float SEGMENT_GAP = 4.5f;
    private static final float ICON_TEXT_GAP = 2.0f;
    private static final float ICON_SCALE = 1.2f;
    private static final float TEXT_SCALE = 0.85f;

    private final LandController controller;

    private final Animation radiusAnim = new Animation(Easing.DECELERATE, 160);
    private final Animation widthAnim = new Animation(Easing.EASE_OUT_BACK, 220);
    private final Animation heightAnim = new Animation(Easing.EASE_OUT_BACK, 220);

    private final Animation watermarkAlpha = new Animation(Easing.DECELERATE, 90);
    private final Animation contentAlpha = new Animation(Easing.DECELERATE, 90);

    private float x;
    private float y;

    private float watermarkWidth = 154f;

    private List<WatermarkSegment> watermarkSegments = List.of();

    private LandInstance current;

    private TextRenderer textRenderer;

    public LandRenderer(LandController controller, float defaultWidth, float defaultHeight) {
        this.controller = controller;

        radiusAnim.setStartValue(1.0f);
        widthAnim.setStartValue(defaultWidth);
        heightAnim.setStartValue(defaultHeight);

        watermarkAlpha.setStartValue(1.0f);
        contentAlpha.setStartValue(0.0f);
    }

    /**
     * 提供用于测量文本的渲染器。
     *
     * @param textRenderer 文本渲染器
     */
    public void setTextRenderer(TextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    /**
     * 选出当前实例并推进所有形变与淡入淡出动画。
     */
    public void update() {
        LandInstance current = controller.update();
        updateWatermarkText();
        boolean hasContent = current != null;

        watermarkAlpha.run(hasContent ? 0.0f : 1.0f);
        contentAlpha.run(hasContent ? 1.0f : 0.0f);

        if (current != null) {
            current.update();
            radiusAnim.run(current.getTargetRadius());
            widthAnim.run(current.getTargetWidth());
            heightAnim.run(current.getTargetHeight());
        } else {
            radiusAnim.run(1.0f);
            widthAnim.run(watermarkWidth);
            heightAnim.run(WATERMARK_HEIGHT);
        }

        this.current = current;
    }

    public void draw(Island island, UiTree.Scope scope) {
        float w = widthAnim.getValue();
        float h = heightAnim.getValue();

        if (w < 1f || h < 1f) return;

        float radius = Math.min(w, h) * 0.4f * Mth.clamp(radiusAnim.getValue(), 0f, 1f);

        if (island.backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(x, y, w, h, radius, island.blurStrength.getValue().floatValue());
        }
        if (island.drawShadow.getValue()) {
            scope.shadow(x, y, w, h, radius, island.shadowBlur.getValue().floatValue(), island.shadowColor.getValue());
        }
        scope.roundRect(x, y, w, h, radius, island.backgroundColor.getValue());
        if (island.outline.getValue()) {
            scope.outline(x, y, w, h, radius, island.outlineWidth.getValue().floatValue(), island.outlineColor.getValue());
        }

        UiRect clip = new UiRect(x, y, w, h);

        float watermarkAlpha = this.watermarkAlpha.getValue();
        if (watermarkAlpha > 0.001f) {
            scope.scissor(clip, inner -> drawWatermark(island, inner, watermarkAlpha));
        }

        LandInstance current = this.current;
        float contentAlpha = this.contentAlpha.getValue();
        if (current != null && contentAlpha > 0.001f) {
            current.setContentAlpha(contentAlpha);
            scope.scissor(clip, inner -> {
                inner.stackPush(clip);
                current.draw(inner, x, y, w, h);
                inner.stackPop();
            });
        }
    }

    private void updateWatermarkText() {
        String playerName = mc.player == null ? "Player" : mc.player.getGameProfile().name();
        String serverAddress;
        if (mc.isLocalServer()) {
            serverAddress = "Singleplayer";
        } else {
            ServerData serverData = mc.getCurrentServer();
            serverAddress = serverData == null || serverData.ip.isBlank() ? "Unknown server" : serverData.ip;
        }

        int latency = getLatency();
        Color latencyColor = getLatencyColor(latency);
        String latencyText = latency < 0 ? "--ms" : latency + "ms";

        watermarkSegments = List.of(
                new WatermarkSegment(IconChars.AUTO_AWESOME, Constants.NAME, IslandPalette.ACCENT_ALT, IslandPalette.ACCENT_ALT, true),
                new WatermarkSegment(IconChars.PERSON, playerName, IslandPalette.TEXT_PRIMARY, IslandPalette.TEXT_PRIMARY, false),
                new WatermarkSegment(IconChars.PUBLIC, serverAddress, IslandPalette.TEXT_SECONDARY, IslandPalette.TEXT_SECONDARY, false),
                new WatermarkSegment(IconChars.NETWORK_PING, latencyText, latencyColor, latencyColor, false),
                new WatermarkSegment(IconChars.SPEED, mc.getFps() + " FPS", IslandPalette.TEXT_SECONDARY, IslandPalette.TEXT_SECONDARY, false)
        );

        float contentWidth = 0f;
        for (int i = 0; i < watermarkSegments.size(); i++) {
            WatermarkSegment segment = watermarkSegments.get(i);
            TtfFontLoader font = StaticFontLoader.defaultFont();
            contentWidth += textRenderer.getWidth(segment.icon(), ICON_SCALE, StaticFontLoader.ICONS);
            contentWidth += ICON_TEXT_GAP;
            contentWidth += textRenderer.getWidth(segment.text(), TEXT_SCALE, font);
            if (i + 1 < watermarkSegments.size()) {
                contentWidth += SEGMENT_GAP;
            }
        }
        watermarkWidth = HORIZONTAL_PADDING * 2f + contentWidth;
    }

    private static int getLatency() {
        if (mc.isLocalServer()) return 0;

        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(mc.player.getUUID());
            if (playerInfo != null && playerInfo.getLatency() >= 0) {
                return playerInfo.getLatency();
            }
        }

        ServerData serverData = mc.getCurrentServer();
        if (serverData == null || serverData.ping < 0) return -1;
        return (int) Math.min(serverData.ping, Integer.MAX_VALUE);
    }

    private static Color getLatencyColor(int latency) {
        if (latency < 0) return IslandPalette.TEXT_MUTED;
        if (latency < 150) return IslandPalette.SUCCESS;
        if (latency < 300) return IslandPalette.WARNING;
        return IslandPalette.DANGER;
    }

    private void drawWatermark(Island island, UiTree.Scope scope, float alpha) {
        float centerY = y + WATERMARK_HEIGHT * 0.5f;
        float contentX = x + HORIZONTAL_PADDING;

        for (int i = 0; i < watermarkSegments.size(); i++) {
            WatermarkSegment segment = watermarkSegments.get(i);
            TtfFontLoader font = StaticFontLoader.defaultFont();
            float iconWidth = textRenderer.getWidth(segment.icon(), ICON_SCALE, StaticFontLoader.ICONS);
            float iconY = centerY - textRenderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS) * 0.5f;
            scope.text(segment.icon(), contentX, iconY, ICON_SCALE,
                    IslandPalette.withAlphaMul(segment.iconColor(), alpha), StaticFontLoader.ICONS);
            contentX += iconWidth + ICON_TEXT_GAP;

            float textY = centerY - textRenderer.getHeight(TEXT_SCALE, font) * 0.5f;
            Color textColor = IslandPalette.withAlphaMul(segment.textColor(), alpha);
            if (segment.glitch() && island.textGlitch.getValue()) {
                scope.glitchText(segment.text(), contentX, textY, TEXT_SCALE, textColor, new TextGlitchEffect(
                        island.chromaticX.getValue().floatValue(),
                        island.chromaticY.getValue().floatValue(),
                        island.glitchGlowRadius.getValue().floatValue(),
                        island.glitchGlowIntensity.getValue().floatValue(),
                        island.sliceHeight.getValue().floatValue(),
                        island.sliceAmount.getValue().floatValue(),
                        island.glitchStrength.getValue().floatValue(),
                        island.scanlineStrength.getValue().floatValue(),
                        island.glitchColor.getValue(),
                        island.noiseStrength.getValue().floatValue()
                ), font);
            } else {
                scope.text(segment.text(), contentX, textY, TEXT_SCALE, textColor, font);
            }

            contentX += textRenderer.getWidth(segment.text(), TEXT_SCALE, font);
            if (i + 1 < watermarkSegments.size()) {
                contentX += SEGMENT_GAP;
            }
        }
    }

    public void syncPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getWidth() {
        return widthAnim.getValue();
    }

    public float getHeight() {
        return heightAnim.getValue();
    }

    private record WatermarkSegment(String icon, String text, Color iconColor, Color textColor, boolean glitch) {
    }

}
