package com.github.epsilon.managers

import com.github.epsilon.events.api.AlwaysListening
import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.kernel.gameplay.rotation.Rotation
import com.github.epsilon.kernel.gameplay.rotation.RotationPriority
import com.github.epsilon.kernel.gameplay.rotation.RotationUtils
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.modules.impl.movement.MovementFix
import com.github.epsilon.modules.impl.render.FreeCamera
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.HitResult
import java.util.function.Function
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

object RotationManager : AlwaysListening {

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
    private var rotationRequest: Rotation? = null

    init {
        handler<RespawnEvent>(alwaysListening = true) {
            rotationRequest = null
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

        handler<RaytraceEvent>(alwaysListening = true) { event ->
            if (hasActiveRotation() && (calculatingHitResult || shouldModifyCrosshair())) {
                event.yaw = rotations.yaw
                event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
            }
        }

        safeEventHandler<PlayerTickEvent.Pre>(priority = -1000, alwaysListening = true) {
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

        handler<RotationAnimationEvent>(alwaysListening = true) { event ->
            val animation = animationRotation
            val lastAnimation = lastAnimationRotation
            if (active && animation != null && lastAnimation != null) {
                event.yaw = animation.yaw
                event.lastYaw = lastAnimation.yaw
                event.pitch = animation.pitch
                event.lastPitch = lastAnimation.pitch
            }
        }

        handler<SendPositionEvent>(EventPriority.LOWEST, alwaysListening = true) { event ->
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
            } else {
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
        }

        safeEventHandler<KeyboardInputEvent>(EventPriority.HIGH, alwaysListening = true) { event ->
            if (MovementFix.isEnabled && hasActiveRotation() && !player.isFallFlying) {
                MovementFix.fixMovement(event, rotations.yaw)
            }
        }

        handler<UseItemRaytraceEvent>(alwaysListening = true) { event ->
            if (hasActiveRotation()) {
                event.yaw = rotations.yaw
                event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
            }
        }

        safeEventHandler<StrafeEvent>(alwaysListening = true) { event ->
            if (MovementFix.isEnabled && hasActiveRotation() && !player.isFallFlying) {
                event.yaw = rotations.yaw
            }
        }

        safeEventHandler<JumpEvent>(alwaysListening = true) { event ->
            if (MovementFix.isEnabled && hasActiveRotation() && !player.isFallFlying) {
                event.yaw = rotations.yaw
            }
        }

        handler<FallFlyingEvent>(alwaysListening = true) { event ->
            if (MovementFix.isEnabled && hasActiveRotation()) {
                event.yaw = rotations.yaw
                event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
            }
        }

        handler<UseItemEvent>(alwaysListening = true) { event ->
            if (hasActiveRotation()) {
                event.yaw = rotations.yaw
                event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
            }
        }

        handler<FireworkRotationEvent>(alwaysListening = true) { event ->
            if (hasActiveRotation()) {
                event.yaw = rotations.yaw
                event.pitch = rotations.pitch.coerceIn(-90.0f, 90.0f)
            }
        }

        handler<AttackYawEvent>(alwaysListening = true) { event ->
            if (hasActiveRotation()) {
                event.yaw = rotations.yaw
            }
        }

        handler<PacketEvent.Send>(alwaysListening = true) { event ->
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

    context(nn: NonNullContext)
    fun setRotations(rotations: Rotation?, rotationSpeed: Double) {
        setRotations(rotations, rotationSpeed, null, RotationPriority.Medium)
    }

    context(nn: NonNullContext)
    fun setRotations(rotations: Rotation?, rotationSpeed: Double, priority: RotationPriority) {
        setRotations(rotations, rotationSpeed, null, priority)
    }

    context(nn: NonNullContext)
    fun setRotations(rotations: Rotation?, rotationSpeed: Double, raytrace: Function<Rotation, Boolean>?) {
        setRotations(rotations, rotationSpeed, raytrace, RotationPriority.Medium)
    }

    context(nn: NonNullContext)
    fun setRotations(
        rotations: Rotation?,
        rotationSpeed: Double,
        raytrace: Function<Rotation, Boolean>?,
        priority: RotationPriority
    ) {
        if (rotations == null) return

        if (this.active && priority.priority < this.priority) {
            return
        }

        this.targetRotations = rotations
        this.rotationRequest = rotations
        this.rotationSpeed = rotationSpeed
        this.raytrace = raytrace
        this.priority = priority.priority
        this.active = true

        smooth()
    }

    /** 采用调用方已经完成步进的角度，不再叠加随机平滑；仍参与统一优先级和移动修正。 */
    fun setRotationsExact(request: Rotation, priority: RotationPriority): Boolean {
        if (active && priority.priority < this.priority) return false
        rotationRequest = request
        targetRotations = request
        rotations = request
        rotationSpeed = 180.0
        this.priority = priority.priority
        active = true
        smoothed = true
        raytrace = null
        updateHitResult()
        return true
    }

    fun ownsRotation(request: Rotation): Boolean = active && rotationRequest === request

    fun clearRotations(expectedRequest: Rotation) {
        if (rotationRequest !== expectedRequest) return
        rotationRequest = null
        active = false
        priority = 0
        targetRotations = null
        raytrace = null
        smoothed = false
        rotationHitResult = null
        calculatingHitResult = false
        useItemRotation = null
    }

    context(nn: NonNullContext)
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
                                (if (nn.player.tickCount / 10 % 2 == 0) -1 else 1)
                        ).toFloat()

                offset.set(
                    (offset.yaw + -Mth.sin(Math.toRadians(randomAngle.toDouble())) * speed).toFloat(),
                    (offset.pitch + Mth.cos(Math.toRadians(randomAngle.toDouble())) * speed).toFloat()
                )

                targetYaw += offset.yaw
                targetPitch += offset.pitch

                if (!raytrace.apply(Rotation(targetYaw, targetPitch))) {
                    randomAngle = (Math.toDegrees(
                        atan2(
                            (trueTargetRotations.yaw - targetYaw).toDouble(),
                            (targetPitch - trueTargetRotations.pitch).toDouble()
                        )
                    ) - 180).toFloat()

                    targetYaw -= offset.yaw
                    targetPitch -= offset.pitch

                    offset.set(
                        (offset.yaw + -Mth.sin(Math.toRadians(randomAngle.toDouble())) * speed).toFloat(),
                        (offset.pitch + Mth.cos(Math.toRadians(randomAngle.toDouble())) * speed).toFloat()
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

    context(nn: NonNullContext)
    fun getYaw(): Float {
        return getRotation().yaw
    }

    context(nn: NonNullContext)
    fun getPitch(): Float {
        return getRotation().pitch
    }

    context(nn: NonNullContext)
    fun getRotation(): Rotation {
        return if (active) rotations else Rotation(nn.player.yRot, nn.player.xRot)
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

}
