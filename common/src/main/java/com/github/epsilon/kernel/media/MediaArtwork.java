package com.github.epsilon.kernel.media;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class MediaArtwork {
    public static final int SIZE = 128;
    private final byte[] rgba;

    private MediaArtwork(byte[] rgba) {
        this.rgba = rgba;
    }

    public byte[] pixels() {
        return rgba.clone();
    }

    public static MediaArtwork decode(byte[] encoded) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported SMTC artwork");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long) width * height > 16_777_216L)
                    throw new IOException("SMTC artwork dimensions exceed limit");
                BufferedImage source = reader.read(0);
                BufferedImage square = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                var graphics = square.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
                    graphics.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
                } finally {
                    graphics.dispose();
                    source.flush();
                }
                byte[] rgba = new byte[SIZE * SIZE * 4];
                for (int y = 0; y < SIZE; y++)
                    for (int x = 0; x < SIZE; x++) {
                        int pixel = square.getRGB(x, y), offset = (y * SIZE + x) * 4;
                        rgba[offset] = (byte) (pixel >>> 16);
                        rgba[offset + 1] = (byte) (pixel >>> 8);
                        rgba[offset + 2] = (byte) pixel;
                        rgba[offset + 3] = (byte) (pixel >>> 24);
                    }
                square.flush();
                return new MediaArtwork(rgba);
            } finally {
                reader.dispose();
            }
        }
    }
}
