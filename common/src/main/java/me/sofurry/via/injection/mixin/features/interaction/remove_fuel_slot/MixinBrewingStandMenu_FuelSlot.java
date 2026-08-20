package me.sofurry.via.injection.mixin.features.interaction.remove_fuel_slot;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$FuelSlot")
public abstract class MixinBrewingStandMenu_FuelSlot extends Slot {

    public MixinBrewingStandMenu_FuelSlot(Container inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Inject(method = "mayPlaceItem(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private static void removeFuelSlot(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public boolean isActive() {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_8);
    }

}
