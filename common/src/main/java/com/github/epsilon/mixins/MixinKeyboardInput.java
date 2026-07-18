package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Shadow
    private static float calculateImpulse(boolean positive, boolean negative) {
        return 0.0f;
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input redirectKeyPresses(Input original) {
        float forwardImpulse = calculateImpulse(original.forward(), original.backward());
        float leftImpulse = calculateImpulse(original.left(), original.right());
        KeyboardInputEvent event = EventBus.INSTANCE.post(new KeyboardInputEvent(forwardImpulse, leftImpulse, original.jump(), original.shift(), original.sprint()));
        return event.toNewInput();
    }

}
