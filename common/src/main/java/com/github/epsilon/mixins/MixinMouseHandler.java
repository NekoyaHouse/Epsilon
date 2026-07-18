package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.events.impl.MouseScrollEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        MousePressEvent event = EventBus.INSTANCE.post(new MousePressEvent(rawButtonInfo.button(), action, rawButtonInfo.modifiers()));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        MouseScrollEvent event = EventBus.INSTANCE.post(new MouseScrollEvent(yoffset));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}
