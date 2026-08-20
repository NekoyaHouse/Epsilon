package me.sofurry.via.injection.mixin.features.item.attack_damage;

import me.sofurry.via.injection.access.item.attack_damage.IDisplayDefault;
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
public abstract class MixinItemStack {

    @Inject(method = "addAttributeTooltips", at = @At("HEAD"))
    private void captureItemEnchantments(Consumer<Component> consumer, TooltipDisplay display, Player player, CallbackInfo ci) {
        final ItemStack itemStack = (ItemStack) (Object) this;
        final IDisplayDefault mixinDefault = (IDisplayDefault) ItemAttributeModifiers.Display.attributeModifiers();
        mixinDefault.viaVersion$setItemEnchantments(EnchantmentHelper.getEnchantmentsForCrafting(itemStack));
    }

}
