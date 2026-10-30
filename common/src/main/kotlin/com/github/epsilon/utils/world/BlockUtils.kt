package com.github.epsilon.utils.world

import com.github.epsilon.utils.NonNullContext
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
     * @param pos 目标位置
     * @return 判断结果
     */
    fun canPlaceAt(pos: BlockPos): Boolean {
        val nn = NonNullContext.current ?: return false
        if (!nn.world.getBlockState(pos).canBeReplaced()) return false
        val except: Entity? = null
        return nn.world.getEntities(
            except,
            AABB(pos)
        ) { entity -> !(entity is ItemEntity || entity is ExperienceOrb || entity is ThrownExperienceBottle || entity is Arrow) }
            .isEmpty()
    }

    fun getPlacePos(hit: BlockHitResult, item: ItemStack): BlockPos {
        val nn = NonNullContext.current ?: return hit.blockPos.relative(hit.direction)
        val state = nn.world.getBlockState(hit.blockPos)
        if (!state.isAir && canBeReplacedWith(state, item)) {
            return hit.blockPos
        }
        return hit.blockPos.relative(hit.direction)
    }

    fun isFaceVisible(pos: BlockPos, face: Direction): Boolean {
        val nn = NonNullContext.current ?: return false
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

    fun isSolidBlock(pos: BlockPos): Boolean {
        val nn = NonNullContext.current ?: return false
        val state = nn.world.getBlockState(pos)
        if (state.isAir) return false
        if (canBeReplacedWith(state, ItemStack.EMPTY)) return false
        return !state.getCollisionShape(nn.world, pos).isEmpty()
    }

    fun hasBlockingEntity(box: AABB): Boolean {
        val nn = NonNullContext.current ?: return false
        val except: Entity? = null
        for (entity in nn.world.getEntities(except, box)) {
            if (!entity.isAlive) continue
            if (entity is ItemEntity || entity is ExperienceOrb) continue
            if (entity is EndCrystal) continue
            return true
        }
        return false
    }

    fun isWithinRange(point: Vec3): Boolean {
        val nn = NonNullContext.current ?: return false
        val range = nn.player.blockInteractionRange()
        return point.distanceToSqr(nn.player.getEyePosition()) <= range * range
    }

    fun getVisibleFace(pos: BlockPos): Direction {
        val nn = NonNullContext.current ?: return Direction.UP
        return Direction.getApproximateNearest(nn.player.position().subtract(Vec3.atCenterOf(pos)))
    }

}
