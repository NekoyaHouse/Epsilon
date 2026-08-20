package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomCow.class)
public abstract class MixinMushroomCow_FeaturesEntityInteraction extends Animal {

    protected MixinMushroomCow_FeaturesEntityInteraction(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    @Redirect(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/cow/MushroomCow;isBaby()Z", ordinal = 0))
    private boolean allowBabyVariant(MushroomCow instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_11) && instance.isBaby();
    }

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/cow/MushroomCow;getEffectsFromItemStack(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;"), cancellable = true)
    private void checkForItemTags(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            final ItemStack itemStack = player.getItemInHand(hand);
            if (!itemStack.is(BlockItemTags.SMALL_FLOWERS.item())) {
                cir.setReturnValue(super.mobInteract(player, hand));
            }
        }
    }

    @Redirect(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/cow/AbstractCow;mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", ordinal = 0))
    private InteractionResult directPass(AbstractCow instance, Player player, InteractionHand interactionHand) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            return InteractionResult.PASS;
        } else {
            return super.mobInteract(player, interactionHand);
        }
    }

}
