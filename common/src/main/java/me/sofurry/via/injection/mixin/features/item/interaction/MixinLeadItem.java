package me.sofurry.via.injection.mixin.features.item.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeadItem.class)
public abstract class MixinLeadItem {

    @Inject(method = "useOn", at = @At("RETURN"), cancellable = true)
    private void swingHand(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            if (context.getLevel().getBlockState(context.getClickedPos()).is(BlockTags.FENCES)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

}
