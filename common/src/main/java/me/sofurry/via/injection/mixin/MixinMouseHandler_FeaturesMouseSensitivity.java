package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.mouse_sensitivity.MouseSensitivity1_13_2;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler_FeaturesMouseSensitivity {

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object adjustMouseSensitivity1_13_2(OptionInstance<Double> instance, Operation<Double> original) {
        final Double value = original.call(instance);

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return (double) MouseSensitivity1_13_2.get1_13SliderValue(value.floatValue()).keyFloat();
        } else {
            return value;
        }
    }

}
