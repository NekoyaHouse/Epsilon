package me.sofurry.via.injection.mixin;

import me.sofurry.via.features.item.negative_item_count.NegativeItemUtil;
import me.sofurry.via.platform.ViaVersionPlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor {

    @Shadow
    public abstract void text(Font font, String text, int x, int y, int color, boolean shadow);

    @Inject(method = "itemCount", at = @At("HEAD"), cancellable = true)
    private void renderNegativeItemCount(Font font, ItemStack stack, int x, int y, String countString, CallbackInfo ci) {
        if (ViaVersionPlatform.isModLoaded("legacy") || countString != null) {
            return;
        }

        final int count = NegativeItemUtil.getCount(stack);
        if (count > 0) {
            return;
        }

        final String text = ChatFormatting.RED.toString() + count;
        this.text(font, text, x + 17 - font.width(text), y + 9, -1, true);
        ci.cancel();
    }

}
