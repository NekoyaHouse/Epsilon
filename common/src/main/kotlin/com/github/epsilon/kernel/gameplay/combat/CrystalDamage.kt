package com.github.epsilon.kernel.gameplay.combat

import com.github.epsilon.kernel.gameplay.inventory.EnchantmentLookup
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.managers.ExtrapolationManager
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.BlocksAttacks
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.acos
import kotlin.math.floor

object CrystalDamage {
    val armorSlots =
        listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY)

    data class Snapshot(
        val id: Int, val position: Vec3, val box: AABB, val health: Double,
        val armor: Float, val toughness: Float, val resistance: Int, val protection: Int,
        val immune: Boolean, val creative: Boolean, val difficulty: Int,
        val minArmor: Double, val maxArmor: Double,
        val blocking: BlocksAttacks?, val facing: Vec3, val explosionType: Holder<DamageType>,
    ) {
        fun blocksExplosion(center: Vec3): Boolean {
            val blocker = blocking ?: return false
            val source = DamageSource(explosionType, center)
            if (blocker.bypassedBy().map { it.contains(explosionType) }.orElse(false)) return false
            val direction = center.subtract(position).multiply(1.0, 0.0, 1.0).normalize()
            val angle = acos(direction.dot(facing).coerceIn(-1.0, 1.0))
            return blocker.resolveBlockedDamage(source, 1.0f, angle) > 0.0f
        }
    }

    fun interface Visibility {
        fun visible(from: Vec3, to: Vec3, base: BlockPos?, ignored: Set<BlockPos>): Boolean
    }

    class WorldSnapshot internal constructor(private val boxes: Map<Long, List<AABB>>) : Visibility {
        // 在线程内构造独立形状，避免后台计算触碰世界或共享 VoxelShape 的懒加载缓存。
        private val shapes by lazy {
            boxes.mapValues { (_, boxes) ->
                boxes.fold(Shapes.empty()) { shape, box ->
                    Shapes.or(
                        shape,
                        Shapes.create(box)
                    )
                }
            }
        }

        override fun visible(from: Vec3, to: Vec3, base: BlockPos?, ignored: Set<BlockPos>): Boolean =
            trace(from, to) { pos ->
                when {
                    pos in ignored -> Shapes.empty()
                    pos == base -> Shapes.block()
                    else -> shapes[pos.asLong()] ?: Shapes.empty()
                }
            }
    }

    fun NonNullContext.snapshot(
        entity: LivingEntity,
        predictionTicks: Int = 0,
        assumeBestArmor: Boolean = false
    ): Snapshot {
        val box = if (predictionTicks > 0 && entity is AbstractClientPlayer) {
            ExtrapolationManager.extrapolate(entity, predictionTicks, 2) ?: entity.boundingBox
        } else entity.boundingBox
        var protection = 0
        val durability = ArrayList<Double>()
        for (slot in armorSlots) {
            val stack = entity.getItemBySlot(slot)
            if (stack.isEmpty) continue
            val general = EnchantmentLookup.getEnchantmentLevel(stack, Enchantments.PROTECTION)
            val blast = EnchantmentLookup.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION)
            protection += if (assumeBestArmor && general == 0 && blast == 0) 4 else general + blast * 2
            if (stack.maxDamage > 0) durability.add((stack.maxDamage - stack.damageValue) * 100.0 / stack.maxDamage)
        }
        return Snapshot(
            entity.id,
            entity.position(),
            box,
            (entity.health + entity.absorptionAmount).toDouble(),
            entity.armorValue.toFloat(),
            entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat(),
            entity.getEffect(MobEffects.RESISTANCE)?.amplifier ?: -1,
            protection,
            entity is Player && (entity.abilities.invulnerable || entity.isSpectator),
            entity is Player && entity.isCreative,
            if (entity is Player) level.difficulty.id else 2,
            durability.minOrNull() ?: Double.NaN,
            durability.maxOrNull() ?: Double.NaN,
            entity.itemBlockingWith?.get(DataComponents.BLOCKS_ATTACKS),
            Vec3.directionFromRotation(0.0f, entity.yHeadRot),
            level.damageSources().explosion(player, player).typeHolder(),
        )
    }

    fun NonNullContext.captureWorld(bounds: AABB, ignoreTerrain: Boolean): WorldSnapshot {
        val boxes = HashMap<Long, List<AABB>>()
        for (pos in BlockPos.betweenClosed(
            BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ).offset(-1, -1, -1),
            BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ).offset(1, 1, 1)
        )) {
            val state = level.getBlockState(pos)
            if (state.isAir || ignoreTerrain && state.block.explosionResistance < 600.0f) continue
            val shape = state.getCollisionShape(level, pos)
            if (!shape.isEmpty) boxes[pos.asLong()] = shape.toAabbs().toList()
        }
        return WorldSnapshot(boxes)
    }

    fun NonNullContext.damage(
        snapshot: Snapshot,
        center: Vec3,
        ignoreTerrain: Boolean = false,
        base: BlockPos? = null,
        ignored: Set<BlockPos> = emptySet()
    ): Double =
        calculate(snapshot, center, { from, to, support, excluded ->
            trace(from, to) { pos ->
                val state = level.getBlockState(pos)
                when {
                    pos in excluded -> Shapes.empty()
                    pos == support -> Shapes.block()
                    ignoreTerrain && state.block.explosionResistance < 600.0f -> Shapes.empty()
                    else -> state.getCollisionShape(level, pos)
                }
            }
        }, base, ignored)

    fun calculate(
        snapshot: Snapshot,
        center: Vec3,
        visibility: Visibility,
        base: BlockPos? = null,
        ignored: Set<BlockPos> = emptySet()
    ): Double {
        if (snapshot.immune) return 0.0
        val distance = snapshot.position.distanceTo(center)
        if (distance > 12.0) return 0.0
        val exposure = exposure(snapshot.box, center, visibility, base, ignored)
        val damage =
            CrystalDamageRules.scaleForDifficulty(CrystalDamageRules.rawDamage(distance, exposure), snapshot.difficulty)
        return CrystalDamageRules.reduceDamage(
            damage,
            snapshot.armor,
            snapshot.toughness,
            snapshot.resistance,
            snapshot.protection
        ).toDouble()
    }

    private fun exposure(
        box: AABB,
        center: Vec3,
        visibility: Visibility,
        base: BlockPos?,
        ignored: Set<BlockPos>
    ): Double {
        val stepX = 1.0 / (box.xsize * 2.0 + 1.0)
        val stepY = 1.0 / (box.ysize * 2.0 + 1.0)
        val stepZ = 1.0 / (box.zsize * 2.0 + 1.0)
        val offsetX = (1.0 - floor(1.0 / stepX) * stepX) * 0.5
        val offsetZ = (1.0 - floor(1.0 / stepZ) * stepZ) * 0.5
        var visible = 0
        var samples = 0
        var x = 0.0
        while (x <= 1.0) {
            var y = 0.0
            while (y <= 1.0) {
                var z = 0.0
                while (z <= 1.0) {
                    val from = Vec3(
                        box.minX + box.xsize * x + offsetX,
                        box.minY + box.ysize * y,
                        box.minZ + box.zsize * z + offsetZ
                    )
                    if (visibility.visible(from, center, base, ignored)) visible++
                    samples++
                    z += stepZ
                }
                y += stepY
            }
            x += stepX
        }
        return visible.toDouble() / samples
    }

    private fun trace(from: Vec3, to: Vec3, shape: (BlockPos) -> VoxelShape): Boolean =
        !BlockGetter.traverseBlocks(from, to, Unit, { _, pos ->
            if (shape(pos).clip(from, to, pos) != null) true else null
        }, { false })
}
