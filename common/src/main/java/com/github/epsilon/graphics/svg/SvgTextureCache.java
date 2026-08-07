package com.github.epsilon.graphics.svg;

import com.github.epsilon.Constants;
import com.github.epsilon.graphics.LuminTexture;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.github.epsilon.Constants.mc;

/**
 * 将资源包中的 SVG 栅格化为 GPU 纹理，并按资源及像素尺寸缓存。
 */
public class SvgTextureCache {

    public static final SvgTextureCache INSTANCE = new SvgTextureCache();

    private static final int MAX_TEXTURE_SIZE = 4096;

    private final SVGLoader loader = new SVGLoader();
    private final Map<SvgKey, LuminTexture> cache = new HashMap<>();
    private final Set<SvgKey> failed = new HashSet<>();

    private SvgTextureCache() {
    }

    public synchronized LuminTexture get(Identifier source, int pixelWidth, int pixelHeight) {
        RenderSystem.assertOnRenderThread();
        SvgKey key = new SvgKey(source, clampSize(pixelWidth), clampSize(pixelHeight));
        LuminTexture cached = cache.get(key);
        if (cached != null || failed.contains(key)) {
            return cached;
        }

        try {
            LuminTexture texture = load(key);
            cache.put(key, texture);
            return texture;
        } catch (Exception exception) {
            failed.add(key);
            Constants.LOGGER.error("Failed to load SVG texture {} at {}x{}", source, key.width(), key.height(), exception);
            return null;
        }
    }

    public synchronized void clear() {
        for (LuminTexture texture : cache.values()) {
            texture.close();
        }
        cache.clear();
        failed.clear();
    }

    private LuminTexture load(SvgKey key) throws Exception {
        SVGDocument document;
        var resource = mc.getResourceManager().getResourceOrThrow(key.source());
        try (InputStream stream = resource.open()) {
            document = loader.load(stream);
        }
        if (document == null) {
            throw new IllegalArgumentException("SVG document is empty: " + key.source());
        }

        BufferedImage bufferedImage = new BufferedImage(key.width(), key.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        document.render(null, graphics, new ViewBox(0.0f, 0.0f, key.width(), key.height()));
        graphics.dispose();

        try (NativeImage image = toNativeImage(bufferedImage)) {
            var device = RenderSystem.getDevice();
            GpuTexture gpuTexture = device.createTexture(
                    "epsilon/svg/" + key.source() + "/" + key.width() + "x" + key.height(),
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM,
                    key.width(), key.height(), 1, 1
            );
            GpuTextureView view = device.createTextureView(gpuTexture);
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            device.createCommandEncoder().writeToTexture(gpuTexture, image);
            return new LuminTexture(gpuTexture, view, sampler, true, false);
        }
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

    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_TEXTURE_SIZE);
    }

    private record SvgKey(Identifier source, int width, int height) {
    }

}
