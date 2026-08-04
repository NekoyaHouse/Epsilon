package com.github.epsilon.graphics;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.utils.network.Http;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static com.github.epsilon.Constants.mc;

public class PlayerHeadTexture extends LuminTexture {

    private PlayerHeadTexture(Uploaded uploaded) {
        super(uploaded.texture(), uploaded.view(), uploaded.sampler(), true, false);
    }

    public PlayerHeadTexture(byte[] head) {
        this(upload(toNativeImage(head)));
    }

    public PlayerHeadTexture() {
        this(upload(loadSteveHead()));
    }

    private static NativeImage toNativeImage(byte[] head) {
        NativeImage image = new NativeImage(8, 8, false);
        image.getPixelBytes().put(head).rewind();
        return image;
    }

    private static NativeImage loadSteveHead() {
        try (InputStream stream = mc.getResourceManager()
                .getResourceOrThrow(ResourceLocationUtils.getIdentifier("textures/gui/steve.png"))
                .open()) {
            return NativeImage.read(stream);
        } catch (IOException e) {
            return MissingTextureAtlasSprite.generateMissingImage();
        }
    }

    private static Uploaded upload(NativeImage image) {
        var device = RenderSystem.getDevice();
        GpuTexture texture = device.createTexture(
                "player-head",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                image.getWidth(), image.getHeight(), 1, 1);
        device.createCommandEncoder().writeToTexture(texture, image);
        GpuTextureView view = device.createTextureView(texture);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        image.close();
        return new Uploaded(texture, view, sampler);
    }

    public static byte[] downloadHead(String url) throws IOException {
        BufferedImage skin;
        try (InputStream in = Http.get(url).sendInputStream()) {
            skin = ImageIO.read(in);
        }

        if (skin == null) throw new IOException("Failed to decode skin image.");

        byte[] head = new byte[8 * 8 * 4];
        int[] pixel = new int[4];

        int i = 0;
        for (int x = 8; x < 16; x++) {
            for (int y = 8; y < 16; y++) {
                skin.getData().getPixel(x, y, pixel);

                for (int j = 0; j < 4; j++) {
                    head[i++] = (byte) pixel[j];
                }
            }
        }

        i = 0;
        for (int x = 40; x < 48; x++) {
            for (int y = 8; y < 16; y++) {
                skin.getData().getPixel(x, y, pixel);

                if (pixel[3] != 0) {
                    for (int j = 0; j < 4; j++) {
                        head[i++] = (byte) pixel[j];
                    }
                } else i += 4;
            }
        }

        // 顺时针旋转 90°：源像素 (x, y) -> 目标 (7 - y, x)
        byte[] rotated = new byte[8 * 8 * 4];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                System.arraycopy(head, (y * 8 + x) * 4, rotated, (x * 8 + (7 - y)) * 4, 4);
            }
        }

        return rotated;
    }

    private record Uploaded(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
    }

}
