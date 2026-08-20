package me.sofurry.via.injection.mixin;

import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.viaversion.api.type.types.misc.NamedCompoundTagType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NamedCompoundTagType.class, remap = false)
public abstract class MixinNamedCompoundTagType {

    @Redirect(method = "read(Lio/netty/buffer/ByteBuf;IZ)Lcom/viaversion/nbt/tag/CompoundTag;", at = @At(value = "INVOKE", target = "Lcom/viaversion/nbt/limiter/TagLimiter;create(II)Lcom/viaversion/nbt/limiter/TagLimiter;"))
    private static TagLimiter removeNBTSizeLimit(int maxBytes, int maxLevels) {
        return TagLimiter.noop();
    }

}
