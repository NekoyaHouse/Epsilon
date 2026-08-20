package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.interaction.container_clicking.IAbstractContainerMenu;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu implements IAbstractContainerMenu {

    @Shadow
    private ItemStack carried;

    @Unique
    private short viaVersion$actionId;

    @Redirect(method = "initializeContents", at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;carried:Lnet/minecraft/world/item/ItemStack;", opcode = Opcodes.PUTFIELD))
    private void preventUpdate(AbstractContainerMenu instance, ItemStack value) {
        if (ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_17_1)) {
            this.carried = value;
        }
    }

    @Override
    public short viaVersion$getActionId() {
        return viaVersion$actionId;
    }

    @Override
    public short viaVersion$incrementAndGetActionId() {
        return ++viaVersion$actionId;
    }

}
