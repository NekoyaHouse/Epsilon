package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantMenu.class)
public abstract class MixinMerchantMenu extends AbstractContainerMenu {

    @Shadow
    @Final
    private MerchantContainer tradeContainer;

    @Shadow
    public abstract MerchantOffers getOffers();

    protected MixinMerchantMenu(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private void onSwitchTo(int newTradeIndex, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            ci.cancel();

            if (newTradeIndex >= this.getOffers().size()) {
                return;
            }

            final MultiPlayerGameMode interactionManager = Minecraft.getInstance().gameMode;
            final LocalPlayer player = Minecraft.getInstance().player;

            // move 1st input slot to inventory
            if (!this.tradeContainer.getItem(0).isEmpty()) {
                final int count = this.tradeContainer.getItem(0).getCount();
                interactionManager.handleContainerInput(containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
                if (count == this.tradeContainer.getItem(0).getCount()) {
                    return;
                }
            }

            // move 2nd input slot to inventory
            if (!this.tradeContainer.getItem(1).isEmpty()) {
                final int count = this.tradeContainer.getItem(1).getCount();
                interactionManager.handleContainerInput(containerId, 1, 0, ContainerInput.QUICK_MOVE, player);
                if (count == this.tradeContainer.getItem(1).getCount()) {
                    return;
                }
            }

            // refill the slots
            if (this.tradeContainer.getItem(0).isEmpty() && this.tradeContainer.getItem(1).isEmpty()) {
                final MerchantOffer tradeOffer = this.getOffers().get(newTradeIndex);
                this.viaVersion$autofill(interactionManager, player, 0, tradeOffer.getItemCostA());
                tradeOffer.getItemCostB().ifPresent(item -> this.viaVersion$autofill(interactionManager, player, 1, item));
            }
        }
    }

    @Inject(method = "canTakeItemForPickAll", at = @At("HEAD"), cancellable = true)
    private void modifyCanInsertIntoSlot(ItemStack carried, Slot target, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void viaVersion$autofill(MultiPlayerGameMode interactionManager, LocalPlayer player, int inputSlot, ItemCost stackNeeded) {
        int slot;
        for (slot = 3; slot < 39; slot++) {
            final ItemStack itemStack = this.slots.get(slot).getItem();
            if (!itemStack.isEmpty() && stackNeeded.test(itemStack)) {
                final ItemStack itemStack2 = this.tradeContainer.getItem(inputSlot);
                if (itemStack2.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemStack2)) {
                    break;
                }
            }
        }
        if (slot == 39) {
            return;
        }

        final boolean wasHoldingItem = !player.containerMenu.getCarried().isEmpty();
        interactionManager.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
        interactionManager.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP_ALL, player);
        interactionManager.handleContainerInput(containerId, inputSlot, 0, ContainerInput.PICKUP, player);
        if (wasHoldingItem) {
            interactionManager.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
        }
    }

}
