package me.sofurry.via.injection.mixin.features.networking.limitation.nbt;

import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.viaversion.api.type.types.misc.TagType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = TagType.class, remap = false)
public abstract class MixinTagType {

    @Redirect(method = "read(Lio/netty/buffer/ByteBuf;)Lcom/viaversion/nbt/tag/Tag;", at = @At(value = "INVOKE", target = "Lcom/viaversion/nbt/limiter/TagLimiter;create(II)Lcom/viaversion/nbt/limiter/TagLimiter;"))
    private TagLimiter removeNBTSizeLimit(int maxBytes, int maxLevels) {
        return TagLimiter.noop();
    }

}
