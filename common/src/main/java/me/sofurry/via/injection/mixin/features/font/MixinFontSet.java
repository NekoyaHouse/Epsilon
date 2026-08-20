package me.sofurry.via.injection.mixin.features.font;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.font.BuiltinEmptyGlyph1_12_2;
import me.sofurry.via.features.font.RenderableGlyphDiff;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontSet.class)
public abstract class MixinFontSet {

    @Shadow
    @Final
    private GlyphStitcher stitcher;

    @Shadow
    @Final
    private FontSet.SelectedGlyphs missingSelectedGlyphs;

    @Unique
    private BakedGlyph viaVersion$blankBakedGlyph1_12_2;

    @Unique
    private FontSet.SelectedGlyphs viaVersion$blankBakedGlyphPair1_12_2;

    @Unique

    @Inject(method = "resetTextures", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/glyphs/SpecialGlyphs;bake(Lnet/minecraft/client/gui/font/GlyphStitcher;)Lnet/minecraft/client/gui/font/glyphs/BakedSheetGlyph;", ordinal = 0))
    private void bakeBlankGlyph1_12_2(CallbackInfo ci) {
        this.viaVersion$blankBakedGlyph1_12_2 = BuiltinEmptyGlyph1_12_2.INSTANCE.bake(this.stitcher);
        this.viaVersion$blankBakedGlyphPair1_12_2 = new FontSet.SelectedGlyphs(() -> this.viaVersion$blankBakedGlyph1_12_2, () -> this.viaVersion$blankBakedGlyph1_12_2);
    }

    @Inject(method = "getGlyph", at = @At("RETURN"), cancellable = true)
    private void filterBakedGlyph(int codepoint, CallbackInfoReturnable<FontSet.SelectedGlyphs> cir) {
        if (this.viaVersion$shouldBeInvisible(codepoint)) {
            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
                cir.setReturnValue(viaVersion$blankBakedGlyphPair1_12_2);
            } else {
                cir.setReturnValue(missingSelectedGlyphs);
            }
        }
    }

    @Inject(method = "computeGlyphInfo", at = @At("RETURN"), cancellable = true)
    private void fixBlankGlyph1_12_2(int codepoint, CallbackInfoReturnable<FontSet.SelectedGlyphs> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            final FontSet.SelectedGlyphs glyphPair = cir.getReturnValue();
            cir.setReturnValue(glyphPair == this.missingSelectedGlyphs ? this.viaVersion$blankBakedGlyphPair1_12_2 : glyphPair);
        }
    }

    @Inject(method = "getRandomGlyph", at = @At("HEAD"))
    private void pauseCharacterFiltering(RandomSource random, int width, CallbackInfoReturnable<BakedGlyph> cir) {
    }

    @Inject(method = "getGlyph", at = @At("RETURN"))
    private void resumeCharacterFiltering(int codepoint, CallbackInfoReturnable<BakedGlyph> cir) {
    }

    @Unique
    private boolean viaVersion$shouldBeInvisible(final int codePoint) {
            return (stitcher.texturePrefix.equals(Minecraft.DEFAULT_FONT)) && !RenderableGlyphDiff.isGlyphRenderable(codePoint);
        } else {
            return false;
        }
    }

}
