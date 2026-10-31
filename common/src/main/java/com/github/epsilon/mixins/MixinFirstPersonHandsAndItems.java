package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.HandView;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
public class MixinFirstPersonHandsAndItems {

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("RETURN"))
    private void hideHotbarSwitchAnimation(LocalPlayer player, CallbackInfo ci) {
        HandView handView = HandView.INSTANCE;
        if (handView.isEnabled()) {
            if (handView.getDisableSwapMain()) {
                this.mainHandHeight = 1.0F;
                this.mainHandItem = player.getMainHandItem();
            }
            if (handView.getDisableSwapOff()) {
                this.offHandHeight = 1.0F;
                this.offHandItem = player.getOffhandItem();
            }
        }
    }

}
