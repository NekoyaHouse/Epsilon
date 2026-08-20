package me.sofurry.via.injection.mixin;

import me.sofurry.via.features.limitation.max_chat_length.MaxChatLength;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.viaversion.viaversion.protocols.v1_10to1_11.Protocol1_10To1_11$6", remap = false)
public abstract class MixinProtocol1_10To1_11 {

    @ModifyConstant(method = "lambda$register$0", constant = @Constant(intValue = 100))
    private static int changeMaxChatLength(int constant) {
        return MaxChatLength.getChatLength();
    }

}
