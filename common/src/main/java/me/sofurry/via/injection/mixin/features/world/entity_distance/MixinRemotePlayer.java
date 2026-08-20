package me.sofurry.via.injection.mixin.features.world.entity_distance;

import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RemotePlayer.class)
public abstract class MixinRemotePlayer extends AbstractClientPlayer {

    public MixinRemotePlayer(final ClientLevel level, final GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void revert10thMultiplication(final double distance, final CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(super.shouldRenderAtSqrDistance(distance));
        }
    }

}
