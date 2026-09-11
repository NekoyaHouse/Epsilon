package com.github.epsilon.elements.impl.island.instance.impl;

import com.github.epsilon.Constants;
import com.github.epsilon.elements.impl.island.IslandPalette;
import com.github.epsilon.elements.impl.island.instance.LandInstance;
import com.github.epsilon.elements.impl.island.pattern.LCPattern;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.mojang.blaze3d.platform.NativeImage;
import me.sofurry.smtc.SmtcService;
import me.sofurry.smtc.SmtcSnapshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;

import static com.github.epsilon.Constants.mc;

/**
 * 将 Windows SMTC 当前媒体会话绘制为 Island 内容。
 */
public class MusicInstance extends LandInstance {

    private static final Identifier COVER_TEXTURE = Identifier.fromNamespaceAndPath("epsilon", "smtc/album_art");

    private static final float PADDING = 7f;
    private static final float COVER_SIZE = 42f;
    private static final float COVER_RADIUS = 8f;
    private static final float COVER_GAP = 8f;
    private static final float MIN_WIDTH = 216f;
    private static final float MAX_WIDTH = 286f;
    private static final float TITLE_SCALE = 1.0f;
    private static final float ARTIST_SCALE = 0.78f;
    private static final float META_SCALE = 0.68f;
    private static final float ICON_SCALE = 0.78f;
    private static final float WAVE_WIDTH = 18f;
    private static final float TITLE_STATUS_GAP = 8f;
    private static final float MARQUEE_SPEED = 22f;
    private static final float MARQUEE_START_HOLD_SECONDS = 1.25f;
    private static final float MARQUEE_GAP = 28f;
    private static final int COVER_TEXTURE_SIZE = 256;

    private final SmtcService service;
    private final Supplier<TextRenderer> textRendererSupplier;

    private SmtcSnapshot snapshot = SmtcSnapshot.UNAVAILABLE;
    private long coverRevision = Long.MIN_VALUE;
    private DynamicTexture coverTexture;
    private boolean coverAvailable;
    private String marqueeTitle = "";
    private long marqueeStartedAtNs = System.nanoTime();

    public MusicInstance(SmtcService service, Supplier<TextRenderer> textRendererSupplier, LCPattern pattern) {
        super(pattern, 1);
        this.service = service;
        this.textRendererSupplier = textRendererSupplier;
    }

    @Override
    public void update() {
        snapshot = service.snapshot();
        updateCoverTexture(snapshot);

        String nextTitle = title(snapshot);
        if (!nextTitle.equals(marqueeTitle)) {
            marqueeTitle = nextTitle;
            marqueeStartedAtNs = System.nanoTime();
        }

        targetRadius = 0.42f;
        targetHeight = PADDING * 2f + COVER_SIZE;

        TextRenderer textRenderer = textRendererSupplier.get();
        String title = title(snapshot);
        String artist = artist(snapshot);
        String metadata = metadata(snapshot);
        float desiredContent = Math.max(
                textRenderer.getWidth(title, TITLE_SCALE) + WAVE_WIDTH + TITLE_STATUS_GAP,
                Math.max(textRenderer.getWidth(artist, ARTIST_SCALE), textRenderer.getWidth(metadata, META_SCALE))
        );
        float desiredWidth = PADDING * 2f + COVER_SIZE + COVER_GAP + desiredContent;
        float screenMaxWidth = Math.max(MIN_WIDTH, LuminRenderSystem.getScaledWidthInt() - 50f);
        targetWidth = Mth.clamp(desiredWidth, MIN_WIDTH, Math.min(MAX_WIDTH, screenMaxWidth));
    }

