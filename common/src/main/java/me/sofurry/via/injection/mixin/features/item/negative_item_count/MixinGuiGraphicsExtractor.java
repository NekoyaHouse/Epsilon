package me.sofurry.via.injection.mixin.features.item.negative_item_count;

import me.sofurry.via.features.item.negative_item_count.NegativeItemUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor {

    @Redirect(method = "itemCount", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCount()I"))
    private int handleNegativeItemCount(ItemStack instance) {
        return NegativeItemUtil.getCount(instance);
    }

    @Redirect(method = "itemCount", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;", remap = false))
    private String makeTextRed(int i) {
        if (i <= 0) {
            return ChatFormatting.RED.toString() + i;
        } else {
            return String.valueOf(i);
        }
    }

}
