package me.sofurry.via.injection.mixin.features.item.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireworkRocketItem.class)
public abstract class MixinFireworkRocketItem {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void allowWhileGliding(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

}
