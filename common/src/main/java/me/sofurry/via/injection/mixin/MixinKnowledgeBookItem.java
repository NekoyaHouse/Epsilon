package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.KnowledgeBookItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KnowledgeBookItem.class)
public abstract class MixinKnowledgeBookItem {

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void removeFullStack(ItemStack instance, int amount, LivingEntity owner, @Local(argsOnly = true) InteractionHand hand) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            if (!owner.hasInfiniteMaterials()) {
                owner.setItemInHand(hand, ItemStack.EMPTY);
            }
        } else {
            instance.consume(amount, owner);
        }
    }

}
