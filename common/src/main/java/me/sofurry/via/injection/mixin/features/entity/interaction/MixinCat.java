package me.sofurry.via.injection.mixin.features.entity.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Cat.class)
public abstract class MixinCat extends TamableAnimal {

    protected MixinCat(EntityType<? extends TamableAnimal> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void interactMob1_20_4(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
            final ItemStack itemStack = player.getItemInHand(hand);
            if (this.isTame() && this.isOwnedBy(player)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            } else {
                cir.setReturnValue(!this.isFood(itemStack) || !(this.getHealth() < this.getMaxHealth()) && this.isTame() ? InteractionResult.PASS : InteractionResult.SUCCESS);
            }
        }
    }

}
