package me.sofurry.via.visuals.injection.mixin.classic.creative_menu;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.visuals.features.classic.creative_menu.GridItemSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class MixinCreativeModeInventoryScreen {

    @Inject(method = "init", at = @At("RETURN"))
    private void replaceCreativeMenu(CallbackInfo ci) {
        if (ClientSetting.INSTANCE.replaceCreativeInventory.isEnabled()) {
            Minecraft.getInstance().gui.setScreen(GridItemSelectionScreen.INSTANCE);
        }
    }

}
