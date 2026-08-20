package me.sofurry.via.injection.mixin.features.entity.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MixinMob {

    @Shadow
    protected abstract InteractionResult checkAndHandleImportantInteractions(Player player, InteractionHand hand);

    @Redirect(method = "interact", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;checkAndHandleImportantInteractions(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult moveItemInteractionsAfterLeashing(Mob instance, Player player, InteractionHand hand) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            return InteractionResult.FAIL;
        } else {
            return checkAndHandleImportantInteractions(player, hand);
        }
    }

    @Inject(method = "interact", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), cancellable = true)
    private void moveItemInteractionsAfterLeashing(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            final InteractionResult result = checkAndHandleImportantInteractions(player, hand);
            if (result.consumesAction()) {
                cir.setReturnValue(result);
            }
        }
    }

}