    @Override
    public void draw(UiTree.Scope scope, float translateX, float translateY, float width, float height) {
        if (!snapshot.available()) return;

        drawCover(scope);

        TextRenderer textRenderer = textRendererSupplier.get();
        float textX = PADDING + COVER_SIZE + COVER_GAP;
        float contentRight = width - PADDING;
        float titleRight = contentRight - WAVE_WIDTH - TITLE_STATUS_GAP;
        float titleViewport = Math.max(1f, titleRight - textX);

        float titleY = PADDING + 1f;
        drawMarqueeTitle(scope, textRenderer, title(snapshot), textX, titleY, titleViewport);
        drawPlaybackWave(scope, contentRight - WAVE_WIDTH, titleY + 1f, WAVE_WIDTH, 9f);

        float artistY = titleY + textRenderer.getHeight(TITLE_SCALE) + 3f;
        drawIconText(scope, IconChars.ARTIST, artist(snapshot), textX, artistY,
                Math.max(1f, contentRight - textX), ARTIST_SCALE, IslandPalette.TEXT_SECONDARY);

        float metaY = height - PADDING - textRenderer.getHeight(META_SCALE) - 1f;
        drawIconText(scope, snapshot.albumTitle().isBlank() ? IconChars.AUDIO_FILE : IconChars.ALBUM, metadata(snapshot), textX, metaY, Math.max(1f, contentRight - textX), META_SCALE, IslandPalette.TEXT_MUTED);
    }

    private void drawCover(UiTree.Scope scope) {
        scope.outline(PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 0.75f, fade(new Color(255, 255, 255, 48)));

        if (coverAvailable) {
            scope.roundedTexture(COVER_TEXTURE, PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 0f, 0f, 1f, 1f, fade(Color.WHITE), true);
        } else {
            scope.roundRectHorizontalGradient(PADDING, PADDING, COVER_SIZE, COVER_SIZE, COVER_RADIUS, fade(new Color(75, 88, 116)), fade(new Color(67, 125, 122)));
            float iconScale = 1.35f;
            TextRenderer textRenderer = textRendererSupplier.get();
            float iconWidth = textRenderer.getWidth(IconChars.ALBUM, iconScale, StaticFontLoader.ICONS);
            float iconHeight = textRenderer.getHeight(iconScale, StaticFontLoader.ICONS);
            scope.text(IconChars.ALBUM, PADDING + (COVER_SIZE - iconWidth) * 0.5f, PADDING + (COVER_SIZE - iconHeight) * 0.5f, iconScale, fade(IslandPalette.TEXT_PRIMARY), StaticFontLoader.ICONS);
        }
    }

    private void drawMarqueeTitle(UiTree.Scope scope, TextRenderer textRenderer, String title,
                                  float x, float y, float viewportWidth) {
        float textWidth = textRenderer.getWidth(title, TITLE_SCALE);
        if (textWidth <= viewportWidth) {
            scope.text(title, x, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY));
            return;
        }

