package me.sofurry.via.injection.mixin.features.world.remove_server_view_distance;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Options.class)
public abstract class MixinOptions {

    @ModifyVariable(method = "setServerRenderDistance", at = @At("HEAD"), argsOnly = true)
    private int changeServerViewDistance(int serverRenderDistance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_17_1)) {
            return 0;
        } else {
            return serverRenderDistance;
        }
    }

}
