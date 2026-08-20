package me.sofurry.via.injection.mixin.features.movement.jump;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;jumpFromGround()V"))
    private boolean dontJumpBeforeFlying(LocalPlayer instance) {
        return ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_20_5);
    }

    @Redirect(method = "updateAutoJump", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;invSqrt(F)F"))
    private float useFastInverseSqrt(float x) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            x = Float.intBitsToFloat(1597463007 - (Float.floatToIntBits(x) >> 1));
            return x * (1.5F - (0.5F * x) * x * x);
        } else {
            return Mth.invSqrt(x);
        }
    }

}
