package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.player.Disabler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(TridentItem.class)
public class MixinTridentItem {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void allowDryTridentUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Disabler disabler = Disabler.INSTANCE;
        if (player != mc.player || !disabler.isEnabled() || !disabler.getGrimTrident()) return;
        if (player.getItemInHand(hand).nextDamageWillBreak()) {
            cir.setReturnValue(InteractionResult.FAIL);
        } else {
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

}
