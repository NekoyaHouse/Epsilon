package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CraftingMenu.class)
public abstract class MixinCraftingMenu_FeaturesInteractionContainerClicking extends AbstractCraftingMenu {

    public MixinCraftingMenu_FeaturesInteractionContainerClicking(MenuType<?> type, int syncId, int width, int height) {
        super(type, syncId, width, height);
    }

    @Redirect(method = "quickMoveStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/CraftingMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", ordinal = 1))
    private boolean noShiftClickMoveIntoCraftingTable(CraftingMenu instance, ItemStack itemStack, int startIndex, int endIndex, boolean fromLast) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_14_4) && this.moveItemStackTo(itemStack, startIndex, endIndex, fromLast);
    }

}
