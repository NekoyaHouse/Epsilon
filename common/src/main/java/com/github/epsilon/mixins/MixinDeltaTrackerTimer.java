package com.github.epsilon.mixins;

import com.github.epsilon.managers.Managers;
import net.minecraft.client.DeltaTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public class MixinDeltaTrackerTimer {

    @Shadow
    private float deltaTicks;

    @Inject(method = "advanceGameTime(J)I", at = @At(value = "FIELD", target = "Lnet/minecraft/client/DeltaTracker$Timer;lastMs:J", opcode = Opcodes.PUTFIELD))
    public void onBeginRenderTick(long currentMs, CallbackInfoReturnable<Integer> cir) {
        deltaTicks *= Managers.TIMER.get();
    }

}
