package me.sofurry.via.injection.mixin.features.swinging;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public LocalPlayer player;

    @WrapWithCondition(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private boolean disableSwing(LocalPlayer instance, InteractionHand hand) {
        return ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_15);
    }

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult$Success;swingSource()Lnet/minecraft/world/InteractionResult$SwingSource;", ordinal = 0))
    private InteractionResult.SwingSource disableSwing(InteractionResult.Success instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return InteractionResult.SwingSource.NONE;
        } else {
            return instance.swingSource();
        }
    }

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult$Success;swingSource()Lnet/minecraft/world/InteractionResult$SwingSource;", ordinal = 2))
    private InteractionResult.SwingSource disableSwing2(InteractionResult.Success instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return InteractionResult.SwingSource.NONE;
        } else {
            return instance.swingSource();
        }
    }

    @Inject(method = "startAttack", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private void fixSwingPacketOrder(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            this.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @WrapWithCondition(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private boolean fixSwingPacketOrder(LocalPlayer instance, InteractionHand hand) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_8);
    }

}
