package me.sofurry.via.injection.mixin.features.movement.liquid;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    @Shadow
    public ClientInput input;

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
    private boolean easierUnderwaterSprinting(ClientInput instance, Operation<Boolean> original) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return this.viaVersion$isWalking1_21_4();
        } else {
            return original.call(instance);
        }
    }

    @Redirect(method = "aiStep",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSwimming()Z", ordinal = 0))
    private boolean dontAllowSneakingWhileSwimming(LocalPlayer instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_14_1) && instance.isSwimming();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInWater()Z"))
    private boolean disableWaterRelatedMovement(LocalPlayer self) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_12_2) && self.isInWater();
    }

    @Unique
    private boolean viaVersion$isWalking1_21_4() {
        final boolean submergedInWater = ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_14_1) && isUnderWater();
        return submergedInWater ? this.input.hasForwardImpulse() : this.input.moveVector.y >= 0.8;
    }

}
