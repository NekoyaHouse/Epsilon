package com.github.epsilon.modules.impl.combat.elytra_combat.combat;

import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvHelper;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;

import java.util.Set;
import java.util.function.Predicate;

/**
 * ElytraCombat 独立武器控制器，不依赖 AutoWeapon、MaceAura 或 SpearKill。
 */
public class CombatWeaponController {

    private static final Minecraft mc = Minecraft.getInstance();
    /**
     * 26.3 全部长矛材质；识别 kinetic 组件时不依赖具体物品名硬编码延迟。
     */
    private static final Set<Item> SPEARS = Set.of(
            Items.WOODEN_SPEAR,
            Items.STONE_SPEAR,
            Items.COPPER_SPEAR,
            Items.IRON_SPEAR,
            Items.GOLDEN_SPEAR,
            Items.DIAMOND_SPEAR,
            Items.NETHERITE_SPEAR
    );
    /**
     * 长矛蓄力期间的临时槽位状态，stopSpearUse 必须按相反顺序恢复。
     */
    private static int spearSavedHotbarSlot = -1;
    private static boolean spearInventorySwapped;
    /** 上次伤害窗口过期后的重新起手 tick，用于保证最短蓄力时间。 */
    private static int lastSpearRestartTick = Integer.MIN_VALUE;
    private static int spearRestartCount;
    /** 本模块是否按住右键；原版会在右键释放时中断物品使用。 */
    private static boolean spearKeyHeld;

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
        // 对方举盾时优先切斧破盾，否则切重锤；Selection 负责 finally 中恢复原槽位。
        Selection selection = selectMainHand(
                shieldSwap
                        ? InvHelper::isAxe
                        : stack -> stack.is(Items.MACE)
        );
        if (selection == null) {
            return false;
        }
        try {
            // 只调用原生 attack，攻击距离使用本地实体交互 reach。
            mc.gameMode.attack(player, target);
            if (swingHand) {
                PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
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
            if (!isSpearWindowExpired(player)) {
                holdSpearKey();
                return true;
            }
            // 首次判定单独处理，避免 tickCount - Integer.MIN_VALUE 溢出。
            int tick = player.tickCount;
            boolean restartedRecently = lastSpearRestartTick != Integer.MIN_VALUE
                    && tick - lastSpearRestartTick < Math.max(1, spearReadyTicks());
            if (restartedRecently) {
                return true;
            }
            lastSpearRestartTick = tick;
            ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.expired",
                    "window over ticks=" + player.getTicksUsingItem() + " -> restart");
            stopSpearUse();
        }

        FindItemResult spear = InvUtils.find(CombatWeaponController::isSpearItem);
        if (!spear.found()) {
            return false;
        }

        if (spear.slot() == 40) {
            // 副手长矛无需切换槽位。
        } else if (spear.slot() < 9) {
            // 热栏长矛直接切换选中槽，记录原槽位以便恢复。
            if (spear.slot() != player.getInventory().getSelectedSlot()) {
                spearSavedHotbarSlot = player.getInventory().getSelectedSlot();
                InvUtils.swap(spear.slot(), false);
            }
        } else {
            // 背包内长矛需要与当前选中槽做 inventory swap。
            InvUtils.invSwap(spear.slot());
            spearInventorySwapped = true;
        }
        mc.gameMode.useItem(player, spear.getHand());
        spearRestartCount++;
        // KINETIC_WEAPON 的 CONSUME 返回值不代表成功起手，必须检查实际使用状态。
        boolean using = isUsingSpear(player);
        if (using) {
            holdSpearKey();
        }
        return using;
    }

    /** 是否已超过 kinetic 伤害窗口；没有伤害条件时不作过期处理。 */
    private static boolean isSpearWindowExpired(LocalPlayer player) {
        KineticWeapon weapon = player.getUseItem().get(DataComponents.KINETIC_WEAPON);
        if (weapon == null || weapon.damageConditions().isEmpty()) {
            return false;
        }
        return player.getTicksUsingItem() >= weapon.computeDamageUseDuration();
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
        // delayTicks 是最短蓄力；maxDuration 大于 0 时还要在超时前出手。
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

    /** 当前长矛伤害窗口上界（tick）；未在使用长矛或缺少组件时返回 0。 */
    public static int spearDamageWindowTicks() {
        LocalPlayer player = mc.player;
        if (player == null || !isUsingSpear(player)) {
            return 0;
        }
        KineticWeapon weapon = player.getUseItem().get(DataComponents.KINETIC_WEAPON);
        return weapon != null ? weapon.computeDamageUseDuration() : 0;
    }

    public static int spearRestartCount() {
        return spearRestartCount;
    }

    /** 手中物品是否仍与本次蓄力物品一致，与原版继续使用物品的判据相同。 */
    public static boolean isSpearChargeConsistent() {
        LocalPlayer player = mc.player;
        return player != null && isUsingSpear(player)
                && ItemStack.isSameItem(player.getItemInHand(player.getUsedItemHand()), player.getUseItem());
    }

    public static void stopSpearUse() {
        // 先松开物品与右键，再按栈顺序恢复 inventory swap / hotbar。
        if (mc.player != null && isUsingSpear(mc.player)) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
        releaseSpearKey();
        if (spearInventorySwapped) {
            InvUtils.invSwapBack();
            spearInventorySwapped = false;
        }
        if (spearSavedHotbarSlot >= 0) {
            InvUtils.swap(spearSavedHotbarSlot, false);
            spearSavedHotbarSlot = -1;
        }
    }

    /** 仅接管未被玩家按住的右键，以维持长矛蓄力。 */
    private static void holdSpearKey() {
        if (spearKeyHeld || mc.options == null || mc.options.keyUse.isDown()) {
            return;
        }
        mc.options.keyUse.setDown(true);
        spearKeyHeld = true;
    }

    /** 只松开由本模块按住的右键；玩家自己按着的不处理。 */
    private static void releaseSpearKey() {
        if (!spearKeyHeld) {
            return;
        }
        spearKeyHeld = false;
        if (mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
    }

    public static boolean canUseAntiShield(Player target) {
        if (target == null || !target.isUsingItem() || !target.isBlocking()) {
            return false;
        }
        return InvUtils.findInHotbar(InvHelper::isAxe).found();
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
            // 热栏切换使用 silent swap，恢复动作延迟到 Selection.restore。
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
