package me.sofurry.via.injection.mixin.features.item.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public abstract class MixinBoneMealItem {

    @Inject(method = "useOn", at = @At(value = "RETURN", ordinal = 1), cancellable = true)
    private void dontSwingHand(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().equalTo(ProtocolVersion.v26_1)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

}
