package me.sofurry.via.injection.mixin;

import com.mojang.serialization.DataResult;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HolderSetCodec.class)
public abstract class MixinHolderSetCodec {

    @Inject(method = "lookupTag", at = @At("RETURN"), cancellable = true)
    private static void workaroundValidation(HolderGetter<?> registry, TagKey<?> key, CallbackInfoReturnable<DataResult<HolderSet<?>>> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            // The client now throws if the server references tags, which weren't previously sent via protocol,
            // instead of implementing verification on protocol level, we just skip the error and return an empty registry list
            if (cir.getReturnValue().isError()) {
                cir.setReturnValue(DataResult.success(HolderSet.empty()));
            }
        }
    }

}
