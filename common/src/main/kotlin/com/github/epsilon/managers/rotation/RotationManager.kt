package com.github.epsilon.managers.rotation

import com.github.epsilon.Constants.mc
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.*
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.HitResult
import java.util.function.Function
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * 托管旋转管理器基类。
 *
 * 与其它 Manager 不同，本类实例是**可替换**的：[switchRotationManager] 会创建新的
 * [SilentRotationManager] / [SnapRotationManager] 实例并赋给 [INSTANCE]。因此调用方每次
 * 使用都必须重新读取 [INSTANCE]，不得长期缓存（AGENTS 约束）。
 *
 * INSTANCE 使用 `@Volatile` 保证跨线程可见性；Kotlin 侧沿用 `RotationManager.INSTANCE.xxx`
 * 语法，Java 侧 Phase B 改为 `RotationManager.Companion.instance`。
 */
abstract class RotationManager {

    enum class RotationMode {
        SILENT,
        SNAP
    }

    private val offset = Rot2f(0f, 0f)
    var rotations = Rot2f(0f, 0f)
    var lastRotations = Rot2f(0f, 0f)
    var targetRotations: Rot2f? = null
    var animationRotation: Rot2f? = null
    var lastAnimationRotation: Rot2f? = null

    @Volatile
    protected var active = false

    @Volatile
    protected var smoothed = false

    @Volatile
    protected var rotationSpeed = 0.0
    protected var raytrace: Function<Rot2f, Boolean>? = null
    private var randomAngle = 0f
    private var rotationHitResult: HitResult? = null
    private var calculatingHitResult = false

    protected var priority = 0

    fun setRotations(rotations: Rot2f?, rotationSpeed: Double) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium)
    }

    fun setRotations(rotations: Rot2f?, rotationSpeed: Double, priority: Priority) {
        setRotations(rotations, rotationSpeed, null, priority)
    }

    fun setRotations(rotations: Rot2f?, rotationSpeed: Double, raytrace: Function<Rot2f, Boolean>?) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium)
    }

    fun setRotations(
        rotations: Rot2f?, rotationSpeed: Double,
        raytrace: Function<Rot2f, Boolean>?, priority: Priority,
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
        onRotationsSet()
    }

    protected open fun onRotationsSet() {
    }

    protected open fun resetModeState() {
    }

    protected open fun smooth() {
        if (!smoothed) {
            var targetYaw = targetRotations!!.yaw
            var targetPitch = targetRotations!!.pitch
            val raytrace = this.raytrace

            if (raytrace != null &&
                (abs(targetYaw - rotations.yaw) > 5 || abs(targetPitch - rotations.pitch) > 5)
            ) {
                val trueTargetRotations = Rot2f(targetRotations!!.yaw, targetRotations!!.pitch)

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

                if (!raytrace.apply(Rot2f(targetYaw, targetPitch))) {
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

                if (!raytrace.apply(Rot2f(targetYaw, targetPitch))) {
                    offset.set(0f, 0f)

                    targetYaw = (targetRotations!!.yaw + Random.nextDouble() * 2).toFloat()
                    targetPitch = (targetRotations!!.pitch + Random.nextDouble() * 2).toFloat()
                }
            }

            rotations = RotationUtils.smooth(Rot2f(targetYaw, targetPitch), rotationSpeed + Random.nextDouble())
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

    protected open fun shouldModifyCrosshair(): Boolean {
        return false
    }

    protected fun hasActiveRotation(): Boolean {
        return active
    }

    protected fun clampPitch(pitch: Float): Float {
        return pitch.coerceIn(-90.0f, 90.0f)
    }

    protected fun correctDisabledRotations(player: LocalPlayer) {
        val playerRotation = Rot2f(player.yRot, player.xRot)
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

    fun getRotation(): Rot2f {
        val player = mc.player!!
        return if (active) rotations else Rot2f(player.yRot, player.xRot)
    }

    fun getLastRotation(): Rot2f {
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

    open fun copyStateFrom(manager: RotationManager) {
        this.rotations = manager.rotations
        this.lastRotations = manager.lastRotations
        this.targetRotations = manager.targetRotations
        this.animationRotation = manager.animationRotation
        this.lastAnimationRotation = manager.lastAnimationRotation
        this.active = manager.active
        this.smoothed = manager.smoothed
        this.rotationSpeed = manager.rotationSpeed
        this.raytrace = manager.raytrace
        this.priority = manager.priority
        this.rotationHitResult = manager.rotationHitResult
    }

    @EventHandler
    protected open fun onRespawn(event: RespawnEvent) {
        offset.set(0f, 0f)
        rotations = Rot2f(0f, 0f)
        lastRotations = Rot2f(0f, 0f)
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
        resetModeState()
    }

    @EventHandler
    private fun onRaytrace(event: RaytraceEvent) {
        if (!hasActiveRotation() || (!calculatingHitResult && !shouldModifyCrosshair())) return

        event.yaw = rotations.yaw
        event.pitch = clampPitch(rotations.pitch)
    }

    @EventHandler(priority = -1000)
    protected open fun onPlayerTick(event: PlayerTickEvent.Pre) {
        val player = mc.player!!
        if (!active || targetRotations == null) {
            val current = Rot2f(player.yRot, player.xRot)
            rotations = current
            lastRotations = current
            targetRotations = current
        }

        if (hasActiveRotation()) {
            smooth()
            afterPlayerTick()
        }
    }

    protected open fun afterPlayerTick() {
    }

    @EventHandler
    protected open fun onAnimation(event: RotationAnimationEvent) {
        val animation = animationRotation
        val lastAnimation = lastAnimationRotation
        if (active && animation != null && lastAnimation != null) {
            event.yaw = animation.yaw
            event.lastYaw = lastAnimation.yaw
            event.pitch = animation.pitch
            event.lastPitch = lastAnimation.pitch
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected open fun onSendPosition(event: SendPositionEvent) {
        val player = mc.player
        if (player == null) {
            active = false
            priority = 0
            targetRotations = null
            raytrace = null
            smoothed = false
            rotationHitResult = null
            calculatingHitResult = false
            resetModeState()
            return
        }

        if (active) {
            handleSendPosition(event)

            if (abs((rotations.yaw - player.yRot) % 360) < 1 &&
                abs(rotations.pitch - player.xRot) < 1
            ) {
                active = false
                priority = 0
                this.correctDisabledRotations(player)
            }

            lastRotations = rotations
        } else {
            lastRotations = Rot2f(player.yRot, player.xRot)
        }

        lastAnimationRotation = animationRotation
        animationRotation = Rot2f(event.yaw, event.pitch)
        targetRotations = Rot2f(player.yRot, player.xRot)
        raytrace = null
        smoothed = false
    }

    protected abstract fun handleSendPosition(event: SendPositionEvent)

    companion object {

        /**
         * 当前生效的托管旋转实例。
         *
         * 该字段是可替换的（模式切换会整体替换实例），调用方每次都必须重新读取。
         * 初始实例与 [RotationMode] 默认值 [RotationMode.SILENT] 保持一致。
         */
        @Volatile
        var INSTANCE: RotationManager = SilentRotationManager()
            private set

        init {
            EventBus.subscribe(INSTANCE)
        }

        fun switchRotationManager(mode: RotationMode) {
            val previous = INSTANCE
            val next = when (mode) {
                RotationMode.SNAP -> SnapRotationManager()
                RotationMode.SILENT -> SilentRotationManager()
            }
            next.copyStateFrom(previous)
            EventBus.unsubscribe(previous)
            INSTANCE = next
            EventBus.subscribe(next)
        }

    }

}
