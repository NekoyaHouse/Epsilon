package me.sofurry.via.injection.mixin.features.scoreboard;

import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import com.viaversion.viaversion.libs.mcstructs.text.stringformat.StringFormat;
import com.viaversion.viaversion.libs.mcstructs.text.stringformat.handling.ColorHandling;
import com.viaversion.viaversion.libs.mcstructs.text.stringformat.handling.DeserializerUnknownHandling;
import com.viaversion.viaversion.util.ComponentUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ComponentUtil.class, remap = false)
public abstract class MixinComponentUtil {

    @Redirect(method = {"legacyToJson", "legacyToJsonString(Ljava/lang/String;Z)Ljava/lang/String;"}, at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/libs/mcstructs/text/stringformat/StringFormat;fromString(Ljava/lang/String;Lcom/viaversion/viaversion/libs/mcstructs/text/stringformat/handling/ColorHandling;Lcom/viaversion/viaversion/libs/mcstructs/text/stringformat/handling/DeserializerUnknownHandling;)Lcom/viaversion/viaversion/libs/mcstructs/text/TextComponent;"))
    private static TextComponent dontSkipEmptySections(StringFormat instance, String s, ColorHandling colorHandling, DeserializerUnknownHandling unknownHandling) {
        return instance.fromString(s, colorHandling, unknownHandling, false);
    }

}
