package com.github.epsilon.modules.impl.movement.elytrafly

import com.github.epsilon.Constants
import com.github.epsilon.events.impl.FallFlyingEvent
import com.github.epsilon.events.impl.FireworkRotationEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.TravelEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class ControlElytraFlightMode(elytraFly: ElytraFly) : ElytraFlightMode(elytraFly) {

    private var hasFirstFirework = false
    private var shouldJump = false
    private var pendingFirework = false
    private var eatGlideElytraSlot = -1
    private var eatCompleted = false
    private var eatClimbed = false
    private var climbTicks = 0
    private val timer = TimerUtils()

    override fun onEnable() {
        hasFirstFirework = false
        shouldJump = false
        pendingFirework = false
        timer.setMs(917813L)
        eatCompleted = false
        eatClimbed = false
        climbTicks = 0
    }

    override fun onDisable() {
        shouldJump = false
        pendingFirework = false
        eatCompleted = false
        eatClimbed = false
        climbTicks = 0
        releaseEatGlide()
    }

    // 控制模式有意跳过基类的自动修复：进食滑翔期间的槽位由本模式自行管理。
    override fun handleUnbreaking() {
    }

    override fun onPlayerTick() {
        updateEatClimb()
        redirectRotation()
        updateEatKeyRelease()
        updateControl()
    }

    override fun onRightClick() {
        releaseUseKeyAfterEating()
    }

    override fun onTravel(event: TravelEvent) {
        runSafe {
            val avoidCeilingLift = shouldAvoidCeilingLift()

            if (avoidCeilingLift && player.deltaMovement.y > 0.0) {
                player.deltaMovement = player.deltaMovement.multiply(1.0, 0.0, 1.0)
            }

            if (!avoidCeilingLift && !hasMoveInput() && (!elytraFly.useFireworks || hasFirstFirework)) {
                player.deltaMovement = Vec3(0.0, 0.02, 0.0)
            }
        }
    }

    override fun onKeyboardInput(event: KeyboardInputEvent) {
        runSafe {
            if (elytraFly.noSprint) {
                event.isSprint = false
                player.isSprinting = false
                mc.options.keySprint.isDown = false
            }
            if (shouldJump) {
                event.isJump = true
                shouldJump = false
            }
        }
    }

    override fun onFallFlying(event: FallFlyingEvent) {
        event.yaw = calcYaw()
        event.pitch = calcPitch()
    }

    override fun onFireworkUpdate(event: FireworkRotationEvent) {
        event.yaw = calcYaw()
        event.pitch = calcPitch()
    }

    private fun updateControl() {
        runSafe {
            // 疾跑限制起飞与换甲，不阻止烟花恢复动量。
            if (elytraFly.noSprint && player.isSprinting) {
                if (!elytraFly.armored) {
                    useTimedFirework()
                }
                return@runSafe
            }

            val eating = isEating()

            if (pendingFirework && !eating) {
                useTimedFirework()
            }

            val elytra = InvUtils.find(Items.ELYTRA)

            if (shouldEatGlide(eating)) {
                handleEatGlide(elytra)
                return@runSafe
            }
            releaseEatGlide()

            if (!canGlide(elytra.found()) || player.onGround()) {
                shouldJump = true
                hasFirstFirework = false
                pendingFirework = false
                return@runSafe
            }

            if (elytraFly.armored) {
                if (canStartFallFlying()) {
                    jiaFei(elytra.slot)
                }
            } else {
                if (canStartFallFlying() && startFallFlying()) {
                    shouldJump = true
                }
                useTimedFirework()
            }
        }
    }

    private fun useTimedFirework() {
        runSafe {
            if (ElytraCombat.isDrivingFlight() && !ElytraCombat.shouldUseFirework()) {
                ElytraDebug.log(ElytraDebug.SLOT_FIREWORK, "firework", "blocked by ElytraCombat")
                return@runSafe
            }
            if (!elytraFly.useFireworks) return@runSafe
            if (isEating()) {
                if (timer.hasDelayed(elytraFly.boostDelay)) {
                    pendingFirework = true
                }
                return@runSafe
            }
            val lowMomentum = isLowMomentum()
            val waitTicks = if (lowMomentum) {
                minOf(LOW_MOMENTUM_BOOST_INTERVAL_TICKS, elytraFly.boostDelay)
            } else {
                elytraFly.boostDelay
            }
            if (!pendingFirework && !timer.hasDelayed(waitTicks)) {
                return@runSafe
            }
            pendingFirework = false
            if (useFirework()) {
                hasFirstFirework = true
                timer.reset()
                ElytraDebug.log(
                    ElytraDebug.SLOT_FIREWORK, "firework",
                    if (lowMomentum) "boost (low momentum)" else "boost"
                )
            } else {
                ElytraDebug.log(ElytraDebug.SLOT_FIREWORK, "firework", "use failed")
            }
        }
    }

    private fun isLowMomentum(): Boolean = runSafeOrFalse {
        player.isFallFlying && player.deltaMovement.length() < LOW_MOMENTUM_SPEED
    }

    private fun isEating(): Boolean = runSafeOrFalse {
        player.isUsingItem && player.useItem.has(DataComponents.FOOD)
    }

    private fun redirectRotation() {
        runSafe {
            if (ElytraDebug.enabled) {
                ElytraDebug.log(
                    ElytraDebug.SLOT_ROTATION, "rotation.request",
                    "yaw=" + ElytraDebug.fmt(calcYaw().toDouble()) + " pitch=" + ElytraDebug.fmt(calcPitch().toDouble())
                )
            }
            RotationManager.INSTANCE.setRotations(Rot2f(calcYaw(), calcPitch()), 360.0, Priority.Highest)
        }
    }

    private fun calcYaw(): Float = runSafeOrElse(0.0f) {
        val combatInput = ElytraCombat.getControlInput()
        if (combatInput != null) {
            return@runSafeOrElse combatInput.yaw
        }

        var yaw = player.yRot

        val forward = mc.options.keyUp.isDown
        val back = mc.options.keyDown.isDown
        val left = mc.options.keyLeft.isDown
        val right = mc.options.keyRight.isDown

        if (forward && !back) {
            if (left && !right) {
                yaw -= 45f
            } else if (right && !left) {
                yaw += 45f
            }
        } else if (back && !forward) {
            yaw += 180f
            if (left && !right) {
                yaw += 45f
            } else if (right && !left) {
                yaw -= 45f
            }
        } else if (left && !right) {
            yaw -= 90f
        } else if (right && !left) {
            yaw += 90f
        }
        Mth.wrapDegrees(yaw)
    }

    private fun calcPitch(): Float = runSafeOrElse(0.0f) {
        val combatInput = ElytraCombat.getControlInput()
        if (combatInput != null) {
            return@runSafeOrElse applyCeilingPitchGuard(combatInput.pitch)
        }

        var pitch = player.xRot

        val climb = isClimbing()
        val jump = mc.options.keyJump.isDown
        val sneak = mc.options.keyShift.isDown
        val moving = player.isMoving

        if (climb) {
            pitch = EAT_CLIMB_PITCH
        } else if (sneak && jump) {
            pitch = -3f
        } else if (jump) {
            pitch = if (moving) -45f else -90f
        } else if (sneak) {
            pitch = if (moving) 45f else 90f
        } else if (moving) {
            pitch = -1.9f
        }
        applyCeilingPitchGuard(pitch)
    }

    private fun applyCeilingPitchGuard(pitch: Float): Float {
        if (shouldAvoidCeilingLift()) {
            return maxOf(pitch, CEILING_ESCAPE_PITCH)
        }
        return Mth.clamp(pitch, -90f, 90f)
    }

    private fun shouldAvoidCeilingLift(): Boolean = runSafeOrFalse {
        if (!player.isFallFlying) return@runSafeOrFalse false

        val box = player.boundingBox
        // 使用意图抬升量，避免实际升速与头顶保护形成反馈振荡。
        val climb = if (ElytraCombat.getControlInput() != null) {
            ElytraCombat.getCombatIntendedClimb()
        } else {
            player.deltaMovement.y
        }
        val probeDistance = CEILING_PROBE_DISTANCE + maxOf(0.0, climb)
        val ceilingProbe = AABB(
            box.minX + CEILING_PROBE_EPSILON,
            box.maxY - CEILING_PROBE_EPSILON,
            box.minZ + CEILING_PROBE_EPSILON,
            box.maxX - CEILING_PROBE_EPSILON,
            box.maxY + probeDistance,
            box.maxZ - CEILING_PROBE_EPSILON
        )
        !level.noBlockCollision(player, ceilingProbe)
    }

    private fun hasMoveInput(): Boolean = runSafeOrFalse {
        val combatInput = ElytraCombat.getControlInput()
        if (combatInput != null) {
            return@runSafeOrFalse combatInput.hasMoveInput()
        }

        mc.options.keyUp.isDown ||
                mc.options.keyDown.isDown ||
                mc.options.keyLeft.isDown ||
                mc.options.keyRight.isDown ||
                mc.options.keyJump.isDown ||
                mc.options.keyShift.isDown ||
                isClimbing()
    }

    private fun shouldEatGlide(eating: Boolean): Boolean = runSafeOrFalse {
        if (elytraFly.noEat || !elytraFly.armored) return@runSafeOrFalse false
        if (player.onGround() || !eating) return@runSafeOrFalse false
        if (player.ticksUsingItem < EAT_GLIDE_START_TICK) return@runSafeOrFalse false
        // 容器界面下的槽位下标不属于 InventoryMenu，此时不做任何装备操作。
        player.containerMenu == player.inventoryMenu
    }

    private fun updateEatKeyRelease() {
        runSafe {
            if (elytraFly.noEat) {
                eatCompleted = false
                return@runSafe
            }

            if (isEating() && player.useItemRemainingTicks <= EAT_COMPLETE_REMAINING_TICKS) {
                eatCompleted = true
            }

            releaseUseKeyAfterEating()
        }
    }

    private fun releaseUseKeyAfterEating() {
        runSafe {
            if (!eatCompleted || player.isUsingItem) return@runSafe

            mc.options.keyUse.isDown = false
            eatCompleted = false
        }
    }

    private fun updateEatClimb() {
        if (climbTicks > 0) {
            climbTicks--
        }

        runSafe {
            if (!isEating()) {
                eatClimbed = false
                return@runSafe
            }
            if (elytraFly.noEat || !elytraFly.armored) return@runSafe
            if (eatClimbed || player.onGround()) return@runSafe
            if (player.ticksUsingItem < EAT_CLIMB_TICK) return@runSafe

            eatClimbed = true
            climbTicks = EAT_CLIMB_TICKS
        }
    }

    private fun isClimbing(): Boolean {
        return climbTicks > 0
    }

    private fun handleEatGlide(elytra: com.github.epsilon.utils.player.FindItemResult) {
        runSafe {
            if (eatGlideElytraSlot < 0) {
                if (!elytra.found()) return@runSafe
                eatGlideElytraSlot = toContainerSlot(elytra.slot)
                swapArmor(eatGlideElytraSlot)
            }

            if (canStartFallFlying() && startFallFlying()) {
                shouldJump = true
            }
        }
    }

    private fun releaseEatGlide() {
        runSafe {
            val slot = eatGlideElytraSlot
            if (slot < 0) return@runSafe

            if (player.containerMenu != player.inventoryMenu) return@runSafe

            val chestStack = player.containerMenu.getSlot(CHEST_MENU_SLOT).item
            if (!LivingEntity.canGlideUsing(chestStack, EquipmentSlot.CHEST) || !isChestEquippable(player.containerMenu.getSlot(slot).item)) {
                eatGlideElytraSlot = -1
                Constants.LOGGER.warn(
                    "Elytra Fly: 胸甲或鞘翅槽位已被改动，跳过装备还原（记录槽位 {}，当前胸甲槽物品 {}）",
                    slot,
                    chestStack
                )
                return@runSafe
            }

            swapArmor(slot)
            eatGlideElytraSlot = -1
        }
    }

    private fun isChestEquippable(stack: ItemStack): Boolean {
        if (stack.isEmpty) return true
        val equippable = stack.get(DataComponents.EQUIPPABLE)
        return equippable != null && equippable.slot == EquipmentSlot.CHEST
    }

    private fun toContainerSlot(inventorySlot: Int): Int {
        return if (inventorySlot < 9) inventorySlot + 36 else inventorySlot
    }

    private fun jiaFei(elytraSlot: Int) {
        runSafe {
            val elytra = if (elytraSlot < 9) elytraSlot + 36 else elytraSlot

            swapArmor(elytra)
            if (startFallFlying()) {
                shouldJump = true
            }
            useTimedFirework()
            swapArmor(elytra)
        }
    }

    private companion object {

        private const val CEILING_PROBE_DISTANCE = 0.75
        private const val CEILING_PROBE_EPSILON = 1.0E-4
        private const val CEILING_ESCAPE_PITCH = 5.0f
        private const val EAT_GLIDE_START_TICK = 30
        private const val CHEST_MENU_SLOT = 6
        private const val EAT_COMPLETE_REMAINING_TICKS = 3
        private const val EAT_CLIMB_TICK = 20
        private const val EAT_CLIMB_TICKS = 5
        private const val EAT_CLIMB_PITCH = -45f

        /**
         * 动量不足的总速度阈值（格/tick），含垂直分量。
         */
        private const val LOW_MOMENTUM_SPEED = 0.4

        /**
         * 低动量时的最短烟花补发间隔（tick）。
         */
        private const val LOW_MOMENTUM_BOOST_INTERVAL_TICKS = 4

    }

}
