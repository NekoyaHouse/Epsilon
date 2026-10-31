package com.github.epsilon.utils.combat

import com.github.epsilon.Constants
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.PlayerUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * 用于计算实体所受爆炸伤害的工具类。
 *
 * 客户端计算流程与原版 `ServerExplosion`、`ExplosionDamageCalculator`、`CombatRules` 以及
 * `LivingEntity.getDamageAfterArmorAbsorb/getDamageAfterMagicAbsorb` 保持一致。
 */
object DamageUtils {

    /**
     * `EndCrystal.hurtServer` 定义的末影水晶爆炸半径。
     */
    const val CRYSTAL_EXPLOSION_RADIUS: Float = 6.0f

    /**
     * `RespawnAnchorBlock.explode` 定义的重生锚爆炸半径。
     */
    const val ANCHOR_EXPLOSION_RADIUS: Float = 5.0f

    private val ARMOR_SLOTS = arrayOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    )

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * 估算末影水晶爆炸对目标造成的最终伤害。
     *
     * @param target     目标实体
     * @param crystalPos 末影水晶爆炸中心
     * @param targetPos  目标预测位置；为 null 时使用实体当前位置
     * @param mode       护甲附魔计算模式
     * @return 应用全部减免后的估算伤害，最小为 0
     */
    fun crystalDamage(target: LivingEntity, crystalPos: Vec3, targetPos: Vec3?, mode: ArmorEnchantmentMode): Float {
        return explosionDamage(target, crystalPos, CRYSTAL_EXPLOSION_RADIUS, targetPos, mode)
    }

    /**
     * 估算重生锚爆炸对目标造成的最终伤害。
     *
     * @param target    目标实体
     * @param anchorPos 重生锚爆炸中心
     * @param mode      护甲附魔计算模式
     * @return 应用全部减免后的估算伤害，最小为 0
     */
    fun anchorDamage(target: LivingEntity, anchorPos: Vec3, mode: ArmorEnchantmentMode): Float {
        return explosionDamage(target, anchorPos, ANCHOR_EXPLOSION_RADIUS, null, mode)
    }

    /**
     * 估算指定爆炸对目标造成的最终伤害。
     *
     * @param target       目标实体
     * @param explosionPos 爆炸中心
     * @param radius       爆炸或特效半径
     * @param targetPos    目标预测位置；为 null 时使用实体当前位置
     * @param mode         护甲附魔计算模式
     * @return 应用全部减免后的估算伤害，最小为 0
     */
    fun explosionDamage(
        target: LivingEntity,
        explosionPos: Vec3,
        radius: Float,
        targetPos: Vec3?,
        mode: ArmorEnchantmentMode,
    ): Float {
        if (target.isInvulnerable) return 0f

        val doubleRadius = radius * 2.0f
        val entityPos = targetPos ?: target.position()
        val dist = entityPos.distanceTo(explosionPos) / doubleRadius
        if (dist > 1.0) return 0f

        val box = if (targetPos != null) getPredictedBoundingBox(target, targetPos) else target.boundingBox
        val exposure = getSeenPercent(explosionPos, box, target)
        if (exposure <= 0f) return 0f

        val impact = (1.0 - dist) * exposure
        var baseDamage = ((impact * impact + impact) / 2.0 * 7.0 * doubleRadius + 1.0).toFloat()

        if (target is Player) {
            baseDamage = applyDifficultyScaling(baseDamage, target)
        }

        val afterArmor = applyArmorReduction(target, baseDamage)
        val afterResistance = applyResistanceReduction(target, afterArmor)
        val afterEnchants = applyEnchantmentReduction(target, afterResistance, mode)

        return Math.max(0f, afterEnchants)
    }

    /**
     * 计算尚未应用护甲等减免的原始爆炸伤害。
     *
     * @param target       目标实体
     * @param explosionPos 爆炸中心
     * @param radius       爆炸或特效半径
     * @return 未应用减免的估算伤害，最小为 0
     */
    fun rawExplosionDamage(target: LivingEntity, explosionPos: Vec3, radius: Float): Float {
        val doubleRadius = radius * 2.0f
        val dist = Math.sqrt(target.distanceToSqr(explosionPos)) / doubleRadius
        if (dist > 1.0) return 0f

        val exposure = getSeenPercent(explosionPos, target)
        if (exposure <= 0f) return 0f

        val impact = (1.0 - dist) * exposure
        return ((impact * impact + impact) / 2.0 * 7.0 * doubleRadius + 1.0).toFloat()
    }

    // ── exposure (seen percent) ─────────────────────────────────────────────

    /**
     * 计算爆炸中心对目标包围盒的无遮挡采样比例。
     *
     * @param center 爆炸中心
     * @param entity 实体
     * @return 范围为 0 到 1 的无遮挡比例
     */
    fun getSeenPercent(center: Vec3, entity: LivingEntity): Float {
        return getSeenPercent(center, entity.boundingBox, entity)
    }

    /**
     * 计算爆炸中心对目标包围盒的无遮挡采样比例。
     *
     * @param center 爆炸中心
     * @param bb     用于采样的实体包围盒
     * @param entity 实体
     * @return 范围为 0 到 1 的无遮挡比例
     */
    fun getSeenPercent(center: Vec3, bb: AABB, entity: LivingEntity): Float {
        val xs = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0)
        val ys = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0)
        val zs = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0)
        val xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0
        val zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0

        if (xs < 0.0 || ys < 0.0 || zs < 0.0) return 0.0f

        val ctx = NonNullContext.current ?: return 0.0f

        var hits = 0
        var total = 0

        var xx = 0.0
        while (xx <= 1.0) {
            var yy = 0.0
            while (yy <= 1.0) {
                var zz = 0.0
                while (zz <= 1.0) {
                    val x = Mth.lerp(xx, bb.minX, bb.maxX)
                    val y = Mth.lerp(yy, bb.minY, bb.maxY)
                    val z = Mth.lerp(zz, bb.minZ, bb.maxZ)
                    val from = Vec3(x + xOffset, y, z + zOffset)

                    if (ctx.world.clip(
                            ClipContext(
                                from, center,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                entity,
                            )
                        ).type == HitResult.Type.MISS
                    ) {
                        hits++
                    }
                    total++
                    zz += zs
                }
                yy += ys
            }
            xx += xs
        }

        return hits.toFloat() / total
    }

    // ── 难度缩放 ───────────────────────────────────────────────────────────

    /**
     * 复现原版对玩家应用的难度伤害缩放。
     *
     * 爆炸伤害类型使用 `DamageScaling.ALWAYS`，因此始终按难度缩放：
     * 和平 0；简单 min(伤害 / 2 + 1, 伤害)；普通不变；困难 ×1.5。
     */
    private fun applyDifficultyScaling(damage: Float, player: Player): Float {
        val difficulty = player.level().difficulty
        return when (difficulty) {
            Difficulty.PEACEFUL -> 0f
            Difficulty.EASY -> Math.min(damage / 2.0f + 1.0f, damage)
            Difficulty.NORMAL -> damage
            Difficulty.HARD -> damage * 1.5f
            // 原 Java switch 覆盖全部枚举常量；Kotlin 对 Java 枚举要求 else 分支。
            else -> damage
        }
    }

    // ── 护甲减免 ───────────────────────────────────────────────────────────

    /**
     * 在客户端复现 `CombatRules.getDamageAfterAbsorb`。
     *
     * 爆炸伤害没有 `BYPASSES_ARMOR` 标签，因此需要应用护甲减免。
     */
    private fun applyArmorReduction(target: LivingEntity, damage: Float): Float {
        val totalArmor = target.getAttributeValue(Attributes.ARMOR).toFloat()
        val armorToughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()

        // CombatRules.getDamageAfterAbsorb (without weapon-based armor piercing)
        val toughness = 2.0f + armorToughness / 4.0f
        val effectiveArmor = Mth.clamp(totalArmor - damage / toughness, totalArmor * 0.2f, 20.0f)
        val armorFraction = effectiveArmor / 25.0f
        return damage * (1.0f - armorFraction)
    }

    // ── 抗性提升效果 ───────────────────────────────────────────────────────

    /**
     * 复现 `LivingEntity.getDamageAfterMagicAbsorb` 中抗性提升效果的减伤。
     *
     * 每级抗性提升减少 20% 伤害。
     */
    private fun applyResistanceReduction(target: LivingEntity, damage: Float): Float {
        if (!target.hasEffect(MobEffects.RESISTANCE)) {
            return damage
        }

        val amplifier = target.getEffect(MobEffects.RESISTANCE)?.amplifier ?: return damage
        val reduction = (amplifier + 1) * 5 // 5 per level
        val remaining = 25 - reduction
        val reduced = damage * remaining
        return Math.max(reduced / 25.0f, 0.0f)
    }

    // ── 附魔保护 ───────────────────────────────────────────────────────────

    /**
     * 通过直接读取护甲附魔，在客户端估算针对爆炸的附魔保护值。
     *
     * 原版数据包中 `Enchantments` 定义的数值：`Protection` 每级 +1，`Blast Protection` 每级 +2；
     * 总保护值限制在 [0, 20]，再按 `CombatRules.getDamageAfterMagicAbsorb` 应用。
     */
    private fun applyEnchantmentReduction(target: LivingEntity, damage: Float, mode: ArmorEnchantmentMode): Float {
        var totalProtection = 0f

        when (mode) {
            ArmorEnchantmentMode.None -> {
                for (slot in ARMOR_SLOTS) {
                    val stack = target.getItemBySlot(slot)
                    if (stack.isEmpty) continue

                    val protLevel = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION)
                    val blastLevel = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION)

                    totalProtection += protLevel * 1.0f // Protection: 1 per level
                    totalProtection += blastLevel * 2.0f // Blast Protection: 2 per level
                }
            }

            ArmorEnchantmentMode.PPPP -> totalProtection += 4 * 1.0f * 4
            ArmorEnchantmentMode.PPBP -> {
                totalProtection += 4 * 1.0f * 3
                totalProtection += 4 * 2.0f * 1
            }
        }

        // CombatRules.getDamageAfterMagicAbsorb
        val clamped = Mth.clamp(totalProtection, 0.0f, 20.0f)
        return damage * (1.0f - clamped / 25.0f)
    }

    // ── helper: self-damage shortcut ────────────────────────────────────────

    /**
     * 估算末影水晶爆炸对本地玩家造成的最终伤害。
     *
     * @param crystalPos 末影水晶爆炸中心
     * @param mode       护甲附魔计算模式
     * @return 本地玩家预计受到的伤害，最小为 0
     */
    fun selfCrystalDamage(crystalPos: Vec3, mode: ArmorEnchantmentMode): Float {
        return selfCrystalDamage(crystalPos, null, mode)
    }

    /**
     * 估算末影水晶爆炸对本地玩家造成的最终伤害。
     *
     * @param crystalPos 末影水晶爆炸中心
     * @param selfPos    本地玩家预测位置；为 null 时使用当前位置
     * @param mode       护甲附魔计算模式
     * @return 本地玩家预计受到的伤害，最小为 0
     */
    fun selfCrystalDamage(crystalPos: Vec3, selfPos: Vec3?, mode: ArmorEnchantmentMode): Float {
        val player = NonNullContext.current?.player ?: return 0f
        return crystalDamage(player, crystalPos, selfPos, mode)
    }

    /**
     * 根据预测位置构造实体的受限包围盒。
     *
     * @param entity 实体
     * @param pos    目标位置
     * @return 以预测位置为中心构造的实体包围盒
     */
    fun getPredictedBoundingBox(entity: LivingEntity, pos: Vec3): AABB {
        val width = entity.bbWidth
        val height = entity.bbHeight
        val halfWidth = Math.min(width, 2.0f) / 2.0
        val clampedHeight = Math.min(height.toDouble(), 3.0)

        return AABB(
            pos.x - halfWidth, pos.y, pos.z - halfWidth,
            pos.x + halfWidth, pos.y + clampedHeight, pos.z + halfWidth,
        )
    }

    /**
     * 破坏准星指向的末影水晶（用于自动打水晶类模块）。
     */
    fun breakCrosshairCrystal(): Boolean {
        val ctx = NonNullContext.current ?: return false
        if (Constants.client.cameraEntity == null) return false

        val player = ctx.player
        val world = ctx.world
        val eye = player.eyePosition
        val look = player.lookAngle
        val end = eye.add(look.scale(4.0))

        var placePos: BlockPos? = null
        var d = 0.1
        while (d <= 4.0) {
            val point = eye.add(look.scale(d))
            val pos = BlockPos.containing(point)
            val state: BlockState = world.getBlockState(pos)
            if (state.isAir || state.canBeReplaced()) {
                placePos = pos
                break
            }
            d += 0.1
        }

        if (placePos == null) return false

        var target: EndCrystal? = null
        var bestDistSq = Double.MAX_VALUE
        val searchBox = AABB(eye, end).inflate(0.5)
        for (crystal in world.getEntitiesOfClass(EndCrystal::class.java, searchBox)) {
            if (!crystal.isAlive) continue
            val toCrystal = crystal.position().subtract(eye)
            val projection = toCrystal.dot(look)
            if (projection < 0 || projection > 4.0) continue
            val closest = eye.add(look.scale(projection))
            if (closest.distanceToSqr(crystal.position()) > 1.5 * 1.5) continue
            val distSq = eye.distanceToSqr(crystal.position())
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                target = crystal
            }
        }

        if (target != null) {
            ctx.interaction.attack(player, target)
            PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
            return true
        }
        return false
    }

    enum class ArmorEnchantmentMode {
        None,
        PPPP,
        PPBP,
    }

}
