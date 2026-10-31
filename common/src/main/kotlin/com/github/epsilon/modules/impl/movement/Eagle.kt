package com.github.epsilon.modules.impl.movement

import com.github.epsilon.utils.*
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.math.MathUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CarvedPumpkinBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.PumpkinBlock
import net.minecraft.world.level.block.SlimeBlock
import net.minecraft.world.level.block.TntBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object Eagle : Module("Eagle", Category.MOVEMENT) {

    private val minDelay by intSetting("Min Delay", 2, 0, 10, 1)
    private val maxDelay by intSetting("Max Delay", 3, 0, 10, 1)
    private val directionCheck by boolSetting("Direction Check", true)
    private val jumpCheck by boolSetting("Jump Check", true)
    private val pitchCheck by boolSetting("Pitch Check", true)
    private val blocksOnly by boolSetting("Blocks Only", true)
    private val sneakingOnly by boolSetting("Sneaking Only", false)

    private var sneakDelay = 0

    init {
        nonNullListener<PlayerTickEvent.Pre>(EventPriority.LOWEST) {
            if (sneakDelay > 0) {
                sneakDelay--
            }

            if (sneakDelay == 0 && isOverEdge()) {
                sneakDelay = MathUtils.getRandom(minDelay, maxDelay)
            }
        }

        nonNullListener<KeyboardInputEvent>(EventPriority.LOWEST) { event ->
            if (mc.gui.screen() != null) return@nonNullListener

            val physicallySneaking = KeybindUtils.isPressed(mc.options.keyShift)
            if (sneakingOnly && physicallySneaking && shouldSneak()) {
                event.isSneak = false
            }

            if (!event.isSneak && shouldSneak() && (sneakDelay > 0 || isOverEdge())) {
                event.isSneak = true
            }
        }
    }

    override fun getInfo(): String {
        return if (minDelay == maxDelay) {
            minDelay.toString()
        } else {
            minDelay.toString() + "-" + maxDelay.toString()
        }
    }

    override fun onDisable() {
        sneakDelay = 0
    }

    override fun resetCustomState() {
        sneakDelay = 0
    }

    context(nn: NonNullContext)
    private fun shouldSneak(): Boolean {
        if (directionCheck && mc.options.keyUp.isDown) return false
        if (jumpCheck && mc.options.keyJump.isDown) return false
        if (pitchCheck && player.xRot < 69.0f) return false
        if (sneakingOnly && !KeybindUtils.isPressed(mc.options.keyShift)) return false
        return (!blocksOnly || isHoldingBlock()) && player.onGround()
    }

    fun isOverEdge(): Boolean = runSafeOrFalse {
        val predictedMovement = predictMovement()
        val movement = player.deltaMovement.add(predictedMovement.x, 0.0, predictedMovement.z)
        level.noCollision(player, player.boundingBox.move(movement.x, -1.0, movement.z))
    }

    context(nn: NonNullContext)
    private fun predictMovement(): Vec3 {
        var strafeInput = getInput(mc.options.keyLeft.isDown, mc.options.keyRight.isDown) * 0.98f
        var forwardInput = getInput(mc.options.keyUp.isDown, mc.options.keyDown.isDown) * 0.98f
        var inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput
        if (inputMagnitude < 1.0E-4f) return Vec3.ZERO

        inputMagnitude = Math.max(Mth.sqrt(inputMagnitude), 1.0f)
        val inputScale = getAllowedHorizontalDistance() / inputMagnitude
        strafeInput *= inputScale
        forwardInput *= inputScale

        val sinYaw = Mth.sin((player.yRot * (Math.PI / 180.0).toFloat()).toDouble())
        val cosYaw = Mth.cos((player.yRot * (Math.PI / 180.0).toFloat()).toDouble())
        return Vec3(
            (strafeInput * cosYaw - forwardInput * sinYaw).toDouble(),
            0.0,
            (forwardInput * cosYaw + strafeInput * sinYaw).toDouble()
        )
    }

    context(nn: NonNullContext)
    private fun getAllowedHorizontalDistance(): Float {
        val posBelow = player.blockPosBelowThatAffectsMyMovement
        val blockFriction = level.getBlockState(posBelow).block.friction
        return player.speed * (0.21600002f / (blockFriction * blockFriction * blockFriction))
    }

    private fun getInput(positive: Boolean, negative: Boolean): Float {
        if (positive == negative) return 0.0f
        return if (positive) 1.0f else -1.0f
    }

    context(nn: NonNullContext)
    private fun isHoldingBlock(): Boolean {
        val stack = player.mainHandItem
        if (stack.isEmpty) return false
        val blockItem = stack.item as? BlockItem ?: return false

        val block = blockItem.block
        val state: BlockState = block.defaultBlockState()
        if (block is EntityBlock || state.getMenuProvider(level, BlockPos.ZERO) != null) return false
        if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false

        return block !is FallingBlock &&
                block !is PumpkinBlock &&
                block !is CarvedPumpkinBlock &&
                block !is SlimeBlock &&
                block !is TntBlock
    }

}
