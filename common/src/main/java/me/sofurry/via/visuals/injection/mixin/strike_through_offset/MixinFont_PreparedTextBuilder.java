package me.sofurry.via.visuals.injection.mixin.strike_through_offset;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.ViaVersion;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Font.PreparedTextBuilder.class)
public abstract class MixinFont_PreparedTextBuilder {

    @Unique
    private static final float viaVersionVisuals$offset = 0.5F; // Magical offset to revert the changes done in 1.13 pre6->1.13 pre7

    @ModifyArg(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/glyphs/EffectGlyph;createEffect(FFFFFIIF)Lnet/minecraft/client/gui/font/TextRenderable;"), index = 1)
    private float fixStrikethroughMinY(float value) {
        if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return value - viaVersionVisuals$offset;
        } else {
            return value;
        }
    }

    @ModifyArg(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/glyphs/EffectGlyph;createEffect(FFFFFIIF)Lnet/minecraft/client/gui/font/TextRenderable;"), index = 3)
    private float fixStrikethroughMaxY(float value) {
        if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return value - viaVersionVisuals$offset;
        } else {
            return value;
        }
    }

}
