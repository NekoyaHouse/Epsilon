package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private GuiMessageTag removeIndicator(GuiMessageTag tag) {
        return ClientSetting.INSTANCE.hideSignatureIndicator.isEnabled() ? null : tag;
    }

}
