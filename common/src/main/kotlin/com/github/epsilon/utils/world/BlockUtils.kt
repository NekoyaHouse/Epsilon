package com.github.epsilon.utils.world

import com.github.epsilon.utils.safety.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object BlockUtils {

    fun canBeReplacedWith(state: BlockState, item: ItemStack): Boolean {
        return state.canBeReplaced() && (item.isEmpty || !item.`is`(state.block.asItem()))
    }

    /**
     * 判断本地玩家是否可在指定位置放置方块。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     *
     * @param pos 目标位置
     * @return 判断结果
     */
    context(nn: NonNullContext)
    fun canPlaceAt(pos: BlockPos): Boolean {
        if (!nn.level.getBlockState(pos).canBeReplaced()) return false
        val except: Entity? = null
        return nn.level.getEntities(
            except,
            AABB(pos)
        ) { entity -> !(entity is ItemEntity || entity is ExperienceOrb || entity is ThrownExperienceBottle || entity is Arrow) }
            .isEmpty()
    }

    /**
     * 计算方块交互命中的实际放置位置。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     */
    context(nn: NonNullContext)
    fun getPlacePos(hit: BlockHitResult, item: ItemStack): BlockPos {
        val state = nn.level.getBlockState(hit.blockPos)
        if (!state.isAir && canBeReplacedWith(state, item)) {
            return hit.blockPos
        }
        return hit.blockPos.relative(hit.direction)
    }

    /**
     * 判断方块的某一面是否朝向本地玩家可见。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     */
    context(nn: NonNullContext)
    fun isFaceVisible(pos: BlockPos, face: Direction): Boolean {
        val eyePos = nn.player.getEyePosition()
        val box = AABB(pos)
        return when (face) {
            Direction.NORTH -> eyePos.z > box.minZ
            Direction.SOUTH -> eyePos.z < box.maxZ
            Direction.EAST -> eyePos.x < box.maxX
            Direction.WEST -> eyePos.x > box.minX
            Direction.UP -> eyePos.y < box.maxY
            Direction.DOWN -> eyePos.y > box.minY
        }
    }

    /**
     * 判断指定位置是否为有碰撞体积的实心方块。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     */
    context(nn: NonNullContext)
    fun isSolidBlock(pos: BlockPos): Boolean {
        val state = nn.level.getBlockState(pos)
        if (state.isAir) return false
        if (canBeReplacedWith(state, ItemStack.EMPTY)) return false
        return !state.getCollisionShape(nn.level, pos).isEmpty()
    }

    /**
     * 判断包围盒内是否存在会阻挡放置的实体。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     */
    context(nn: NonNullContext)
    fun hasBlockingEntity(box: AABB): Boolean {
        val except: Entity? = null
        for (entity in nn.level.getEntities(except, box)) {
            if (!entity.isAlive) continue
            if (entity is ItemEntity || entity is ExperienceOrb) continue
            if (entity is EndCrystal) continue
            return true
        }
        return false
    }

    /**
     * 判断目标点是否处于本地玩家的方块交互距离内。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     */
    context(nn: NonNullContext)
    fun isWithinRange(point: Vec3): Boolean {
        val range = nn.player.blockInteractionRange()
        return point.distanceToSqr(nn.player.getEyePosition()) <= range * range
    }

    /**
     * 计算方块朝向玩家一侧的大致方向。
     *
     * 调用方需在 [NonNullContext] 作用域内调用。
     */
    context(nn: NonNullContext)
    fun getVisibleFace(pos: BlockPos): Direction {
        return Direction.getApproximateNearest(nn.player.position().subtract(Vec3.atCenterOf(pos)))
    }

}
