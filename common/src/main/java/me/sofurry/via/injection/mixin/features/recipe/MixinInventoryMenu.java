package me.sofurry.via.injection.mixin.features.recipe;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.recipe.Recipes1_11_2;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class MixinInventoryMenu extends AbstractCraftingMenu {

    public MixinInventoryMenu(MenuType<?> type, int syncId, int width, int height) {
        super(type, syncId, width, height);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"))
    private void clientSideCrafting(Container container, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
            Recipes1_11_2.setCraftingResultSlot(containerId, this, this.craftSlots);
        }
    }

}
