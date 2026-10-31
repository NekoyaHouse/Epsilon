package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.tags.EntityTypeTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.MaceItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.enchantment.Enchantments

object AutoWeapon : Module("Auto Weapon", Category.COMBAT) {

    private enum class Mode {
        Simple,
        Smart,
    }

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgSwapping: SettingGroup = settingGroup("Swapping")
    private val sgSwordEnchants: SettingGroup = settingGroup("Sword Enchants")
    private val sgMaceEnchants: SettingGroup = settingGroup("Mace Enchants")
    private val sgOtherEnchants: SettingGroup = settingGroup("Other Enchants")
    private val sgWeapon: SettingGroup = settingGroup("Weapon")

    private var pauseOnEat by boolSetting("Pause On Eat", true).apply { group(sgGeneral) }
    private var mode by enumSetting("Mode", Mode.Simple).apply { group(sgGeneral) }
    private var targetSlot by intSetting(
        "Target Slot", 1, 1, 9, 1,
        dependency = { mode == (Mode.Simple) },
    ).apply { group(sgGeneral) }
    private var swapBack by boolSetting("Swap Back", true).apply { group(sgGeneral) }
    private var swapBackDelay by intSetting("Swap Back Delay", 200, 0, 500, 10).apply { group(sgGeneral) }

    private var smartDurability by boolSetting(
        "Smart Durability Saver", false,
        dependency = { mode == (Mode.Smart) },
    ).apply { group(sgSwapping) }
    private var smartShieldBreak by boolSetting(
        "Smart Shield Breaker", true,
        dependency = { mode == (Mode.Smart) },
    ).apply { group(sgSwapping) }
    private var swordSwapping by boolSetting(
        "Sword Swapping", true,
        dependency = { mode == (Mode.Smart) },
    ).apply { group(sgSwapping) }
    private var maceSwapping by boolSetting(
        "Mace Swapping", true,
        dependency = { mode == (Mode.Smart) },
    ).apply { group(sgSwapping) }
    private var otherSwapping by boolSetting(
        "Other Swapping", true,
        dependency = { mode == (Mode.Smart) },
    ).apply { group(sgSwapping) }

    private var enchantFireAspect by boolSetting(
        "Fire Aspect", true,
        dependency = { mode == (Mode.Smart) && swordSwapping },
    ).apply { group(sgSwordEnchants) }
    private var enchantLooting by boolSetting(
        "Looting", true,
        dependency = { mode == (Mode.Smart) && swordSwapping },
    ).apply { group(sgSwordEnchants) }
    private var enchantSharpness by boolSetting(
        "Sharpness", true,
        dependency = { mode == (Mode.Smart) && swordSwapping },
    ).apply { group(sgSwordEnchants) }
    private var enchantSmite by boolSetting(
        "Smite", true,
        dependency = { mode == (Mode.Smart) && swordSwapping },
    ).apply { group(sgSwordEnchants) }
    private var enchantBaneOfArthropods by boolSetting(
        "Bane of Arthropods", true,
        dependency = { mode == (Mode.Smart) && swordSwapping },
    ).apply { group(sgSwordEnchants) }
    private var enchantSweepingEdge by boolSetting(
        "Sweeping Edge", true,
        dependency = { mode == (Mode.Smart) && swordSwapping },
    ).apply { group(sgSwordEnchants) }

    private var regularMace by boolSetting(
        "Regular Mace", true,
        dependency = { mode == (Mode.Smart) && maceSwapping },
    ).apply { group(sgMaceEnchants) }
    private var enchantDensity by boolSetting(
        "Density", true,
        dependency = { mode == (Mode.Smart) && maceSwapping },
    ).apply { group(sgMaceEnchants) }
    private var enchantBreach by boolSetting(
        "Breach", true,
        dependency = { mode == (Mode.Smart) && maceSwapping },
    ).apply { group(sgMaceEnchants) }
    private var enchantWindBurst by boolSetting(
        "Wind Burst", true,
        dependency = { mode == (Mode.Smart) && maceSwapping },
    ).apply { group(sgMaceEnchants) }

    private var enchantImpaling by boolSetting(
        "Impaling", true,
        dependency = { mode == (Mode.Smart) && otherSwapping },
    ).apply { group(sgOtherEnchants) }

    private var onlyOnWeapon by boolSetting(
        "Only On Weapon", false,
        dependency = { mode == (Mode.Smart) },
    ).apply { group(sgWeapon) }
    private var sword by boolSetting(
        "Sword", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }
    private var axe by boolSetting(
        "Axe", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }
    private var pickaxe by boolSetting(
        "Pickaxe", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }
    private var shovel by boolSetting(
        "Shovel", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }
    private var hoe by boolSetting(
        "Hoe", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }
    private var mace by boolSetting(
        "Mace", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }
    private var trident by boolSetting(
        "Trident", true,
        dependency = { mode == (Mode.Smart) && onlyOnWeapon },
    ).apply { group(sgWeapon) }

