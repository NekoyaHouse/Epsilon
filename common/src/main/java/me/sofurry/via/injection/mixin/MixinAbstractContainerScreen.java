package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen extends Screen {

    @Shadow
    public abstract boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick);

    protected MixinAbstractContainerScreen(final Component component) {
        super(component);
    }

    @Redirect(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonEvent;hasShiftDown()Z"))
    private boolean disableShiftClickItems(final MouseButtonEvent instance) {
        return instance.hasShiftDown() && ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_6_1);
    }

    // TODO: Item is supposed to go in slot on click not where mouse is released
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void disableItemDragging(final MouseButtonEvent event, final double dx, final double dy, final CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThan(LegacyProtocolVersion.r1_5tor1_5_1)) {
            cir.setReturnValue(super.mouseDragged(event, dx, dy));
        }
    }

    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", ordinal = 0))
    private boolean disableItemCloning(AbstractContainerScreen<?> instance, Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
        return ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_4_2);
    }

    @Group(name = "disableHotbarKeys", min = 1, max = 1)
    @Redirect(method = "checkHotbarKeyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z", ordinal = 1), require = 0)
    private boolean disableVanillaHotbarKeys(final KeyMapping instance, final KeyEvent event) {
        return instance.matches(event) && ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_4_2);
    }

    @Group(name = "disableHotbarKeys", min = 1, max = 1)
    @WrapOperation(method = "checkHotbarKeyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isActiveAndMatches(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z", ordinal = 1), require = 0)
    private boolean disableNeoForgeHotbarKeys(final KeyMapping instance, final InputConstants.Key key, final Operation<Boolean> original) {
        return ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_4_2) && original.call(instance, key);
    }

}
