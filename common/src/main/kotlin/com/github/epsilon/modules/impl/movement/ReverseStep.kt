package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.player
import com.github.epsilon.utils.level
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.phys.Vec3

object ReverseStep : Module("Reverse Step", Category.MOVEMENT) {

    private val fallSpeed by doubleSetting("Fall Speed", 3.0, 0.0, 10.0, 1.0)
    private val fallDistance by doubleSetting("Fall Distance", 3.0, 0.0, 10.0, 1.0)
    private val vehicles by boolSetting("Vehicles", false)

    init {
        nonNullListener<PlayerTickEvent.Post> {
            if (Scaffold.isEnabled) return@nonNullListener

            val vehicle = player.vehicle
            if (vehicle != null && vehicles) {
                if (canSnap(vehicle)) {
                    vehicle.deltaMovement = Vec3(vehicle.deltaMovement.x, -fallSpeed, vehicle.deltaMovement.z)
                }
            } else {
                if (player.isSuppressingSlidingDownLadder || player.zza == 0.0f && player.xxa == 0.0f) {
                    return@nonNullListener
                }
                if (!isOnBed() && canSnap(player)) {
                    player.deltaMovement = Vec3(player.deltaMovement.x, -fallSpeed, player.deltaMovement.z)
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun canSnap(entity: Entity): Boolean {
        if (!entity.onGround() || entity.isUnderWater || entity.isInLava || mc.options.keyJump.isDown || entity.noPhysics) {
            return false
        }
        return !level.noCollision(
            entity,
            entity.boundingBox.move(0.0, (-(fallDistance + 0.01)).toFloat().toDouble(), 0.0)
        )
    }

    context(nn: NonNullContext)
    private fun isOnBed(): Boolean {
        val blockPos = player.blockPosition().mutable()

        if (check(blockPos, 0, 0)) return true

        val xa = player.x - blockPos.x
        val za = player.z - blockPos.z

        if (xa >= 0 && xa <= 0.3 && check(blockPos, -1, 0)) return true
        if (xa >= 0.7 && check(blockPos, 1, 0)) return true
        if (za >= 0 && za <= 0.3 && check(blockPos, 0, -1)) return true
        if (za >= 0.7 && check(blockPos, 0, 1)) return true

        if (xa >= 0 && xa <= 0.3 && za >= 0 && za <= 0.3 && check(blockPos, -1, -1)) return true
        if (xa >= 0 && xa <= 0.3 && za >= 0.7 && check(blockPos, -1, 1)) return true
        if (xa >= 0.7 && za >= 0 && za <= 0.3 && check(blockPos, 1, -1)) return true
        return xa >= 0.7 && za >= 0.7 && check(blockPos, 1, 1)
    }

    context(nn: NonNullContext)
    private fun check(blockPos: BlockPos.MutableBlockPos, x: Int, z: Int): Boolean {
        blockPos.move(x, 0, z)
        val isBed = level.getBlockState(blockPos).block is BedBlock
        blockPos.move(-x, 0, -z)
        return isBed
    }

}