    private val backTimer = TimerUtils()
    private var awaitingBack: Boolean = false

    init {
        nonNullHandler<AttackEntityEvent> { onAttackEntity(it) }
        nonNullHandler<PlayerTickEvent.Pre> { onTick() }
    }

    override fun onDisable() {
        backTimer.reset()
        awaitingBack = false
    }

    context(nn: NonNullContext)
    private fun onAttackEntity(event: AttackEntityEvent) {
        if (ElytraCombat.isControllingCombat()) {
            return
        }
        if (pauseOnEat && PlayerUtils.isEating()) return
        if (!canSwapByWeapon()) return
        performSwap(event.entity)
    }

    context(nn: NonNullContext)
    private fun performSwap(target: Entity?) {
        if (awaitingBack) return

        val player = this.mc.player ?: return
        val slotIndex = if (mode == (Mode.Simple)) {
            targetSlot - 1
        } else {
            getSmartSlot(target)
        }

        if (slotIndex < 0 || slotIndex > 8) return
        if (slotIndex == player.inventory.selectedSlot) return

        InvUtils.swap(slotIndex, swapBack)

        awaitingBack = swapBack
    }

    context(nn: NonNullContext)
    private fun onTick() {
        if (!awaitingBack) return
        if (backTimer.passedMillise(swapBackDelay.toLong())) {
            InvUtils.swapBack()
            backTimer.reset()
            awaitingBack = false
        }
    }

    context(nn: NonNullContext)
    private fun canSwapByWeapon(): Boolean {
        if (!onlyOnWeapon) return true
        return InvUtils.testInMainHand { item ->
            (sword && item == (ItemTags.SWORDS)) ||
                    (axe && item == (ItemTags.AXES)) ||
                    (pickaxe && item == (ItemTags.PICKAXES)) ||
                    (shovel && item == (ItemTags.SHOVELS)) ||
                    (hoe && item == (ItemTags.HOES)) ||
                    (mace && item.item is MaceItem) ||
                    (trident && item.item is TridentItem)
        }
    }

    context(nn: NonNullContext)
    private fun getSmartSlot(target: Entity?): Int {
        val player = this.mc.player ?: return -1
        val currentStack = player.mainHandItem

        val livingTarget = target as? LivingEntity
        if (livingTarget != null && smartShieldBreak && livingTarget.isBlocking) {
            if (InvHelper.isAxe(currentStack)) return -1
            val axeSlot = InvUtils.findInHotbar { stack -> InvHelper.isAxe(stack) }.slot
            if (axeSlot != -1) return axeSlot
        }

        val isFalling = player.fallDistance.toDouble() > 1.5
        val durability = smartDurability

        val isLiving = livingTarget != null
        val isPlayer = target is Player
        val isOnFire = target?.isOnFire ?: false
        val isUndead = target?.`is`(EntityTypeTags.SENSITIVE_TO_SMITE) ?: false
        val isArthropod = target?.`is`(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS) ?: false
        val isAquatic = target?.`is`(EntityTypeTags.SENSITIVE_TO_IMPALING) ?: false
        val hasFireResistance = livingTarget != null &&
                (livingTarget.hasEffect(MobEffects.FIRE_RESISTANCE) || hasFireProtectionArmor(livingTarget))
        val armor = livingTarget?.getAttributeValue(Attributes.ARMOR) ?: 0.0
        val health = livingTarget?.health ?: 0.0f

        var bestSlot = -1
        var bestScore = getItemScore(
            currentStack, isFalling, durability, isLiving, isPlayer, isOnFire,
            hasFireResistance, isUndead, isArthropod, isAquatic, armor, health,
        )

        for (i in 0 until 9) {
            if (i == player.inventory.selectedSlot) continue

            val stack = player.inventory.getItem(i)
            if (stack.isEmpty && !durability) continue

            val score = getItemScore(
                stack, isFalling, durability, isLiving, isPlayer, isOnFire,
                hasFireResistance, isUndead, isArthropod, isAquatic, armor, health,
            )
            if (score > bestScore) {
                bestScore = score
                bestSlot = i
            }
        }

        return bestSlot
    }

    private fun getItemScore(
        stack: ItemStack,
        isFalling: Boolean,
        durability: Boolean,
        isLiving: Boolean,
        isPlayer: Boolean,
        isOnFire: Boolean,
        hasFireResistance: Boolean,
        isUndead: Boolean,
        isArthropod: Boolean,
        isAquatic: Boolean,
        armor: Double,
        health: Float,
    ): Double {
        var score = 0.0

        if (durability) {
            score += getDurabilityScore(stack)
        }

        if (stack.isEmpty) return score

        score += getCombatScore(
            stack, isFalling, isLiving, isPlayer, isOnFire,
            hasFireResistance, isUndead, isArthropod, isAquatic, armor, health,
        )

        return score
    }

