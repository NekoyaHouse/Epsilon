package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.MoveEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.core.BlockPos
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sign

object SafeWalk : Module("Safe Walk", Category.MOVEMENT) {

    private val motion by doubleSetting("Motion", 1.0, 0.5..1.0, 0.05)
    private val speedMotion by doubleSetting("Speed Motion", 1.0, 0.5..1.5, 0.05)
    private val air by boolSetting("Air", false)
    private val directionCheck by boolSetting("Direction Check", true)
    private val pitchCheck by boolSetting("Pitch Check", true)
    private val requirePress by boolSetting("Require Press", false)
    private val blocksOnly by boolSetting("Blocks Only", true)

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (!player.onGround() || !isMovementInputPressed()) return@safeEventHandler

            val motionX = player.deltaMovement.x
            val motionZ = player.deltaMovement.z
            if (!canSafeWalk(motionX, motionZ)) return@safeEventHandler

            val multiplier = if (player.hasEffect(MobEffects.SPEED)) speedMotion else motion
            if (multiplier == 1.0) return@safeEventHandler

            player.deltaMovement = Vec3(
                motionX * multiplier,
                player.deltaMovement.y,
                motionZ * multiplier
            )
        }

        safeEventHandler<MoveEvent>(EventPriority.LOWEST) { event ->
            if (!canSafeWalk(event.x, event.z)) return@safeEventHandler

            var movementX = event.x
            var movementZ = event.z
            val stepX = sign(movementX) * 0.05
            val stepZ = sign(movementZ) * 0.05
            val maxDownStep = player.maxUpStep()

            while (movementX != 0.0 && canFallAtLeast(movementX, 0.0, maxDownStep.toDouble())) {
                if (abs(movementX) <= 0.05) {
                    movementX = 0.0
                    break
                }
                movementX -= stepX
            }

            while (movementZ != 0.0 && canFallAtLeast(0.0, movementZ, maxDownStep.toDouble())) {
                if (abs(movementZ) <= 0.05) {
                    movementZ = 0.0
                    break
                }
                movementZ -= stepZ
            }

            while (movementX != 0.0 && movementZ != 0.0 && canFallAtLeast(
                    movementX,
                    movementZ,
                    maxDownStep.toDouble()
                )
            ) {
                if (abs(movementX) <= 0.05) {
                    movementX = 0.0
                } else {
                    movementX -= stepX
                }

                if (abs(movementZ) <= 0.05) {
                    movementZ = 0.0
                } else {
                    movementZ -= stepZ
                }
            }

            event.x = movementX
            event.z = movementZ
            event.cancel()
        }
    }

    context(nn: NonNullContext)
    private fun canSafeWalk(movementX: Double, movementZ: Double): Boolean {
        if (Scaffold.isEnabled) return false
        if (directionCheck && mc.options.keyUp.isDown) return false
        if (pitchCheck && nn.player.xRot < 69.0f) return false
        if (blocksOnly && !isHoldingBlock()) return false
        if (requirePress && !mc.options.keyUse.isDown) return false

        return nn.player.onGround() && canMove(movementX, movementZ, -1.0) ||
                air && canMove(movementX, movementZ, -2.0)
    }

    context(nn: NonNullContext)
    private fun canMove(movementX: Double, movementZ: Double, movementY: Double): Boolean {
        return nn.level.noCollision(
            nn.player,
            nn.player.boundingBox.move(movementX, movementY, movementZ)
        )
    }

    context(nn: NonNullContext)
    private fun canFallAtLeast(movementX: Double, movementZ: Double, minHeight: Double): Boolean {
        val boundingBox = nn.player.boundingBox
        return nn.level.noCollision(
            nn.player,
            AABB(
                boundingBox.minX + 1.0E-7 + movementX,
                boundingBox.minY - minHeight - 1.0E-7,
                boundingBox.minZ + 1.0E-7 + movementZ,
                boundingBox.maxX - 1.0E-7 + movementX,
                boundingBox.minY,
                boundingBox.maxZ - 1.0E-7 + movementZ
            )
        )
    }

    private fun isMovementInputPressed(): Boolean {
        return mc.options.keyUp.isDown != mc.options.keyDown.isDown || mc.options.keyLeft.isDown != mc.options.keyRight.isDown
    }

    context(nn: NonNullContext)
    private fun isHoldingBlock(): Boolean {
        val stack = nn.player.mainHandItem
        if (stack.isEmpty) return false
        val blockItem = stack.item as? BlockItem ?: return false

        val block = blockItem.block
        val state: BlockState = block.defaultBlockState()
        if (block is EntityBlock || state.getMenuProvider(nn.level, BlockPos.ZERO) != null) return false
        if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false

        return block !is FallingBlock &&
                block !is PumpkinBlock &&
                block !is CarvedPumpkinBlock &&
                block !is SlimeBlock &&
                block !is TntBlock
    }

}
