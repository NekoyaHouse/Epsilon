package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.impl.FallFlyingEvent;
import com.github.epsilon.events.impl.FireworkRotationEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public abstract class ElytraFlightMode {

    protected final Minecraft mc = Minecraft.getInstance();
    protected final ElytraFly elytraFly;
    private final TimerUtils unbreakingTimer = new TimerUtils();

    protected ElytraFlightMode(ElytraFly elytraFly) {
        this.elytraFly = elytraFly;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onPlayerTick() {
    }

    public void onTravel(TravelEvent event) {
    }

    public void onKeyboardInput(KeyboardInputEvent event) {
    }

    public void onFallFlying(FallFlyingEvent event) {
    }

    public void onFireworkUpdate(FireworkRotationEvent event) {
    }

    public boolean shouldCancelRightClick() {
        return true;
    }

    public void armUnbreakingTimer() {
        unbreakingTimer.setMs(917813L);
    }

    public void handleUnbreaking() {
        if (!elytraFly.unbreaking.getValue()) return;
        if (mc.screen != null) return;
        if (!mc.player.isFallFlying() || mc.player.onGround()) return;
        if (!unbreakingTimer.passedMillise(elytraFly.unbreakingDelay.getValue())) return;

        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!LivingEntity.canGlideUsing(chestStack, EquipmentSlot.CHEST)) return;

        int containerId = mc.player.containerMenu.containerId;
        ClickSlotUtils.click(containerId, 6);
        ClickSlotUtils.click(containerId, 6);

        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        mc.player.startFallFlying();
        unbreakingTimer.reset();
    }

    protected boolean canStartFallFlying() {
        return !mc.player.isFallFlying() && !mc.player.isInWater();
    }

    protected boolean startFallFlying() {
        if (mc.player.tryToStartFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            return true;
        }
        return false;
    }

    protected void restartFallFlying() {
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        mc.player.startFallFlying();
    }

    protected boolean canGlide(boolean hasElytraInInventory) {
        if (elytraFly.armored.getValue() && hasElytraInInventory) {
            return true;
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (LivingEntity.canGlideUsing(mc.player.getItemBySlot(slot), slot)) {
                return true;
            }
        }
        return false;
    }

    protected boolean useFirework() {
        FindItemResult rocket = elytraFly.swapMode.is(ElytraFly.SwapMode.Silent) ? InvUtils.findInHotbar(Items.FIREWORK_ROCKET) : InvUtils.find(Items.FIREWORK_ROCKET);
        if (!rocket.found()) return false;

        InteractionHand hand = rocket.getHand();

        if (elytraFly.swapMode.is(ElytraFly.SwapMode.Silent)) {
            InvUtils.swap(rocket.slot(), true);
        } else {
            InvUtils.invSwap(rocket.slot());
        }

        InteractionResult result = mc.gameMode.useItem(mc.player, hand);
        if (result.consumesAction()) {
            mc.player.swing(hand);
        }

        if (elytraFly.swapMode.is(ElytraFly.SwapMode.Silent)) {
            InvUtils.swapBack();
        } else {
            InvUtils.invSwapBack();
        }

        return result.consumesAction();
    }

    protected void swapArmor(int containerSlot) {
        int containerId = mc.player.containerMenu.containerId;
        ClickSlotUtils.click(containerId, containerSlot);
        ClickSlotUtils.click(containerId, 6);
        ClickSlotUtils.click(containerId, containerSlot);
    }

}
