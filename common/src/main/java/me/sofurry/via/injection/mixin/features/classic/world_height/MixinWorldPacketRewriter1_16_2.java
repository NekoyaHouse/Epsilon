package me.sofurry.via.injection.mixin.features.classic.world_height;

import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.rewriter.WorldPacketRewriter1_16_2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = WorldPacketRewriter1_16_2.class, remap = false)
public abstract class MixinWorldPacketRewriter1_16_2 {

    @ModifyConstant(method = "lambda$register$0", constant = @Constant(intValue = 16))
    private static int modifySectionCountToSupportClassicWorldHeight(int constant) {
        return 64;
    }

}
