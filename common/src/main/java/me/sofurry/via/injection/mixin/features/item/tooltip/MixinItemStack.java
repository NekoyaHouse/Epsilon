package me.sofurry.via.injection.mixin.features.item.tooltip;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import me.sofurry.via.features.item.r1_14_4_enchantment_tooltip.Enchantments1_14_4;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ItemUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @Shadow
    public abstract Item getItem();

    @WrapWithCondition(method = "addDetailsToTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;appendHoverText(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V"))
    private boolean hideAdditionalTooltip(Item instance, ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            final CompoundTag tag = ItemUtil.getTagOrNull((ItemStack) (Object) this);
            final CompoundTag backup = tag == null ? null : tag.getCompoundOrEmpty(ItemUtil.vvNbtName(Protocol1_21_4To1_21_5.class, "backup"));
            return backup == null || !backup.contains("hide_additional_tooltip");
        } else {
            return true;
        }
    }

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private <T extends TooltipProvider> void replaceEnchantmentTooltip(DataComponentType<T> type, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> consumer, TooltipFlag flag, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_14_4)) {
            return;
        }

        final CompoundTag tag = ItemUtil.getTagOrNull((ItemStack) (Object) this);
        if (tag == null) {
            return;
        }
        if (type == DataComponents.ENCHANTMENTS) {
            this.viaVersion$appendEnchantments1_14_4("Enchantments", tag, context, consumer);
            ci.cancel();
        } else if (type == DataComponents.STORED_ENCHANTMENTS) {
            this.viaVersion$appendEnchantments1_14_4("StoredEnchantments", tag, context, consumer);
            ci.cancel();
        }
    }

    @Unique
    private void viaVersion$appendEnchantments1_14_4(final String name, final CompoundTag nbt, Item.TooltipContext context, final Consumer<Component> tooltip) {
        final HolderLookup.Provider registryLookup = context.registries();
        final ListTag enchantments = nbt.getList(name).orElse(null);
        if (enchantments == null) {
            return;
        }

        for (Tag element : enchantments) {
            final CompoundTag enchantment = (CompoundTag) element;

            final String id = enchantment.getStringOr("id", "");
            final Optional<ResourceKey<Enchantment>> value = Enchantments1_14_4.getOrEmpty(id);
            value.ifPresent(e -> {
                final int lvl = enchantment.getIntOr("lvl", 0);
                if (registryLookup != null) {
                    final Optional<Holder.Reference<Enchantment>> v = registryLookup.lookupOrThrow(Registries.ENCHANTMENT).get(e);
                    v.ifPresent(enchantmentReference -> tooltip.accept(Enchantment.getFullname(enchantmentReference, Mth.clamp(lvl, Short.MIN_VALUE, Short.MAX_VALUE))));
                }
            });
        }
    }

}
