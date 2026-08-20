package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mth.class)
public abstract class MixinMth {

    @Shadow
    @Final
    private static float[] SIN;

    @Inject(method = "sin", at = @At("HEAD"), cancellable = true)
    private static void changeSinScaling(double i, CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_9)) {
            final float index = (float) i; // Convert back as float; the game still passes the same types
            cir.setReturnValue(SIN[(int) (index * 10430.378F) & 65535]);
        }
    }

    @Inject(method = "cos", at = @At("HEAD"), cancellable = true)
    private static void changeCosScaling(double i, CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_9)) {
            final float index = (float) i;
            cir.setReturnValue(SIN[(int) (index * 10430.378F + 16384.0F) & 65535]);
        }
    }

}
