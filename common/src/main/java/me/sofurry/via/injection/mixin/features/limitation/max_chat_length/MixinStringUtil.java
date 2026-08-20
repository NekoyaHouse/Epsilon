package me.sofurry.via.injection.mixin.features.limitation.max_chat_length;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.sofurry.via.features.limitation.max_chat_length.MaxChatLength;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StringUtil.class)
public abstract class MixinStringUtil {

    @ModifyExpressionValue(method = "trimChatMessage", at = @At(value = "CONSTANT", args = "intValue=256"))
    private static int modifyMaxChatLength(int constant) {
        return MaxChatLength.getChatLength();
    }

}
