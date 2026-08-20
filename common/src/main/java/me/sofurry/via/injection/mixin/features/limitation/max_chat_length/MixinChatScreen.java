package me.sofurry.via.injection.mixin.features.limitation.max_chat_length;

import me.sofurry.via.features.limitation.max_chat_length.MaxChatLength;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatScreen.class, priority = 1) // Apply our mixin first so other mods can override the chat length
public abstract class MixinChatScreen {

    @Shadow
    protected EditBox input;

    @Inject(method = "init", at = @At("RETURN"))
    private void changeChatLength(CallbackInfo ci) {
        if (this.input.getMaxLength() == MaxChatLength.MAX_CHAT_LENGTH_LATEST) {
            this.input.setMaxLength(MaxChatLength.getChatLength());
        }
    }

}
