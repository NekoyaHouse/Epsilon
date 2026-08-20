package me.sofurry.via.injection.mixin.features.recipe;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.viaversion.viaversion.protocols.v1_11_1to1_12.rewriter.EntityPacketRewriter1_12$1", remap = false)
public abstract class MixinEntityPacketRewriter1_12 {

    @Redirect(method = "lambda$register$1", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;newerThanOrEqualTo(Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;)Z"))
    private static boolean dontClearRecipes(ProtocolVersion instance, ProtocolVersion other) {
        return false;
    }

}
