package com.github.epsilon.managers

import com.github.epsilon.Constants.mc
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.*
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.modules.impl.movement.MovementFix
import com.github.epsilon.modules.impl.render.FreeCamera
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import com.github.epsilon.utils.rotation.RotationUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.HitResult
import java.util.function.Function
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

object RotationManager {

    private val offset = Rotation(0f, 0f)
    var rotations = Rotation(0f, 0f)
    var lastRotations = Rotation(0f, 0f)
    var targetRotations: Rotation? = null
    var animationRotation: Rotation? = null
    var lastAnimationRotation: Rotation? = null

    @Volatile
    private var active = false

    @Volatile
    private var smoothed = false

    @Volatile
    private var rotationSpeed = 0.0
    private var raytrace: Function<Rotation, Boolean>? = null
    private var randomAngle = 0f
    private var rotationHitResult: HitResult? = null
    private var calculatingHitResult = false
    private var useItemRotation: Rotation? = null

    private var priority = 0

    init {
        handler<RespawnEvent>(alwaysListening = true) { onRespawn(it) }
        handler<RaytraceEvent>(alwaysListening = true) { onRaytrace(it) }
        handler<PlayerTickEvent.Pre>(priority = -1000, alwaysListening = true) { onPlayerTick(it) }
        handler<RotationAnimationEvent>(alwaysListening = true) { onAnimation(it) }
        handler<SendPositionEvent>(EventPriority.LOWEST, alwaysListening = true) { onSendPosition(it) }
        handler<KeyboardInputEvent>(EventPriority.HIGH, alwaysListening = true) { onMoveInput(it) }
        handler<UseItemRaytraceEvent>(alwaysListening = true) { onItemRaytrace(it) }
        handler<StrafeEvent>(alwaysListening = true) { onStrafe(it) }
        handler<JumpEvent>(alwaysListening = true) { onJump(it) }
        handler<FallFlyingEvent>(alwaysListening = true) { onFallFlying(it) }
        handler<UseItemEvent>(alwaysListening = true) { onUseItem(it) }
        handler<FireworkRotationEvent>(alwaysListening = true) { onFireworkUpdate(it) }
        handler<AttackYawEvent>(alwaysListening = true) { onAttack(it) }
        handler<PacketEvent.Send>(alwaysListening = true) { onPacketSend(it) }
    }

