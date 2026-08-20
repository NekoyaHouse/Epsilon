package me.sofurry.via.injection.mixin.features.movement.slowdown;

import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    @Shadow
    public ClientInput input;

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Redirect(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
    private boolean changeSneakSlowdownCondition(LocalPlayer instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return instance.input.keyPresses.shift();
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return !Minecraft.getInstance().player.isSpectator() && (instance.input.keyPresses.shift() || instance.isMovingSlowly());
        } else {
            return instance.isMovingSlowly();
        }
    }

    @Inject(method = "aiStep()V",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isControlledCamera()Z")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;shift()Z", ordinal = 0))
    private void undoSneakSlowdownForFly(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_9, ProtocolVersion.v1_14_4)) {
            if (this.input.keyPresses.shift()) {
                final float movementSideways = (float) ((double) this.input.moveVector.x / 0.3D);
                final float movementForward = (float) ((double) this.input.moveVector.y / 0.3D);
                this.input.moveVector = new Vec2(movementSideways, movementForward);
            }
        }
    }

}
