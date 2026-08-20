package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_FeaturesInteraction {

    @Group(name = "replaceItemStackEqualsCheck", min = 1, max = 1)
    @Redirect(method = "updatingUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"), require = 0)
    private boolean replaceVanillaItemStackEqualsCheck(ItemStack a, ItemStack b) {
        return viaVersion$canContinueUsing(a, b);
    }

    @Group(name = "replaceItemStackEqualsCheck", min = 1, max = 1)
    @Redirect(method = "updatingUsingItem", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;canContinueUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"), require = 0)
    private boolean replaceNeoForgeItemStackEqualsCheck(ItemStack a, ItemStack b) {
        return viaVersion$canContinueUsing(a, b);
    }

    private static boolean viaVersion$canContinueUsing(ItemStack a, ItemStack b) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_3)) {
            return a == b;
        } else {
            return ItemStack.isSameItem(a, b);
        }
    }

    @Inject(method = "getEquipmentSlotForItem", at = @At("HEAD"), cancellable = true)
    private void removeShieldSlotPreference(ItemStack itemStack, CallbackInfoReturnable<EquipmentSlot> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_9_3) && itemStack.is(Items.SHIELD)) {
            cir.setReturnValue(EquipmentSlot.MAINHAND);
        }
    }

}
