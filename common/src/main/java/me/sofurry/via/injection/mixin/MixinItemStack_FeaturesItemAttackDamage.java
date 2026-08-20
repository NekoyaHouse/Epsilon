package me.sofurry.via.injection.mixin;

import me.sofurry.via.injection.access.item.attack_damage.IDisplayDefault;
import me.sofurry.via.platform.ViaVersionPlatform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class MixinItemStack_FeaturesItemAttackDamage {

    @Inject(method = "addAttributeTooltips", at = @At("HEAD"))
    private void captureItemEnchantments(Consumer<Component> consumer, TooltipDisplay display, Player player, CallbackInfo ci) {
        if (ViaVersionPlatform.isModLoaded("legendarytooltips")) {
            return;
        }

        final ItemStack itemStack = (ItemStack) (Object) this;
        final IDisplayDefault mixinDefault = (IDisplayDefault) ItemAttributeModifiers.Display.attributeModifiers();
        mixinDefault.viaVersion$setItemEnchantments(EnchantmentHelper.getEnchantmentsForCrafting(itemStack));
    }

}
