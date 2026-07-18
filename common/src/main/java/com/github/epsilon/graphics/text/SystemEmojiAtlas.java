package com.github.epsilon.graphics.text;

import com.github.epsilon.graphics.LuminTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author 06789
 * 我也不知道这他妈是个什么 78 东西。
 */
public final class SystemEmojiAtlas implements AutoCloseable {

    public static final SystemEmojiAtlas INSTANCE = new SystemEmojiAtlas();

    private static final int ATLAS_SIZE = 512;
    private static final int CELL_SIZE = 128;
    private static final int CELLS_PER_ROW = ATLAS_SIZE / CELL_SIZE;
    private static final int FONT_SIZE = 96;

    private final Map<String, EmojiGlyph> glyphs = new LinkedHashMap<>();
    private final Font emojiFont;
    private final ColorGlyphFont colorGlyphFont;
    private BufferedImage atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
    private LuminTexture texture;
    private int nextCell;

    private SystemEmojiAtlas() {
        Path fontPath = systemEmojiFontPath();
        this.emojiFont = loadFont(fontPath).deriveFont(Font.PLAIN, FONT_SIZE);
        this.colorGlyphFont = ColorGlyphFont.load(fontPath);
    }

    public EmojiGlyph get(String emoji) {
        EmojiGlyph glyph = glyphs.get(emoji);
        if (glyph != null) {
            return glyph;
        }

        if (nextCell >= CELLS_PER_ROW * CELLS_PER_ROW) {
            clearAtlas();
        }

        int cell = nextCell++;
        int cellX = cell % CELLS_PER_ROW;
        int cellY = cell / CELLS_PER_ROW;
        int x = cellX * CELL_SIZE;
        int y = cellY * CELL_SIZE;
        drawEmojiToAtlas(emoji, x, y);
        uploadAtlas();

        float u0 = x / (float) ATLAS_SIZE;
        float v0 = y / (float) ATLAS_SIZE;
        float u1 = (x + CELL_SIZE) / (float) ATLAS_SIZE;
        float v1 = (y + CELL_SIZE) / (float) ATLAS_SIZE;
        glyph = new EmojiGlyph(texture, u0, v0, u1, v1);
        glyphs.put(emoji, glyph);
        return glyph;
    }

