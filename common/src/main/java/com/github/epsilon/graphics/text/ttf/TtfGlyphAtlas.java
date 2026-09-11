package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminTexture;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.epsilon.Constants.mc;

public class TtfGlyphAtlas {

    private static final int SIZE = 1024;
    private static final int GLYPH_GUTTER = 2;
    /** Vulkan 要求 bufferOffset 为格式 texel block size 的倍数，取 4 同时覆盖 R8（1）与 RGBA8（4）。 */
    private static final long STAGING_ALIGNMENT = 4L;
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();
    private final LuminTexture texture;
    private final LuminTexture alphaTexture;
    private final Identifier textureId;
    private final Identifier alphaTextureId;

    private int currentX = 0;
    private int currentY = 0;
    private int currentRowHeight = 0;

    public TtfGlyphAtlas(int atlasId) {
        this.textureId = Identifier.fromNamespaceAndPath("epsilon", "ttf_atlas/" + NEXT_TEXTURE_ID.getAndIncrement());
        this.alphaTextureId = Identifier.fromNamespaceAndPath("epsilon", "ttf_alpha_atlas/" + NEXT_TEXTURE_ID.getAndIncrement());

        final var texture = RenderSystem.getDevice().createTexture(
                () -> "Lumin-TtfGlyphAtlas",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.R8_UNORM,
                SIZE, SIZE,
                1, 1
        );
        final var alphaTexture = RenderSystem.getDevice().createTexture(
                () -> "Lumin-TtfGlyphAlphaAtlas",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.R8_UNORM,
                SIZE, SIZE,
                1, 1
        );

        final var textureView = RenderSystem.getDevice().createTextureView(texture);
        final var sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR,
                1, OptionalDouble.empty()
        );

        this.texture = new LuminTexture(texture, textureView, sampler);
        final var alphaTextureView = RenderSystem.getDevice().createTextureView(alphaTexture);
        final var alphaSampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR,
                1, OptionalDouble.empty()
        );
        this.alphaTexture = new LuminTexture(alphaTexture, alphaTextureView, alphaSampler);
        fillTexture(texture, (byte) 0xFF);
        fillTexture(alphaTexture, (byte) 0x00);
        mc.getTextureManager().register(this.textureId, this.texture);
        mc.getTextureManager().register(this.alphaTextureId, this.alphaTexture);
    }

    private static void fillTexture(GpuTexture texture, byte value) {
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(pixels), value & 0xFF, SIZE * SIZE);
            uploadToTexture(texture, pixels, 0, 0, SIZE, SIZE);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /**
     * 上传一段像素数据到 atlas 纹理。
     * <p>
     * Vulkan 下 {@code VkBufferImageCopy.bufferOffset} 必须是目标格式 texel block size 的倍数
     * （VUID-vkCmdCopyBufferToImage-dstImage-07975），而 blaze3d 的
     * {@code writeToTexture(GpuTexture, ByteBuffer, ...)} 固定按 alignment = 1 申请 staging，
     * 且暂存区游标按原始长度前进。字形是 R8 且宽高不保证 4 字节对齐，一旦游标错位，
     * 之后走同一路径的 RGBA8 纹理上传（视频帧、动态纹理等）都会拿到非法的 bufferOffset。
     * 因此 Vulkan 下自行申请 4 字节对齐的暂存区，并把分配长度补齐到 4 的倍数；
     * 后端判定由 {@link LuminRenderSystem#IS_VULKAN_BACKEND} 提供。
     */
    private static void uploadToTexture(GpuTexture texture, ByteBuffer data, int destX, int destY, int width, int height) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (!LuminRenderSystem.IS_VULKAN_BACKEND) {
            encoder.writeToTexture(texture, data, 0, 0, destX, destY, width, height);
            return;
        }

        long stagingSize = Mth.roundToward(data.remaining(), STAGING_ALIGNMENT);
        try (GpuBufferSlice.MappedView staging = encoder.transientMemory()
                .allocateStaging(stagingSize, STAGING_ALIGNMENT, GpuBuffer.USAGE_COPY_SRC)) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(staging.data()), data.remaining());
            encoder.copyBufferToTexture(
                    staging.slice(),
                    0, 0,
                    width, height,
                    texture,
                    destX, destY,
                    width, height,
                    0, 0
            );
        }
    }

    /**
     * Try to append a glyph to atlas
     * <p>
     * Return null if glyph atlas is full
     */
    public GlyphUV appendGlyph(TtfGlyph glyph) {
        if (glyph.glyphData() == null) return null;

        int cellWidth = glyph.width() + GLYPH_GUTTER * 2;
        int cellHeight = glyph.height() + GLYPH_GUTTER * 2;

        if (currentX + cellWidth >= SIZE) {
            currentX = 0;
            currentY += currentRowHeight;
            currentRowHeight = 0;
        }

        // Return null if glyph atlas is full
        if (currentY + cellHeight >= SIZE) {
            return null;
        }

        int glyphX = currentX + GLYPH_GUTTER;
        int glyphY = currentY + GLYPH_GUTTER;

        uploadToTexture(
                this.texture.getTexture(),
                glyph.glyphData(),
                glyphX, glyphY,
                glyph.width(),
                glyph.height()
        );
        if (glyph.alphaData() != null) {
            uploadToTexture(
                    this.alphaTexture.getTexture(),
                    glyph.alphaData(),
                    glyphX, glyphY,
                    glyph.width(),
                    glyph.height()
            );
        }

        GlyphUV uv = new GlyphUV(
                (float) glyphX / SIZE,
                (float) glyphY / SIZE,
                (float) (glyphX + glyph.width()) / SIZE,
                (float) (glyphY + glyph.height()) / SIZE
        );

        currentX += cellWidth;
        currentRowHeight = Math.max(currentRowHeight, cellHeight);

        return uv;
    }

    public LuminTexture getTexture() {
        return texture;
    }

    public LuminTexture getAlphaTexture() {
        return alphaTexture;
    }

    public static int getSize() {
        return SIZE;
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public void destroy() {
        mc.getTextureManager().release(this.textureId);
        mc.getTextureManager().release(this.alphaTextureId);
    }

    public record GlyphUV(float u0, float v0, float u1, float v1) {
    }

}
