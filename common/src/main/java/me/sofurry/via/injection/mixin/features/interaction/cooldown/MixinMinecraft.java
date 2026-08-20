package me.sofurry.via.injection.mixin.features.interaction.cooldown;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    protected int missTime;

    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;missTime:I", ordinal = 1, opcode = Opcodes.GETFIELD))
    private int moveCooldownIncrement(Minecraft instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return 0;
        } else {
            return missTime;
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"))
    private void moveCooldownIncrement(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            if (this.missTime > 0) {
                --this.missTime;
            }
        }
    }

    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasMissTime()Z", ordinal = 1))
    private boolean removeHitPenalty(MultiPlayerGameMode instance, Operation<Boolean> original) {
        // In <=1.7 this code is not inside the MISS case but in the BLOCK case, causing it to never be called
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_7_6) && original.call(instance);
    }

}
