package me.sofurry.via.injection.mixin.features.interaction.remove_offhand_slot;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(InventoryMenu.class)
public abstract class MixinInventoryMenu extends AbstractCraftingMenu {

    public MixinInventoryMenu(MenuType<?> type, int syncId, int width, int height) {
        super(type, syncId, width, height);
    }

    @Redirect(method = "<init>",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/InventoryMenu$1;<init>(Lnet/minecraft/world/inventory/InventoryMenu;Lnet/minecraft/world/Container;IIILnet/minecraft/world/entity/player/Player;)V")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/InventoryMenu;addSlot(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;", ordinal = 0))
    private Slot removeOffhandSlot(InventoryMenu screenHandler, Slot slot) {
        return ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8) ? null : addSlot(slot);
    }

}
