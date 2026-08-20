package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockGetter.class)
public interface MixinBlockGetter {

    @ModifyExpressionValue(method = "forEachBlockIntersectedBetween", at = @At(value = "CONSTANT", args = "floatValue=1.0E-5"))
    private static float use1_21_8Epsilon(float original) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_7)) {
            return 0.99999F;
        } else {
            return original;
        }
    }

}
