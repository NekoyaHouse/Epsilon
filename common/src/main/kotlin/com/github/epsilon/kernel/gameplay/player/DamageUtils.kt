package com.github.epsilon.kernel.gameplay.player

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.sqrt

internal object DamageUtils {
    val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    fun rawDamage(distance: Double, exposure: Double): Double {
        if (distance > 12.0) return 0.0
        val impact = (1.0 - distance / 12.0) * exposure
        return (impact * impact + impact) * 42.0 + 1.0
    }

    fun damage(level: ClientLevel, center: Vec3, target: LivingEntity, bounds: AABB = target.boundingBox, ignored: Set<BlockPos> = emptySet(), solid: BlockPos? = null, bestArmor: Boolean = false): Double {
        if (target is Player && (target.isCreative || target.isSpectator)) return 0.0
        val bottom = Vec3((bounds.minX + bounds.maxX) / 2.0, bounds.minY, (bounds.minZ + bounds.maxZ) / 2.0)
        val distance = sqrt(bottom.distanceToSqr(center))
        if (distance > 12.0) return 0.0
        var amount = rawDamage(distance, exposure(level, center, target, bounds, ignored, solid))
        if (target is Player) amount = when (level.difficulty.id) {
            0 -> 0.0
            1 -> minOf(amount / 2.0 + 1.0, amount)
            3 -> amount * 1.5
            else -> amount
        }
        val source = level.damageSources().explosion(null as Explosion?)
        var reduced = CombatRules.getDamageAfterAbsorb(target, amount.toFloat(), source,
            target.armorValue.toFloat(),
            target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat())
        val resistance = target.getEffect(MobEffects.RESISTANCE)
        if (resistance != null || bestArmor) {
            val amplifier = resistance?.amplifier ?: 1
            reduced *= maxOf(0, 25 - (amplifier + 1) * 5) / 25f
        }
        var protection = 0
        for (slot in armorSlots) for (entry in target.getItemBySlot(slot).enchantments.entrySet()) {
            if (entry.key.`is`(Enchantments.PROTECTION)) protection += entry.intValue
            if (entry.key.`is`(Enchantments.BLAST_PROTECTION)) protection += entry.intValue * 2
        }
        if (bestArmor) protection = ((1 + armorSlots.count { !target.getItemBySlot(it).isEmpty } - if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty) 1 else 0) * 5).coerceAtMost(20)
        reduced = CombatRules.getDamageAfterMagicAbsorb(reduced, protection.toFloat()).coerceAtLeast(0f)
        val blocking = target.itemBlockingWith?.get(DataComponents.BLOCKS_ATTACKS)
        if (blocking != null) {
            val direction = center.subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize()
            val view = Entity.calculateViewVector(0f, target.yHeadRot)
            val angle = acos(direction.dot(view).coerceIn(-1.0, 1.0))
            reduced -= blocking.resolveBlockedDamage(source, reduced, angle)
        }
        return reduced.coerceAtLeast(0f).toDouble()
    }

    private fun exposure(level: ClientLevel, center: Vec3, entity: LivingEntity, bb: AABB, ignored: Set<BlockPos>, solid: BlockPos?): Double {
        val xs = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0)
        val ys = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0)
        val zs = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0)
        val ox = (1.0 - floor(1.0 / xs) * xs) / 2.0
        val oz = (1.0 - floor(1.0 / zs) * zs) / 2.0
        var hits = 0; var count = 0
        var x = 0.0
        while (x <= 1.0) {
            var y = 0.0
            while (y <= 1.0) {
                var z = 0.0
                while (z <= 1.0) {
                    val from = Vec3(bb.minX + x * (bb.maxX - bb.minX) + ox,
                        bb.minY + y * (bb.maxY - bb.minY), bb.minZ + z * (bb.maxZ - bb.minZ) + oz)
                    val context = object : ClipContext(from, center, Block.COLLIDER, Fluid.NONE, entity) {
                        override fun getBlockShape(state: BlockState, getter: BlockGetter, pos: BlockPos): VoxelShape {
                            if (pos == solid) return Shapes.block()
                            if (pos in ignored || state.block.explosionResistance < 600f) return Shapes.empty()
                            return super.getBlockShape(state, getter, pos)
                        }
                    }
                    if (level.clip(context).type == HitResult.Type.MISS) hits++
                    count++; z += zs
                }
                y += ys
            }
            x += xs
        }
        return if (count == 0) 0.0 else hits.toDouble() / count
    }
}