    private void clearAtlas() {
        glyphs.clear();
        nextCell = 0;
        Graphics2D graphics = atlasImage.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
        } finally {
            graphics.dispose();
        }
    }

    private void drawEmojiToAtlas(String emoji, int x, int y) {
        Graphics2D graphics = atlasImage.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setFont(emojiFont);

            FontRenderContext context = graphics.getFontRenderContext();
            GlyphVector glyphVector = emojiFont.createGlyphVector(context, emoji);
            Rectangle2D bounds = glyphVector.getVisualBounds();
            float drawX = (float) (x + (CELL_SIZE - bounds.getWidth()) / 2.0 - bounds.getX());
            float drawY = (float) (y + (CELL_SIZE - bounds.getHeight()) / 2.0 - bounds.getY());
            int glyphId = glyphVector.getNumGlyphs() > 0 ? glyphVector.getGlyphCode(0) & 0xFFFF : -1;

            if (!drawColorGlyph(graphics, context, glyphId, drawX, drawY)) {
                graphics.setColor(Color.WHITE);
                graphics.drawString(emoji, drawX, drawY);
            }
        } finally {
            graphics.dispose();
        }
    }

    private boolean drawColorGlyph(Graphics2D graphics, FontRenderContext context, int glyphId, float x, float y) {
        if (colorGlyphFont == null || glyphId < 0) {
            return false;
        }

        ColorGlyphLayer[] layers = colorGlyphFont.layers(glyphId);
        if (layers == null || layers.length == 0) {
            return false;
        }

        for (ColorGlyphLayer layer : layers) {
            Color color = colorGlyphFont.color(layer.paletteIndex());
            if (color == null || color.getAlpha() == 0) {
                continue;
            }
            GlyphVector vector = emojiFont.createGlyphVector(context, new int[]{layer.glyphId()});
            graphics.setColor(color);
            graphics.drawGlyphVector(vector, x, y);
        }
        return true;
    }

    private void uploadAtlas() {
        try (NativeImage image = toNativeImage(atlasImage)) {
            if (texture == null) {
                var device = RenderSystem.getDevice();
                GpuTexture gpuTexture = device.createTexture(
                        "epsilon/system_emoji_atlas",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        TextureFormat.RGBA8,
                        ATLAS_SIZE,
                        ATLAS_SIZE,
                        1,
                        1
                );
                GpuTextureView view = device.createTextureView(gpuTexture);
                GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                texture = new LuminTexture(gpuTexture, view, sampler, true, false);
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), image);
        }
    }

    private static Path systemEmojiFontPath() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            String windowsDir = System.getenv("WINDIR");
            return windowsDir == null ? Path.of("C:\\Windows\\Fonts\\seguiemj.ttf") : Path.of(windowsDir, "Fonts", "seguiemj.ttf");
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Path.of("/System/Library/Fonts/Apple Color Emoji.ttc");
        }
        Path noto = Path.of("/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf");
        return Files.isRegularFile(noto) ? noto : Path.of("/usr/share/fonts/opentype/noto/NotoColorEmoji.ttf");
    }

    private static Font loadFont(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Font.createFont(Font.TRUETYPE_FONT, path.toFile());
            }
        } catch (FontFormatException | IOException ignored) {
        }
        return new Font(systemEmojiFamily(), Font.PLAIN, FONT_SIZE);
    }

    private static String systemEmojiFamily() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "Segoe UI Emoji";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "Apple Color Emoji";
        }
        return "Noto Color Emoji";
    }

    private static NativeImage toNativeImage(BufferedImage bufferedImage) {
        NativeImage image = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), true);
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                image.setPixel(x, y, bufferedImage.getRGB(x, y));
            }
        }
        return image;
    }

    @Override
    public void close() {
        glyphs.clear();
        atlasImage = null;
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    public record EmojiGlyph(LuminTexture texture, float u0, float v0, float u1, float v1) {
    }

    private record ColorGlyphLayer(int glyphId, int paletteIndex) {
    }

    private record ColorGlyphFont(Map<Integer, ColorGlyphLayer[]> layers, Color[] colors) {
        static ColorGlyphFont load(Path path) {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.BIG_ENDIAN);
                Table colr = table(data, "COLR");
                Table cpal = table(data, "CPAL");
                if (colr == null || cpal == null) {
                    return null;
                }
                Color[] colors = readCpal(data, cpal);
                Map<Integer, ColorGlyphLayer[]> layers = readColr(data, colr);
                return colors.length == 0 || layers.isEmpty() ? null : new ColorGlyphFont(layers, colors);
            } catch (RuntimeException | IOException ignored) {
                return null;
            }
        }

        ColorGlyphLayer[] layers(int glyphId) {
            return layers.get(glyphId);
        }

        Color color(int paletteIndex) {
            if (paletteIndex == 0xFFFF || paletteIndex < 0 || paletteIndex >= colors.length) {
                return Color.WHITE;
            }
            return colors[paletteIndex];
        }

        private static Map<Integer, ColorGlyphLayer[]> readColr(ByteBuffer data, Table table) {
            int base = table.offset();
            int numBaseGlyphRecords = u16(data, base + 2);
            int baseGlyphRecordsOffset = i32(data, base + 4);
            int layerRecordsOffset = i32(data, base + 8);
            int numLayerRecords = u16(data, base + 12);
            Map<Integer, ColorGlyphLayer[]> result = new LinkedHashMap<>();
            for (int i = 0; i < numBaseGlyphRecords; i++) {
                int record = base + baseGlyphRecordsOffset + i * 6;
                int glyphId = u16(data, record);
                int firstLayerIndex = u16(data, record + 2);
                int numLayers = u16(data, record + 4);
                if (firstLayerIndex + numLayers > numLayerRecords) {
                    continue;
                }
                ColorGlyphLayer[] layers = new ColorGlyphLayer[numLayers];
                for (int layerIndex = 0; layerIndex < numLayers; layerIndex++) {
                    int layerRecord = base + layerRecordsOffset + (firstLayerIndex + layerIndex) * 4;
                    layers[layerIndex] = new ColorGlyphLayer(u16(data, layerRecord), u16(data, layerRecord + 2));
                }
                result.put(glyphId, layers);
            }
            return result;
        }

        private static Color[] readCpal(ByteBuffer data, Table table) {
            int base = table.offset();
            int numPaletteEntries = u16(data, base + 2);
            int numColorRecords = u16(data, base + 6);
            int offsetFirstColorRecord = i32(data, base + 8);
            int firstPaletteIndex = u16(data, base + 12);
            int colorsToRead = Math.min(numPaletteEntries, Math.max(0, numColorRecords - firstPaletteIndex));
            Color[] colors = new Color[colorsToRead];
            for (int i = 0; i < colors.length; i++) {
                int colorRecord = base + offsetFirstColorRecord + (firstPaletteIndex + i) * 4;
                int blue = u8(data, colorRecord);
                int green = u8(data, colorRecord + 1);
                int red = u8(data, colorRecord + 2);
                int alpha = u8(data, colorRecord + 3);
                colors[i] = new Color(red, green, blue, alpha);
            }
            return colors;
        }

        private static Table table(ByteBuffer data, String tag) {
            int numTables = u16(data, 4);
            for (int i = 0; i < numTables; i++) {
                int record = 12 + i * 16;
                if (tag.equals(tag(data, record))) {
                    return new Table(i32(data, record + 8), i32(data, record + 12));
                }
            }
            return null;
        }

        private static String tag(ByteBuffer data, int offset) {
            return String.valueOf(new char[]{
                    (char) u8(data, offset),
                    (char) u8(data, offset + 1),
                    (char) u8(data, offset + 2),
                    (char) u8(data, offset + 3)
            });
        }

        private static int u8(ByteBuffer data, int offset) {
            return data.get(offset) & 0xFF;
        }

        private static int u16(ByteBuffer data, int offset) {
            return data.getShort(offset) & 0xFFFF;
        }

        private static int i32(ByteBuffer data, int offset) {
            return data.getInt(offset);
        }
    }

    private record Table(int offset, int length) {
    }
}
