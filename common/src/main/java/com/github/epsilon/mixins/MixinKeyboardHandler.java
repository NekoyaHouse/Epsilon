package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.KeyPressEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void keyPress(long handle, int action, KeyEvent keyEvent, CallbackInfo ci) {
        KeyPressEvent event = EventBus.INSTANCE.post(new KeyPressEvent(keyEvent, action));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}
