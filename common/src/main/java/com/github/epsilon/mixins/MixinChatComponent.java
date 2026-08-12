package com.github.epsilon.mixins;

import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.modules.impl.render.BetterChat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"
})
public class MixinChatComponent {

    @ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence onHandleMessage(FormattedCharSequence message) {
        return NotificationManager.INSTANCE.applyAnimatedPrefix(message);
    }

    @ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float animateMessageOpacity(float opacity) {
        return opacity * BetterChat.INSTANCE.getChatContentAlpha();
    }

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void hookFill(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (BetterChat.INSTANCE.isEnabled() && (color & 0x00FFFFFF) == 0) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "handleTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void hideTagIndicator(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int col, Operation<Void> original) {
        if (!BetterChat.INSTANCE.isEnabled()) {
            original.call(graphics, x0, y0, x1, y1, col);
        }
    }

}
