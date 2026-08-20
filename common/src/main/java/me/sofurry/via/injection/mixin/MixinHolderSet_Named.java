package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HolderSet.Named.class)
public abstract class MixinHolderSet_Named {

    @Shadow
    private List<Holder> contents;

    @Shadow
    abstract void bind(List<Holder> contents);

    @Inject(method = "contents", at = @At("HEAD"))
    private void preventNullableEntries(CallbackInfoReturnable<List<Holder<?>>> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            // Previously didn't have unbounded entries
            if (this.contents == null) {
                this.bind(List.of());
            }
        }
    }

    @Inject(method = "isBound", at = @At("HEAD"), cancellable = true)
    private void alwaysBound(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            // Previously didn't exist
            cir.setReturnValue(true);
        }
    }

}
