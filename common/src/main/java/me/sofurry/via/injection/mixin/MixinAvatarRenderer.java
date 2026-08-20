package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void modifySleepingOffset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        if (state.pose == Pose.SLEEPING) {
            final Direction sleepingDir = state.bedOrientation;
            if (sleepingDir != null) {
                if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
                    cir.setReturnValue(cir.getReturnValue().subtract(sleepingDir.getStepX() * 0.4, 0, sleepingDir.getStepZ() * 0.4));
                }
                if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_5tob1_5_2)) {
                    cir.setReturnValue(cir.getReturnValue().subtract(sleepingDir.getStepX() * 0.1, 0, sleepingDir.getStepZ() * 0.1));
                }
                if (ProtocolTranslator.getTargetVersion().betweenInclusive(LegacyProtocolVersion.b1_6tob1_6_6, ProtocolVersion.v1_7_6)) {
                    cir.setReturnValue(cir.getReturnValue().subtract(0, 0.3F, 0));
                }
            }
        }
    }

    @Redirect(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isCrouching:Z", opcode = Opcodes.GETFIELD))
    private boolean disableSneakPositionOffset(AvatarRenderState instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_11_1) && instance.isCrouching;
    }

}
