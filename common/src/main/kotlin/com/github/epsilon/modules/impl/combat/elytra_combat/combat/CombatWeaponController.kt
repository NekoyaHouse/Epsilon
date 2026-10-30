package com.github.epsilon.modules.impl.combat.elytra_combat.combat

import com.github.epsilon.Constants
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.KineticWeapon
import java.util.function.Predicate

/**
 * ElytraCombat 独立武器控制器，不依赖 AutoWeapon、MaceAura 或 SpearKill。
 */
object CombatWeaponController {

    private val mc: Minecraft
        get() = Constants.mc

    /**
     * 26.3 全部长矛材质；识别 kinetic 组件时不依赖具体物品名硬编码延迟。
     */
    private val SPEARS: Set<Item> = setOf(
        Items.WOODEN_SPEAR,
        Items.STONE_SPEAR,
        Items.COPPER_SPEAR,
        Items.IRON_SPEAR,
        Items.GOLDEN_SPEAR,
        Items.DIAMOND_SPEAR,
        Items.NETHERITE_SPEAR,
    )

    /**
     * 长矛蓄力期间的临时槽位状态，stopSpearUse 必须按相反顺序恢复。
     */
    private var spearSavedHotbarSlot: Int = -1
    private var spearInventorySwapped: Boolean = false

    /**
     * 上次伤害窗口过期后的重新起手 tick，用于保证最短蓄力时间。
     */
    private var lastSpearRestartTick: Int = Int.MIN_VALUE
    private var spearRestartCount: Int = 0

    /**
     * 本模块是否按住右键；原版会在右键释放时中断物品使用。
     */
    private var spearKeyHeld: Boolean = false

    fun isSpearItem(stack: ItemStack): Boolean {
        return !stack.isEmpty && SPEARS.contains(stack.item)
    }

    fun isUsingSpear(entity: LivingEntity?): Boolean {
        return entity != null && entity.isUsingItem && isSpearItem(entity.useItem)
    }

    fun attackMace(
        target: LivingEntity?,
        antiShield: Boolean,
        swingHand: Boolean,
        reachBuffer: Double,
    ): Boolean {
        val player = mc.player ?: return false
        if (target == null || !target.isAlive) {
            return false
        }
        if (!player.isWithinEntityInteractionRange(target, reachBuffer)) {
            return false
        }

        val shieldSwap = antiShield &&
                target is Player &&
                target.isBlocking &&
                target.isUsingItem
        // 对方举盾时优先切斧破盾，否则切重锤；Selection 负责 finally 中恢复原槽位。
        val gameMode = this.mc.gameMode ?: return false
        val selection = selectMainHand(
            if (shieldSwap) {
                Predicate { stack -> InvHelper.isAxe(stack) }
            } else {
                Predicate { stack -> stack.`is`(Items.MACE) }
            }
        ) ?: return false
        try {
            // 只调用原生 attack，攻击距离使用本地实体交互 reach。
            gameMode.attack(player, target)
            if (swingHand) {
                PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
            }
            return true
        } finally {
            selection.restore()
        }
    }

    fun ensureSpearUse(): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        if (isUsingSpear(player)) {
            if (!isSpearWindowExpired(player)) {
                holdSpearKey()
                return true
            }
            // 首次判定单独处理，避免 tickCount - Integer.MIN_VALUE 溢出。
            val tick = player.tickCount
            val restartedRecently = lastSpearRestartTick != Int.MIN_VALUE &&
                    tick - lastSpearRestartTick < Math.max(1, spearReadyTicks())
            if (restartedRecently) {
                return true
            }
            lastSpearRestartTick = tick
            ElytraDebug.log(
                ElytraDebug.SLOT_SPEAR_HIT,
                "spear.expired",
                "window over ticks=" + player.ticksUsingItem + " -> restart",
            )
            stopSpearUse()
        }

        val spear = InvUtils.find(Predicate { stack -> isSpearItem(stack) })
        if (!spear.found()) {
            return false
        }

