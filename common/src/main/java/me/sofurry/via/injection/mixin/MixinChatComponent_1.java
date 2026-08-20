package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class MixinChatComponent_1 {

    @ModifyExpressionValue(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;tag()Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"))
    private GuiMessageTag removeIndicator(GuiMessageTag original) {
        if (ClientSetting.INSTANCE.hideSignatureIndicator.isEnabled()) {
            return null;
        } else {
            return original;
        }
    }

}
