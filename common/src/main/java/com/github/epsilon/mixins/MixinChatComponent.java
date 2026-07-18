package com.github.epsilon.mixins;

import com.github.epsilon.managers.Managers;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"
})
public class MixinChatComponent {

    @ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence sakura$animateClientPrefix(FormattedCharSequence message) {
        return Managers.NOTIFICATION.applyAnimatedPrefix(message);
    }

}
