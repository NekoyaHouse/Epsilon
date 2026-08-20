package me.sofurry.via.injection.mixin.features.item.filter_creative_tabs;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.sofurry.via.features.item.filter_creative_tabs.VersionedRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.item.CreativeModeTab$ItemDisplayBuilder")
public abstract class MixinCreativeModeTab_ItemDisplayBuilder {

    @Shadow
    @Final
    private CreativeModeTab tab;

    @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;isEnabled(Lnet/minecraft/world/flag/FeatureFlagSet;)Z"))
    private boolean removeUnknownItems(Item instance, FeatureFlagSet featureSet, Operation<Boolean> original, @Local(argsOnly = true) ItemStack stack) {
        final boolean originalValue = original.call(instance, featureSet);
        final int index = ClientSetting.INSTANCE.removeNotAvailableItemsFromCreativeTab.getModeIndex();

        if (index == 2 /* Off */ || Minecraft.getInstance().isLocalServer()) {
            return originalValue;
        } else if (index == 1 /* Vanilla only */ && !BuiltInRegistries.CREATIVE_MODE_TAB.getKey(this.tab).getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return originalValue;
        } else {
            return VersionedRegistries.keepItem(stack) && originalValue;
        }
    }

}
