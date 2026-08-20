package me.sofurry.via.features.font;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;

public enum BuiltinEmptyGlyph1_12_2 implements GlyphInfo {
    INSTANCE;

    private static final int WIDTH = 0;
    private static final int HEIGHT = 8;

    @Override
    public float getAdvance() {
        return WIDTH;
    }

    public BakedSheetGlyph bake(final GlyphStitcher baker) {
        return baker.stitch(this, new GlyphBitmap() {

            @Override
            public int getPixelWidth() {
                return WIDTH;
            }

            @Override
            public int getPixelHeight() {
                return HEIGHT;
            }

            @Override
            public void upload(final int x, final int y, final GpuTexture texture) {
            }

            @Override
            public float getOversample() {
                return 1F;
            }

            @Override
            public boolean isColored() {
                return true;
            }
        });
    }

}
