package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity_FeaturesEntityInteraction {

    @Shadow
    public abstract boolean isAlive();

    @Redirect(method = "interact", at = @At(value = "FIELD", target = "Lnet/minecraft/world/InteractionResult;CONSUME:Lnet/minecraft/world/InteractionResult$Success;", opcode = Opcodes.GETSTATIC))
    private InteractionResult.Success swingHand() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_9)) {
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void removeLeashActions(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        // Removes shearing of equipment & snipping all leashes + condition changes
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            final ItemStack itemStack = player.getItemInHand(hand);
            if (this.isAlive() && this instanceof final Leashable leashable) {
                if (leashable.getLeashHolder() != player) {
                    if (itemStack.is(Items.LEAD) && leashable.canHaveALeashAttachedTo(player)) {
                        itemStack.shrink(1);
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        return;
                    }
                } else {
                    cir.setReturnValue(InteractionResult.SUCCESS.withoutItem());
                    return;
                }
            }

            cir.setReturnValue(InteractionResult.PASS);
        }
    }

}
