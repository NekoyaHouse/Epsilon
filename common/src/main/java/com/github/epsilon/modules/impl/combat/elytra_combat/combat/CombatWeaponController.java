package com.github.epsilon.modules.impl.combat.elytra_combat.combat;

import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.core.component.DataComponents;

import java.util.Set;
import java.util.function.Predicate;

/**
 * ElytraCombat 独立武器控制器，不依赖 AutoWeapon、MaceAura 或 SpearKill。
 */
public final class CombatWeaponController {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Set<Item> SPEARS = Set.of(
            Items.WOODEN_SPEAR,
            Items.STONE_SPEAR,
            Items.COPPER_SPEAR,
            Items.IRON_SPEAR,
            Items.GOLDEN_SPEAR,
            Items.DIAMOND_SPEAR,
            Items.NETHERITE_SPEAR
    );
    private static int spearSavedHotbarSlot = -1;
    private static boolean spearInventorySwapped;

    private CombatWeaponController() {
    }

    public static boolean isSpearItem(ItemStack stack) {
        return !stack.isEmpty() && SPEARS.contains(stack.getItem());
    }

    public static boolean isUsingSpear(LivingEntity entity) {
        return entity != null && entity.isUsingItem() && isSpearItem(entity.getUseItem());
    }

    public static boolean attackMace(
            LivingEntity target,
            boolean antiShield,
            boolean swingHand,
            double reachBuffer
    ) {
        LocalPlayer player = mc.player;
        if (player == null || target == null || !target.isAlive()) {
            return false;
        }
        if (!player.isWithinEntityInteractionRange(target, reachBuffer)) {
            return false;
        }

        boolean shieldSwap = antiShield
                && target instanceof Player targetPlayer
                && targetPlayer.isBlocking()
                && targetPlayer.isUsingItem();
        Selection selection = selectMainHand(
                shieldSwap
                        ? stack -> stack.getItem() instanceof net.minecraft.world.item.AxeItem
                        : stack -> stack.is(Items.MACE)
        );
        if (selection == null) {
            return false;
        }
        try {
            mc.gameMode.attack(player, target);
            if (swingHand) {
                player.swing(InteractionHand.MAIN_HAND);
            }
            return true;
        } finally {
            selection.restore();
        }
    }

    public static boolean ensureSpearUse() {
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        if (isUsingSpear(player)) {
            return true;
        }

        FindItemResult spear = InvUtils.find(CombatWeaponController::isSpearItem);
        if (!spear.found()) {
            return false;
        }

        if (spear.slot() == 40) {
            // 副手长矛无需切换槽位。
        } else if (spear.slot() < 9) {
            if (spear.slot() != player.getInventory().getSelectedSlot()) {
                spearSavedHotbarSlot = player.getInventory().getSelectedSlot();
                InvUtils.swap(spear.slot(), false);
            }
        } else {
            InvUtils.invSwap(spear.slot());
            spearInventorySwapped = true;
        }
        InteractionResult result = mc.gameMode.useItem(player, spear.getHand());
        // 长矛需要持续蓄力，保留当前手持；行为状态机在 pull-over 时统一调用 stopSpearUse。
        return result.consumesAction() || isUsingSpear(player);
    }

    public static boolean canUseSpearAttack() {
        LocalPlayer player = mc.player;
        if (!isUsingSpear(player)) {
            return false;
        }

        ItemStack stack = player.getUseItem();
        KineticWeapon weapon = stack.get(DataComponents.KINETIC_WEAPON);
        int ticksUsed = player.getTicksUsingItem();
        if (weapon == null) {
            return ticksUsed >= 8;
        }

        int maxDuration = weapon.computeDamageUseDuration();
        return ticksUsed >= weapon.delayTicks() && (maxDuration <= 0 || ticksUsed < maxDuration);
    }

    public static int spearReadyTicks() {
        LocalPlayer player = mc.player;
        if (player == null || !isUsingSpear(player)) {
            return 8;
        }
        KineticWeapon weapon = player.getUseItem().get(DataComponents.KINETIC_WEAPON);
        return weapon != null ? Math.max(1, weapon.delayTicks()) : 8;
    }

    public static void stopSpearUse() {
        if (mc.player != null && isUsingSpear(mc.player)) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
        if (spearInventorySwapped) {
            InvUtils.invSwapBack();
            spearInventorySwapped = false;
        }
        if (spearSavedHotbarSlot >= 0) {
            InvUtils.swap(spearSavedHotbarSlot, false);
            spearSavedHotbarSlot = -1;
        }
    }

    public static boolean canUseAntiShield(Player target) {
        if (target == null || !target.isUsingItem() || !target.isBlocking()) {
            return false;
        }
        return InvUtils.findInHotbar(stack -> stack.getItem() instanceof net.minecraft.world.item.AxeItem).found();
    }

    private static Selection selectMainHand(Predicate<ItemStack> predicate) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        if (predicate.test(player.getMainHandItem())) {
            return new Selection(false, false, null);
        }

        int hotbar = InvUtils.find(predicate, 0, 8).slot();
        if (hotbar != -1) {
            InvUtils.swap(hotbar, true);
            return new Selection(true, true, InvUtils::swapBack);
        }

        int inventory = InvUtils.find(predicate, 9, 35).slot();
        if (inventory != -1) {
            InvUtils.invSwap(inventory);
            return new Selection(true, false, InvUtils::invSwapBack);
        }
        return null;
    }

    private record Selection(boolean changed, boolean hotbar, Runnable restoreAction) {
        private void restore() {
            if (restoreAction != null) {
                restoreAction.run();
            }
        }
    }
}
