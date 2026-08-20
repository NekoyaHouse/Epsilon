package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AxeItem.class)
public abstract class MixinAxeItem {

    @Shadow
    private static boolean playerHasBlockingItemUseIntent(UseOnContext context) {
        return false;
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/AxeItem;playerHasBlockingItemUseIntent(Lnet/minecraft/world/item/context/UseOnContext;)Z"))
    private boolean neverCancelStripAttempt(UseOnContext context) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_20_5) && playerHasBlockingItemUseIntent(context);
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void disableUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

}