    private fun getDurabilityScore(stack: ItemStack): Double {
        if (!stack.isDamageableItem) return 4.0

        val unbreaking = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.UNBREAKING)
        if (unbreaking > 0) return unbreaking * 0.05

        return 0.0
    }

    private fun getCombatScore(
        stack: ItemStack,
        isFalling: Boolean,
        isLiving: Boolean,
        isPlayer: Boolean,
        isOnFire: Boolean,
        hasFireResistance: Boolean,
        isUndead: Boolean,
        isArthropod: Boolean,
        isAquatic: Boolean,
        armor: Double,
        health: Float,
    ): Double {
        var score = 0.0

        if (swordSwapping) {
            score += getFireAspectScore(stack, isOnFire, hasFireResistance)
            score += getLootingScore(stack, isPlayer, isLiving, isOnFire, health)
            score += getSharpnessScore(stack, isOnFire)
            score += getSmiteScore(stack, isUndead, isOnFire)
            score += getBaneOfArthropodsScore(stack, isArthropod, isOnFire)
            score += getSweepingEdgeScore(stack)
        }
        if (maceSwapping) {
            score += getBreachScore(stack, isLiving, armor)
            score += getDensityScore(stack, isFalling)
            score += getWindBurstScore(stack, isFalling)
            score += getMaceScore(stack, isFalling)
        }
        if (otherSwapping) {
            score += getImpalingScore(stack, isAquatic)
        }

        return score
    }

    private fun getFireAspectScore(stack: ItemStack, isOnFire: Boolean, hasFireResistance: Boolean): Double {
        if (!enchantFireAspect || isOnFire || hasFireResistance) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FIRE_ASPECT)
        return if (level > 0) 30.0 else 0.0
    }

    private fun getLootingScore(
        stack: ItemStack,
        isPlayer: Boolean,
        isLiving: Boolean,
        isOnFire: Boolean,
        health: Float,
    ): Double {
        if (!enchantLooting || isPlayer) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.LOOTING)
        if (level > 0) {
            val execute = (isLiving && health < 20) || isOnFire
            return level * (if (execute) 10 else 5).toDouble()
        }
        return 0.0
    }

    private fun getSharpnessScore(stack: ItemStack, isOnFire: Boolean): Double {
        if (!enchantSharpness) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS)
        if (level > 0) {
            val baseScore = (1 + 0.5 * (level - 1)) * 3
            return if (isOnFire) baseScore * 1.5 else baseScore
        }
        return 0.0
    }

    private fun getSmiteScore(stack: ItemStack, isUndead: Boolean, isOnFire: Boolean): Double {
        if (!enchantSmite || !isUndead) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SMITE)
        if (level > 0) {
            val baseScore = level * 5.0
            return if (isOnFire) baseScore * 1.5 else baseScore
        }
        return 0.0
    }

    private fun getBaneOfArthropodsScore(stack: ItemStack, isArthropod: Boolean, isOnFire: Boolean): Double {
        if (!enchantBaneOfArthropods || !isArthropod) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS)
        if (level > 0) {
            val baseScore = level * 5.0
            return if (isOnFire) baseScore * 1.5 else baseScore
        }
        return 0.0
    }

    private fun getSweepingEdgeScore(stack: ItemStack): Double {
        if (!enchantSweepingEdge) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SWEEPING_EDGE)
        if (level > 0) {
            return level * 3.0
        }
        return 0.0
    }

    private fun getImpalingScore(stack: ItemStack, isAquatic: Boolean): Double {
        if (!enchantImpaling || !isAquatic) return 0.0

        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.IMPALING)
        if (level > 0) {
            return level * 5.0
        }
        return 0.0
    }

    private fun getBreachScore(stack: ItemStack, isLiving: Boolean, armor: Double): Double {
        if (!enchantBreach || !isLiving || armor <= 0) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BREACH)
        if (level > 0) {
            return level * armor * 0.3
        }
        return 0.0
    }

    private fun getDensityScore(stack: ItemStack, isFalling: Boolean): Double {
        if (!enchantDensity || !isFalling) return 0.0
        val player = this.mc.player ?: return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.DENSITY)
        if (level > 0) return (50 + (level * player.fallDistance * 2)).toDouble()
        return 0.0
    }

    private fun getWindBurstScore(stack: ItemStack, isFalling: Boolean): Double {
        if (!enchantWindBurst || !isFalling) return 0.0
        val level = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.WIND_BURST)
        if (level > 0) return level * 20.0
        return 0.0
    }

    private fun getMaceScore(stack: ItemStack, isFalling: Boolean): Double {
        if (!regularMace || !isFalling) return 0.0
        if (stack.item is MaceItem) return 40.0
        return 0.0
    }

    private fun hasFireProtectionArmor(entity: LivingEntity): Boolean {
        for (slot in arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val stack = entity.getItemBySlot(slot)
            if (stack.isEmpty) continue

            val fireProtection = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION)
            if (fireProtection > 0) return true
        }
        return false
    }

}
