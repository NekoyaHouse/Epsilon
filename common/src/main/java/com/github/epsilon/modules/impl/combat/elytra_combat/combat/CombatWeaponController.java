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
    /**
     * 上次因伤害窗口过期而重新起手的 tick。
     *
     * <p>过期判定读的是客户端自己的 {@code useItemRemaining}；该值一旦异常，过期会每 tick 成立，
     * 把 {@code ticksUsed} 永久压在 0（表现为 {@code using=true} 但 {@code ready} 永远是 false）。
     * 限制重起手频率，保证每次重起手后至少能积累一个起手延迟的蓄力时间。</p>
     */
    private static int lastSpearRestartTick = Integer.MIN_VALUE;
    /**
     * 调试用：累计真正重新起手的次数。
     *
     * <p>蓄力进度长期停在 0 只有两种可能——起手被反复重置，或递减根本没有发生。这个计数能把两者
     * 分开：它持续快速增长就说明 {@code ensureSpearUse} 在反复起手。</p>
     */
    private static int spearRestartCount;
    /**
     * 蓄力期间是否由本模块替玩家按住了右键。
     *
     * <p>原版 {@code Minecraft#handleKeybinds} 每 tick 都在"正在使用物品但右键没按下"时调用
     * {@code releaseUsingItem}。只用 {@code gameMode.useItem} 主动起手而不按住右键，蓄力会在下一
     * tick 被原版当场松开，表现为 {@code getTicksUsingItem()} 永远为 0、{@code ready} 永远 false，
     * 而 {@code ensureSpearUse} 只好每 tick 重新起手。</p>
     */
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
            // 伤害窗口已过期：原版之后不再判定 kinetic，继续举着打不出任何伤害，
            // 必须松手重新起手，否则会永久停在"蓄力中却零命中"的状态。
            // 但要留出最短蓄力时间，否则一旦过期判定异常就会每 tick 重置，ready 永远为 false。
            // 注意首次判定必须走显式分支：tickCount - Integer.MIN_VALUE 会溢出成负数。
            int tick = player.tickCount;
            boolean restartedRecently = lastSpearRestartTick != Integer.MIN_VALUE
                    && tick - lastSpearRestartTick < Math.max(1, spearReadyTicks());
            if (restartedRecently) {
                return true;
            }
            lastSpearRestartTick = tick;
            // 打上日志：这是"长时间没命中、长矛萎掉"的唯一恢复路径，必须能观察到。
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
        // Item.use 对带 KINETIC_WEAPON 的物品无条件返回 CONSUME，consumesAction() 无法区分
        // "真的起手了"和"startUsingItem 被静默拒绝"（例如当时正在使用别的物品），这里只认实际使用状态。
        boolean using = isUsingSpear(player);
        if (using) {
            // 只有确认真的起手了才按住右键：否则原版会拿主手的其他物品去 startUseItem。
            holdSpearKey();
        }
        return using;
    }

    /**
     * 长矛的 kinetic 伤害窗口是否已经过期。
     *
     * <p>原版只在 {@code delayTicks} 到 {@code delayTicks + damageTime} 之间判定命中，之后即使
     * 仍保持"使用中"也不会再造成伤害。没有伤害条件的长矛原版本就不会命中，不作过期处理。</p>
     */
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

    /**
     * 当前长矛的 kinetic 伤害窗口上界（{@code delayTicks + damageTime}）；未在使用长矛或缺少组件时返回 0。
     *
     * <p>调试用：配合 {@link #spearReadyTicks()} 与 {@code player.getTicksUsingItem()}，可以判断
     * {@code canUseSpearAttack()} 为 false 时究竟是"还没到最短蓄力"还是"窗口已经过期"。</p>
     */
    public static int spearDamageWindowTicks() {
        LocalPlayer player = mc.player;
        if (player == null || !isUsingSpear(player)) {
            return 0;
        }
        KineticWeapon weapon = player.getUseItem().get(DataComponents.KINETIC_WEAPON);
        return weapon != null ? weapon.computeDamageUseDuration() : 0;
    }

    /** 调试用：累计重新起手次数；持续增长说明蓄力在被反复重置。 */
    public static int spearRestartCount() {
        return spearRestartCount;
    }

    /**
     * 调试用：手中物品是否仍与本次蓄力的物品一致。
     *
     * <p>这正是 {@code LivingEntity.updatingUsingItem()} 用来决定"继续蓄力"还是 {@code stopUsingItem()}
     * 的判据（服务端同步的 {@code getUsedItemHand()} 与本次 {@code useItem} 比对）。返回 false 说明
     * 客户端每 tick 都会自己中断蓄力，然后由 {@code ensureSpearUse} 重新起手，使 ticksUsed 永远为 0。</p>
     */
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

    /**
     * 替玩家按住右键，让原版不去松开蓄力。
     *
     * <p>玩家自己已经按着右键时不接管，也不记录归属，松开时自然不去碰玩家的按键。</p>
     */
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