        float elapsedSeconds = (System.nanoTime() - marqueeStartedAtNs) / 1_000_000_000.0f;
        float scrollSeconds = Math.max(0f, elapsedSeconds - MARQUEE_START_HOLD_SECONDS);
        float cycle = textWidth + MARQUEE_GAP;
        float offset = -(scrollSeconds * MARQUEE_SPEED % cycle);
        UiRect clip = new UiRect(x, y - 1f, viewportWidth, textRenderer.getHeight(TITLE_SCALE) + 2f);
        scope.scissor(clip, inner -> {
            inner.text(title, x + offset, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY));
            inner.text(title, x + offset + cycle, y, TITLE_SCALE, fade(IslandPalette.TEXT_PRIMARY));
        });
    }

    private void drawIconText(UiTree.Scope scope, String icon, String text, float x, float y,
                              float viewportWidth, float textScale, Color color) {
        TextRenderer textRenderer = textRendererSupplier.get();
        float iconWidth = textRenderer.getWidth(icon, ICON_SCALE, StaticFontLoader.ICONS);
        float iconY = y + (textRenderer.getHeight(textScale) - textRenderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS)) * 0.5f;
        scope.text(icon, x, iconY, ICON_SCALE, fade(IslandPalette.ACCENT), StaticFontLoader.ICONS);

        float textX = x + iconWidth + 3f;
        float textViewport = Math.max(1f, viewportWidth - iconWidth - 3f);
        UiRect clip = new UiRect(textX, y - 1f, textViewport, textRenderer.getHeight(textScale) + 2f);
        scope.scissor(clip, inner -> inner.text(text, textX, y, textScale, fade(color)));
    }

    private void drawPlaybackWave(UiTree.Scope scope, float x, float y, float width, float height) {
        int bars = 4;
        float barWidth = 2f;
        float gap = (width - bars * barWidth) / (bars - 1);
        double time = System.nanoTime() / 1_000_000_000.0;

        for (int index = 0; index < bars; index++) {
            float amplitude = 0.28f + 0.72f * (float) ((Math.sin(time * 4.8 + index * 1.7) + 1.0) * 0.5);
            float barHeight = Math.max(2f, height * amplitude);
            float barX = x + index * (barWidth + gap);
            float barY = y + (height - barHeight) * 0.5f;
            Color color = index < 2 ? IslandPalette.ACCENT : IslandPalette.ACCENT_ALT;
            scope.roundRect(barX, barY, barWidth, barHeight, barWidth * 0.5f, fade(color));
        }
    }

    private void updateCoverTexture(SmtcSnapshot next) {
        if (next.thumbnailRevision() == coverRevision) return;

        coverRevision = next.thumbnailRevision();
        coverAvailable = false;
        byte[] thumbnail = next.thumbnail();
        if (thumbnail == null || thumbnail.length == 0) return;

        NativeImage image = null;
        DynamicTexture newTexture = null;
        try {
            image = decodeThumbnail(thumbnail);
            if (coverTexture == null) {
                newTexture = new DynamicTexture(() -> "Epsilon SMTC album art", image);
                image = null;
                mc.getTextureManager().register(COVER_TEXTURE, newTexture);
                coverTexture = newTexture;
                newTexture = null;
            } else {
                coverTexture.setPixels(image);
                image = null;
                coverTexture.upload();
            }
            coverAvailable = true;
        } catch (IOException | RuntimeException e) {
            if (newTexture != null) {
                newTexture.close();
            } else if (image != null) {
                image.close();
            }
            Constants.LOGGER.warn("Failed to decode SMTC album art revision {}", coverRevision, e);
        }
    }

    /**
     * SMTC 封面可能是 JPEG；Minecraft 26.2 的 NativeImage.read 会先强制校验 PNG。
     * 这里先通过 ImageIO 解码并裁成方形，再显式写入 NativeImage。
     */
    private static NativeImage decodeThumbnail(byte[] thumbnail) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(thumbnail));
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new IOException("Unsupported SMTC thumbnail image format");
        }

        int cropSize = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - cropSize) / 2;
        int cropY = (source.getHeight() - cropSize) / 2;
        BufferedImage normalized = new BufferedImage(COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE, cropX, cropY, cropX + cropSize, cropY + cropSize, null);
        graphics.dispose();

        NativeImage result = new NativeImage(COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE, false);
        try {
            for (int y = 0; y < COVER_TEXTURE_SIZE; y++) {
                for (int x = 0; x < COVER_TEXTURE_SIZE; x++) {
                    result.setPixel(x, y, normalized.getRGB(x, y));
                }
            }
            return result;
        } catch (RuntimeException e) {
            result.close();
            throw e;
        }
    }

    private void releaseCoverTexture() {
        if (coverTexture != null) {
            mc.getTextureManager().release(COVER_TEXTURE);
            coverTexture = null;
        }
        coverAvailable = false;
    }

    private static String title(SmtcSnapshot snapshot) {
        return snapshot.title().isBlank() ? "Unknown track" : snapshot.title();
    }

    private static String artist(SmtcSnapshot snapshot) {
        return snapshot.artist().isBlank() ? "Unknown artist" : snapshot.artist();
    }

    private static String metadata(SmtcSnapshot snapshot) {
        String source = sourceName(snapshot.sourceAppId());
        if (snapshot.albumTitle().isBlank()) return source;
        if (source.isBlank()) return snapshot.albumTitle();
        return snapshot.albumTitle() + "  /  " + source;
    }

    private static String sourceName(String sourceAppId) {
        if (sourceAppId == null || sourceAppId.isBlank()) return "Windows media";

        String value = sourceAppId;
        int appSeparator = value.lastIndexOf('!');
        if (appSeparator >= 0 && appSeparator + 1 < value.length()) {
            value = value.substring(appSeparator + 1);
        }
        if (value.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            value = value.substring(0, value.length() - 4);
        }
        return value.isBlank() ? "Windows media" : value;
    }

    @Override
    public void onRemoved() {
        releaseCoverTexture();
    }

}
