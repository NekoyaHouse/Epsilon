package com.github.epsilon.modules.impl.movement.elytrafly

import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.TravelEvent
import com.github.epsilon.managers.TimerManager
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.safety.runSafe
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class NCPElytraFlightMode(elytraFly: ElytraFly) : ElytraFlightMode(elytraFly) {

    private var hasElytra = false
    private var hasTouchedGround = false
    private var shouldJump = false

    /**
     * 是否由本模式设置了临时变速，只有设置过才负责还原。
     */
    private var timerBoosted = false
    private val instantFlyTimer = TimerUtils()
    private val strictTimer = TimerUtils()

    override fun onEnable() {
        hasElytra = false
        hasTouchedGround = false
        shouldJump = false
        instantFlyTimer.setMs(TIMER_ARMED_MS)
        strictTimer.setMs(TIMER_ARMED_MS)
    }

    override fun onDisable() {
        hasElytra = false
        hasTouchedGround = false
        shouldJump = false
        releaseTimerBoost()
    }

    override fun onKeyboardInput(event: KeyboardInputEvent) {
        if (shouldJump) {
            event.isJump = true
            shouldJump = false
        }
    }

    override fun onPlayerTick() {
        runSafe {
            if (player.onGround()) {
                hasTouchedGround = true
            }
            val elytra = InvUtils.find(Items.ELYTRA)
            hasElytra = canGlide(elytra.found())

            // 仅释放本模式设置的临时变速。
            if (strictTimer.passedMillise(1500L) && !strictTimer.passedMillise(2000L) ||
                timerBoosted && player.isFallFlying && TimerManager.get() == TIMER_BOOST
            ) {
                releaseTimerBoost()
            }

            if (player.isFallFlying) return@runSafe

            if (hasTouchedGround && elytraFly.ncpTimer && !player.onGround()) {
                TimerManager.set(TIMER_BOOST)
                timerBoosted = true
            }

            if (elytraFly.armored) {
                updateArmoredGlide(elytra)
                return@runSafe
            }

            if (player.onGround() ||
                !elytraFly.ncpInstantFly ||
                player.deltaMovement.y >= 0.0
            ) {
                return@runSafe
            }
            if (!instantFlyTimer.passedMillise((1000.0 * elytraFly.ncpTimeout).toLong())) return@runSafe
            instantFlyTimer.reset()

            if (canStartFallFlying()) {
                startFallFlying()
            }
            hasTouchedGround = false
            strictTimer.reset()
        }
    }

    /**
     * 未滑翔时临时换上鞘翅触发滑翔，再恢复胸甲；已装备鞘翅时直接起飞。
     */
    private fun updateArmoredGlide(elytra: FindItemResult) {
        runSafe {
            if (!canGlide(elytra.found())) return@runSafe

            if (player.onGround()) {
                shouldJump = true
                return@runSafe
            }
            if (!canStartFallFlying()) return@runSafe

            if (!elytra.found()) {
                if (startFallFlying()) {
                    shouldJump = true
                }
                return@runSafe
            }
            // 容器界面下的槽位下标不属于 InventoryMenu，此时不做装备操作，避免把物品点进别的容器。
            if (player.containerMenu != player.inventoryMenu) return@runSafe

            val slot = toContainerSlot(elytra.slot)
            swapArmor(slot)
            if (startFallFlying()) {
                shouldJump = true
            }
            swapArmor(slot)
        }
    }

    private fun toContainerSlot(inventorySlot: Int): Int {
        return if (inventorySlot < 9) inventorySlot + 36 else inventorySlot
    }

    override fun onTravel(event: TravelEvent) {
        runSafe {
            if (!hasElytra || !player.isFallFlying) return@runSafe

            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val lookVec = Entity.calculateViewVector(-elytraFly.ncpUpPitch.toFloat(), player.getYRot(partialTick))
            val lookDist = sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z)

            val movement = player.deltaMovement
            var x = movement.x
            var y = movement.y
            var z = movement.z
            val motionDist = sqrt(x * x + z * z)

            // 读取真实跳跃键，避免起飞时注入的 jump 影响移动。
            val jumping = mc.options.keyJump.isDown

            if (mc.options.keyShift.isDown) {
                y = -elytraFly.ncpDownSpeed
            } else if (!jumping) {
                y = HOVER_Y * elytraFly.ncpDownFactor
            }

            if (jumping) {
                if (motionDist > elytraFly.ncpUpFactor / elytraFly.ncpUpFactorSetting.max) {
                    val rawUpSpeed = motionDist * 0.01325
                    y += rawUpSpeed * 3.2
                    x -= lookVec.x * rawUpSpeed / lookDist
                    z -= lookVec.z * rawUpSpeed / lookDist
                } else {
                    val dir = directionSpeed(elytraFly.ncpSpeed)
                    x = dir[0]
                    z = dir[1]
                }
            }

            if (lookDist > 0.0) {
                x += (lookVec.x / lookDist * motionDist - x) * 0.1
                z += (lookVec.z / lookDist * motionDist - z) * 0.1
            }

            if (!jumping) {
                val dir = directionSpeed(elytraFly.ncpSpeed)
                x = dir[0]
                z = dir[1]
            }

            if (!elytraFly.ncpNoDrag) {
                y *= 0.9900000095367432
                x *= 0.9800000190734863
                z *= 0.9900000095367432
            }

            val finalDist = sqrt(x * x + z * z)
            if (elytraFly.ncpSpeedLimit && finalDist > elytraFly.ncpMaxSpeed) {
                x = x * elytraFly.ncpMaxSpeed / finalDist
                z = z * elytraFly.ncpMaxSpeed / finalDist
            }

            player.deltaMovement = Vec3(x, y, z)
            event.cancel()
            player.move(MoverType.SELF, player.deltaMovement)
        }
    }

    private fun releaseTimerBoost() {
        if (!timerBoosted) return
        timerBoosted = false
        TimerManager.reset()
    }

    /**
     * 按真实移动按键与插值朝向计算水平速度。
     */
    context(nn: NonNullContext)
    private fun directionSpeed(speed: Double): DoubleArray {
        var forward = (if (mc.options.keyUp.isDown) 1.0f else 0.0f) - (if (mc.options.keyDown.isDown) 1.0f else 0.0f)
        var side = (if (mc.options.keyLeft.isDown) 1.0f else 0.0f) - (if (mc.options.keyRight.isDown) 1.0f else 0.0f)
        var yaw = player.getYRot(mc.deltaTracker.getGameTimeDeltaPartialTick(true))

        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += if (forward > 0.0f) -45.0f else 45.0f
            } else if (side < 0.0f) {
                yaw += if (forward > 0.0f) 45.0f else -45.0f
            }
            side = 0.0f
            forward = if (forward > 0.0f) 1.0f else -1.0f
        }

        val sin = sin(((yaw + 90.0f).toDouble()).toRadians())
        val cos = cos(((yaw + 90.0f).toDouble()).toRadians())
        return doubleArrayOf(
            forward * speed * cos + side * speed * sin,
            forward * speed * sin - side * speed * cos
        )
    }

    private companion object {

        /**
         * 起飞时的 tick 长度倍率，0.3 对应约 3.33 倍速。
         */
        private const val TIMER_BOOST = 0.3f

        /**
         * 悬停时的微小下降速度，乘以 DownFactor。
         */
        private const val HOVER_Y = -0.00000000000003

        /**
         * 预置计时器经过时间，使首次起飞无需等待。
         */
        private const val TIMER_ARMED_MS = 917813L

    }

}
