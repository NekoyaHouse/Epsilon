package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.libs.gson.Gson;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import com.viaversion.viaversion.libs.mcstructs.text.serializer.TextComponentSerializer;
import com.viaversion.viaversion.libs.mcstructs.text.utils.LegacyGson;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = TextComponentSerializer.class, remap = false)
public abstract class MixinTextComponentSerializer {

    @Shadow
    @Final
    private boolean legacyGson;

    @Shadow
    public abstract Gson getGson();

    /**
     * @author Florian Reuth (EnZaXD)
     * @reason Fix legacy text component deserialization
     */
    @Overwrite
    public TextComponent deserialize(String json) {
        if (this.legacyGson) {
            LegacyGson.checkStartingType(json, true);
            json = LegacyGson.fixInvalidEscapes(json);
        }
        return this.getGson().fromJson(json, TextComponent.class);
    }

}