        if (spear.slot == 40) {
            // 副手长矛无需切换槽位。
        } else if (spear.slot < 9) {
            // 热栏长矛直接切换选中槽，记录原槽位以便恢复。
            if (spear.slot != player.inventory.selectedSlot) {
                spearSavedHotbarSlot = player.inventory.selectedSlot
                InvUtils.swap(spear.slot, false)
            }
        } else {
            // 背包内长矛需要与当前选中槽做 inventory swap。
            InvUtils.invSwap(spear.slot)
            spearInventorySwapped = true
        }
        gameMode.useItem(player, spear.getHand())
        spearRestartCount++
        // KINETIC_WEAPON 的 CONSUME 返回值不代表成功起手，必须检查实际使用状态。
        val using = isUsingSpear(player)
        if (using) {
            holdSpearKey()
        }
        return using
    }

    /**
     * 是否已超过 kinetic 伤害窗口；没有伤害条件时不作过期处理。
     */
    private fun isSpearWindowExpired(player: LocalPlayer): Boolean {
        val weapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return false
        if (weapon.damageConditions().isEmpty()) {
            return false
        }
        return player.ticksUsingItem >= weapon.computeDamageUseDuration()
    }

    fun canUseSpearAttack(): Boolean {
        val player = mc.player ?: return false
        if (!isUsingSpear(player)) {
            return false
        }

        val stack = player.useItem
        val weapon = stack.get(DataComponents.KINETIC_WEAPON)
        val ticksUsed = player.ticksUsingItem
        if (weapon == null) {
            return ticksUsed >= 8
        }

        val maxDuration = weapon.computeDamageUseDuration()
        // delayTicks 是最短蓄力；maxDuration 大于 0 时还要在超时前出手。
        return ticksUsed >= weapon.delayTicks() && (maxDuration <= 0 || ticksUsed < maxDuration)
    }

    fun spearReadyTicks(): Int {
        val player = mc.player
        if (player == null || !isUsingSpear(player)) {
            return 8
        }
        val weapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        return if (weapon != null) Math.max(1, weapon.delayTicks()) else 8
    }

    /**
     * 当前长矛伤害窗口上界（tick）；未在使用长矛或缺少组件时返回 0。
     */
    fun spearDamageWindowTicks(): Int {
        val player = mc.player
        if (player == null || !isUsingSpear(player)) {
            return 0
        }
        val weapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        return if (weapon != null) weapon.computeDamageUseDuration() else 0
    }

    fun spearRestartCount(): Int {
        return spearRestartCount
    }

    /**
     * 手中物品是否仍与本次蓄力物品一致，与原版继续使用物品的判据相同。
     */
    fun isSpearChargeConsistent(): Boolean {
        val player = mc.player ?: return false
        return isUsingSpear(player) &&
                ItemStack.isSameItem(player.getItemInHand(player.usedItemHand), player.useItem)
    }

    fun stopSpearUse() {
        // 先松开物品与右键，再按栈顺序恢复 inventory swap / hotbar。
        val player = mc.player
        if (player != null && isUsingSpear(player)) {
            mc.gameMode?.releaseUsingItem(player)
        }
        releaseSpearKey()
        if (spearInventorySwapped) {
            InvUtils.invSwapBack()
            spearInventorySwapped = false
        }
        if (spearSavedHotbarSlot >= 0) {
            InvUtils.swap(spearSavedHotbarSlot, false)
            spearSavedHotbarSlot = -1
        }
    }

    /**
     * 仅接管未被玩家按住的右键，以维持长矛蓄力。
     */
    private fun holdSpearKey() {
        if (spearKeyHeld || mc.options == null || mc.options.keyUse.isDown) {
            return
        }
        mc.options.keyUse.isDown = true
        spearKeyHeld = true
    }

    /**
     * 只松开由本模块按住的右键；玩家自己按着的不处理。
     */
    private fun releaseSpearKey() {
        if (!spearKeyHeld) {
            return
        }
        spearKeyHeld = false
        if (mc.options != null) {
            mc.options.keyUse.isDown = false
        }
    }

    fun canUseAntiShield(target: Player?): Boolean {
        if (target == null || !target.isUsingItem || !target.isBlocking) {
            return false
        }
        return InvUtils.findInHotbar(Predicate { stack -> InvHelper.isAxe(stack) }).found()
    }

    private fun selectMainHand(predicate: Predicate<ItemStack>): Selection? {
        val player = mc.player ?: return null
        if (predicate.test(player.mainHandItem)) {
            return Selection(false, false, null)
        }

        val hotbar = InvUtils.find(predicate, 0, 8).slot
        if (hotbar != -1) {
            // 热栏切换使用 silent swap，恢复动作延迟到 Selection.restore。
            InvUtils.swap(hotbar, true)
            return Selection(true, true, Runnable { InvUtils.swapBack() })
        }

        val inventory = InvUtils.find(predicate, 9, 35).slot
        if (inventory != -1) {
            InvUtils.invSwap(inventory)
            return Selection(true, false, Runnable { InvUtils.invSwapBack() })
        }
        return null
    }

    private data class Selection(val changed: Boolean, val hotbar: Boolean, val restoreAction: Runnable?) {

        fun restore() {
            restoreAction?.run()
        }

    }

}