    fun setRotations(rotations: Rotation?, rotationSpeed: Double) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium)
    }

    fun setRotations(rotations: Rotation?, rotationSpeed: Double, priority: Priority) {
        setRotations(rotations, rotationSpeed, null, priority)
    }

    fun setRotations(rotations: Rotation?, rotationSpeed: Double, raytrace: Function<Rotation, Boolean>?) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium)
    }

    fun setRotations(
        rotations: Rotation?,
        rotationSpeed: Double,
        raytrace: Function<Rotation, Boolean>?,
        priority: Priority
    ) {
        if (rotations == null) return

        if (this.active && priority.priority < this.priority) {
            return
        }

        this.targetRotations = rotations
        this.rotationSpeed = rotationSpeed
        this.raytrace = raytrace
        this.priority = priority.priority
        this.active = true

        smooth()
    }

    private fun smooth() {
        if (!smoothed) {
            var targetYaw = targetRotations!!.yaw
            var targetPitch = targetRotations!!.pitch
            val raytrace = this.raytrace

            if (raytrace != null &&
                (abs(targetYaw - rotations.yaw) > 5 || abs(targetPitch - rotations.pitch) > 5)
            ) {
                val trueTargetRotations = Rotation(targetRotations!!.yaw, targetRotations!!.pitch)

                val speed = (Random.nextDouble() * Random.nextDouble() * Random.nextDouble()) * 20
                randomAngle += (
                        (20 + (Random.nextDouble() - 0.5).toFloat() *
                                (Random.nextDouble() * Random.nextDouble() * Random.nextDouble() * 360).toFloat()) *
                                (if (mc.player!!.tickCount / 10 % 2 == 0) -1 else 1)
                        ).toFloat()

                offset.set(
                    (offset.yaw + -Mth.sin((randomAngle.toDouble()).toRadians()) * speed).toFloat(),
                    (offset.pitch + Mth.cos((randomAngle.toDouble()).toRadians()) * speed).toFloat()
                )

                targetYaw += offset.yaw
                targetPitch += offset.pitch

                if (!raytrace.apply(Rotation(targetYaw, targetPitch))) {
                    randomAngle = ((atan2(
                        (trueTargetRotations.yaw - targetYaw).toDouble(),
                        (targetPitch - trueTargetRotations.pitch).toDouble()
                    )).toDegrees() - 180).toFloat()

                    targetYaw -= offset.yaw
                    targetPitch -= offset.pitch

                    offset.set(
                        (offset.yaw + -Mth.sin((randomAngle.toDouble()).toRadians()) * speed).toFloat(),
                        (offset.pitch + Mth.cos((randomAngle.toDouble()).toRadians()) * speed).toFloat()
                    )

                    targetYaw = targetYaw + offset.yaw
                    targetPitch = targetPitch + offset.pitch
                }

                if (!raytrace.apply(Rotation(targetYaw, targetPitch))) {
                    offset.set(0f, 0f)

                    targetYaw = (targetRotations!!.yaw + Random.nextDouble() * 2).toFloat()
                    targetPitch = (targetRotations!!.pitch + Random.nextDouble() * 2).toFloat()
                }
            }

            rotations = RotationUtils.smooth(Rotation(targetYaw, targetPitch), rotationSpeed + Random.nextDouble())
        }

        smoothed = true

        updateHitResult()
        if (shouldModifyCrosshair()) {
            mc.pick(1.0f)
        }
    }

    private fun updateHitResult() {
        val player = mc.player
        if (!hasActiveRotation() || player == null || mc.level == null) {
            rotationHitResult = null
            return
        }

        calculatingHitResult = true
        try {
            rotationHitResult = player.raycastHitResult(1.0f, player)
        } finally {
            calculatingHitResult = false
        }
    }

    private fun shouldModifyCrosshair(): Boolean {
        return ClientSetting.modifyCrosshair && !FreeCamera.isEnabled
    }

    private fun hasActiveRotation(): Boolean {
        return active
    }

    private fun correctDisabledRotations(player: LocalPlayer) {
        val playerRotation = Rotation(player.yRot, player.xRot)
        val fixedRotations = RotationUtils.applySensitivityPatch(playerRotation, lastRotations)
        val fixedYaw = fixedRotations.yaw + Mth.wrapDegrees(player.yRot - fixedRotations.yaw)
        player.yRot = fixedYaw
    }

    fun getYaw(): Float {
        return getRotation().yaw
    }

    fun getPitch(): Float {
        return getRotation().pitch
    }

    fun getRotation(): Rotation {
        val player = mc.player!!
        return if (active) rotations else Rotation(player.yRot, player.xRot)
    }

    fun getLastRotation(): Rotation {
        return lastRotations
    }

    /**
     * 获取按当前托管旋转计算的逻辑命中结果。未启用托管旋转时返回原版准星结果。
     *
     * @return 当前逻辑命中结果
     */
    fun getHitResult(): HitResult? {
        return if (hasActiveRotation() && rotationHitResult != null) rotationHitResult else mc.hitResult
    }

    fun isActive(): Boolean {
        return active
    }

    fun updateActive(active: Boolean) {
        this.active = active
    }

    fun isSmoothed(): Boolean {
        return smoothed
    }

    fun updateSmoothed(smoothed: Boolean) {
        this.smoothed = smoothed
    }

    private fun onRespawn(event: RespawnEvent) {
        offset.set(0f, 0f)
        rotations = Rotation(0f, 0f)
        lastRotations = Rotation(0f, 0f)
        targetRotations = null
        animationRotation = null
        lastAnimationRotation = null
        active = false
        priority = 0
        smoothed = false
        raytrace = null
        randomAngle = 0f
        rotationHitResult = null
        calculatingHitResult = false
        useItemRotation = null
    }

    private fun onRaytrace(event: RaytraceEvent) {
        if (!hasActiveRotation() || (!calculatingHitResult && !shouldModifyCrosshair())) return

        event.yaw = rotations.yaw
        event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
    }

    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        val player = mc.player!!
        if (!active || targetRotations == null) {
            val current = Rotation(player.yRot, player.xRot)
            rotations = current
            lastRotations = current
            targetRotations = current
        }

        if (hasActiveRotation()) {
            smooth()
        }
    }

    private fun onAnimation(event: RotationAnimationEvent) {
        val animation = animationRotation
        val lastAnimation = lastAnimationRotation
        if (active && animation != null && lastAnimation != null) {
            event.yaw = animation.yaw
            event.lastYaw = lastAnimation.yaw
            event.pitch = animation.pitch
            event.lastPitch = lastAnimation.pitch
        }
    }

    private fun onSendPosition(event: SendPositionEvent) {
        val player = mc.player
        if (player == null) {
            active = false
            priority = 0
            targetRotations = null
            raytrace = null
            smoothed = false
            rotationHitResult = null
            calculatingHitResult = false
            useItemRotation = null
            return
        }

        if (active) {
            // Item use is sent before LocalPlayer ticks; keep both packets on the same server rotation.
            val useItemRotation = useItemRotation
            if (useItemRotation != null) {
                rotations = useItemRotation
                this.useItemRotation = null
            }

            val yaw = rotations.yaw
            val pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)

            if (!yaw.isNaN() && !pitch.isNaN()) {
                event.yaw = yaw
                event.pitch = pitch
            }

            if (abs((rotations.yaw - player.yRot) % 360) < 1 &&
                abs(rotations.pitch - player.xRot) < 1
            ) {
                active = false
                priority = 0
                this.correctDisabledRotations(player)
            }

            lastRotations = rotations
        } else {
            lastRotations = Rotation(player.yRot, player.xRot)
        }

        lastAnimationRotation = animationRotation
        animationRotation = Rotation(event.yaw, event.pitch)
        targetRotations = Rotation(player.yRot, player.xRot)
        raytrace = null
        smoothed = false
    }

    private fun onMoveInput(event: KeyboardInputEvent) {
        if (MovementFix.isEnabled && hasActiveRotation() && !mc.player!!.isFallFlying) {
            MovementFix.fixMovement(event, rotations.yaw)
        }
    }

    private fun onItemRaytrace(event: UseItemRaytraceEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
        }
    }

    private fun onStrafe(event: StrafeEvent) {
        if (MovementFix.isEnabled && hasActiveRotation() && !mc.player!!.isFallFlying) {
            event.yaw = rotations.yaw
        }
    }

    private fun onJump(event: JumpEvent) {
        if (MovementFix.isEnabled && hasActiveRotation() && !mc.player!!.isFallFlying) {
            event.yaw = rotations.yaw
        }
    }

    private fun onFallFlying(event: FallFlyingEvent) {
        if (MovementFix.isEnabled && hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
        }
    }

    private fun onUseItem(event: UseItemEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
        }
    }

    private fun onFireworkUpdate(event: FireworkRotationEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
        }
    }

    private fun onAttack(event: AttackYawEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
        }
    }

    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet
        if (packet is ServerboundClientTickEndPacket) {
            useItemRotation = null
        } else if (hasActiveRotation() && packet is ServerboundUseItemPacket) {
            val yaw = rotations.yaw
            val pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
            if (!yaw.isNaN() && !pitch.isNaN()) {
                useItemRotation = Rotation(yaw, pitch)
                if (packet.yRot() != yaw || packet.xRot() != pitch) {
                    event.packet = ServerboundUseItemPacket(
                        packet.hand(),
                        packet.sequence(),
                        yaw,
                        pitch
                    )
                }
            }
        }
    }

}
