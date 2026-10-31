package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.AfterSendPositionEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.network.NetworkUtils
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.safety.runSafe
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sign

/**
 * 鞘翅 Grim 拉回加速，移植自 SlimefunHelper 的 {@code ElytraGrimAccelerate}。
 *
 * 原理：滑翔期间丢弃本 tick 的真实移动包并把本地坐标回滚到 tick 起点，客户端坐标因此停留在服务端
 * 最后收到的位置上，而速度不受影响；随后发一个不可能的伪造移动包（Y 轴抬高 2.5~7.5 格，或把 X/Z
 * 写成世界边界外的坐标），强迫 Grim 判定预测失败并执行拉回（setback）。Grim 的拉回传送包会把客户端
 * 速度一并回传，并且在拉回被确认之前挂起预测检查；因为本地坐标已经回滚，拉回目标与客户端当前位置
 * 一致，玩家不会被拖走，只拿到速度，于是可以逐轮累积速度，直到达到本模块的速度上限。
 *
 * 该功能依赖服务端安装 Grim，本地世界或没有 Grim 的服务器不会有任何加速效果；速度上限用于避免
 * 服务端直接判定超速拉回或踢出。
 */
object ElytraGrimAccelerate : Module("Elytra Grim Acc", Category.MOVEMENT) {

    /**
     * 触发拉回的方式。
     */
    enum class SetBackTrigger {
        /**
         * 抬高 Y 轴坐标，改动最小。
         */
        Simulation,

        /**
         * 把 X/Z 写到世界边界外，强制产生拉回，风险更高。
         */
        CrashPackets,
    }

    /**
     * 伪造包使用的 Y 轴偏移，按 tick 轮换以避开固定的位置漂移。
     */
    private val SIMULATION_Y_OFFSETS = doubleArrayOf(2.5, 5.0, 7.5)

    /**
     * Grim 会把超过 3.0E7 的坐标夹回边界，用更大的坐标可以稳定触发拉回。
     */
    private const val BORDER_COORDINATE = 3.9999999E7

    /**
     * 旧版本下服务端速度包会放大本地速度的水平阈值。
     */
    private const val LEGACY_VELOCITY_THRESHOLD = 3.8

    /**
     * 判断真实位置包是否被视为发生位移的最小平方距离阈值。
     */
    private const val POSITION_EPSILON = 1.0E-7

    private val mode by enumSetting("Mode", SetBackTrigger.Simulation)
    private val maxAccelerateVelocity by doubleSetting("Max Accelerate Velocity", 6.0, 0.0, 100.0, 0.5)
    private val setbackWait by intSetting("Setback Wait", 20, 1, 100, 1)
    private val fixOldVersionVelocity by boolSetting("Fix Old Version Velocity", false)
    private val fixKickFromLag by boolSetting("Fix Kick From Lag", true)

    private var tickStartX = 0.0
    private var tickStartY = 0.0
    private var tickStartZ = 0.0

    private var lastSentX = 0.0
    private var lastSentY = 0.0
    private var lastSentZ = 0.0
    private var hasSentPosition = false

    /**
     * 服务端视角下最后一次真实位移，用于判断速度是否已经超过上限。
     */
    private var lastKnownMovementSpeed = Vec3.ZERO

    private var currentTryWorking = false

    private var pendingSetBackPacket: ServerboundMovePlayerPacket? = null

    /**
     * 上一次发出伪造包的 tick；收到客户端对拉回的确认后会重置，允许立即开始下一轮。
     */
    @Volatile
    private var lastSetBackPacketTick = 0

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            tickStartX = player.x
            tickStartY = player.y
            tickStartZ = player.z

            // 旧版本速度异常时不再接管移动，否则会把服务端已经接受的速度继续放大
            val legacyVelocity = fixOldVersionVelocity && isLegacyVelocity(lastKnownMovementSpeed)
            currentTryWorking = isEnabled &&
                    !legacyVelocity &&
                    player.isFallFlying &&
                    !player.onGround() &&
                    !isFireworkBoosted()
        }

        nonNullHandler<SendPositionEvent>(EventPriority.LOW) { event ->
            if (!currentTryWorking || !isSpeedAllowed()) {
                trackSentPosition(event.x, event.y, event.z)
                return@nonNullHandler
            }

            // 回滚到 tick 起点：本地坐标停在服务端最后收到的位置上，拉回才不会把玩家拖走
            val moveX = player.x - tickStartX
            val moveZ = player.z - tickStartZ
            player.setPos(tickStartX, tickStartY, tickStartZ)
            event.cancel()

            if (pendingSetBackPacket != null) {
                // 上一轮的伪造包尚未发出，丢弃它，避免同一 tick 连发两个包
                pendingSetBackPacket = null
                return@nonNullHandler
            }
            pendingSetBackPacket = createSetBackPacket(moveX, moveZ)
        }

        nonNullHandler<AfterSendPositionEvent> {
            val packet = pendingSetBackPacket
            pendingSetBackPacket = null

            if (packet == null) return@nonNullHandler

            // 绕过 Epsilon 的发送事件，避免伪造包被其他模块记录或延迟
            NetworkUtils.sendPacketNoEvent(packet)
            lastSetBackPacketTick = player.tickCount
        }

        handler<PacketEvent.Send> { event ->
            if (event.packet is ServerboundAcceptTeleportationPacket) {
                // 客户端已确认服务端拉回，预测检查重新可用，可以开始下一轮
                lastSetBackPacketTick = 0
            }
        }

        handler<PacketEvent.Receive> { event ->
            if (event.packet is ClientboundPlayerPositionPacket) {
                // 服务端传送会直接改写客户端坐标，下一个位置包的位移不能当作真实速度
                hasSentPosition = false
            }
        }
    }

    override fun onEnable() {
        pendingSetBackPacket = null
        currentTryWorking = false
        lastSetBackPacketTick = 0
        lastKnownMovementSpeed = Vec3.ZERO
        hasSentPosition = false
        runSafe {
            hasSentPosition = true
            lastSentX = player.x
            lastSentY = player.y
            lastSentZ = player.z
        }
    }

    override fun onDisable() {
        pendingSetBackPacket = null
        currentTryWorking = false
    }

    /**
     * 速度上限用于避免服务端已经接受的速度被继续放大成超速拉回或踢出。
     */
    private fun isSpeedAllowed(): Boolean {
        return lastKnownMovementSpeed.length() <= maxAccelerateVelocity
    }

    /**
     * 记录真正发给服务端的位置，作为服务端视角下的移动速度。
     *
     * 零位移的位置包（例如只更新朝向）不会覆盖速度，与上游保持一致。
     */
    private fun trackSentPosition(x: Double, y: Double, z: Double) {
        if (!hasSentPosition) {
            hasSentPosition = true
            lastSentX = x
            lastSentY = y
            lastSentZ = z
            return
        }

        val delta = Vec3(x - lastSentX, y - lastSentY, z - lastSentZ)
        lastSentX = x
        lastSentY = y
        lastSentZ = z
        if (delta.lengthSqr() > POSITION_EPSILON) {
            lastKnownMovementSpeed = delta
        }
    }

    /**
     * 构造用于触发拉回的伪造位置包，坐标取回滚之后的本地位置。
     */
    context(nn: NonNullContext)
    private fun createSetBackPacket(moveX: Double, moveZ: Double): ServerboundMovePlayerPacket? {
        if (fixKickFromLag) {
            // 区块未加载时服务端会忽略移动，发送伪造包只会换来踢出
            if (isFacingUnloadedChunk(moveX, moveZ)) return null
            // 已经在等待上一次拉回结果时不再继续发，避免刷包
            if (player.tickCount < lastSetBackPacketTick + setbackWait) return null
        }

        val y = player.y + SIMULATION_Y_OFFSETS[player.tickCount % SIMULATION_Y_OFFSETS.size]
        val yRot = player.yRot
        val xRot = player.xRot
        val onGround = player.onGround()
        val horizontalCollision = player.horizontalCollision

        return when (mode) {
            SetBackTrigger.Simulation -> ServerboundMovePlayerPacket.PosRot(
                player.x, y, player.z, yRot, xRot, onGround, horizontalCollision
            )

            SetBackTrigger.CrashPackets -> ServerboundMovePlayerPacket.PosRot(
                BORDER_COORDINATE, y, BORDER_COORDINATE, yRot, xRot, onGround, horizontalCollision
            )
        }
    }

    /**
     * 判断前进方向上相邻的区块是否已经加载，对应上游 AntiChunkLag 的卡区块保护。
     */
    context(nn: NonNullContext)
    private fun isFacingUnloadedChunk(moveX: Double, moveZ: Double): Boolean {
        var directionX = moveX
        var directionZ = moveZ
        if (abs(directionX) < 1.0E-3 && abs(directionZ) < 1.0E-3) {
            val velocity = player.deltaMovement
            directionX = velocity.x
            directionZ = velocity.z
        }

        val signX = sign(directionX).toInt()
        val signZ = sign(directionZ).toInt()
        if (signX == 0 && signZ == 0) return false

        val chunkX = SectionPos.blockToSectionCoord(player.x)
        val chunkZ = SectionPos.blockToSectionCoord(player.z)
        for (x in 0..1) {
            for (z in 0..1) {
                if (!level.hasChunk(chunkX + x * signX, chunkZ + z * signZ)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * 上游在玩家被烟花推进时不介入：烟花产生的位移是合法的，交给其他模块控制。
     *
     * 26.3 的 {@code FireworkRocketEntity#isAttachedToEntity} 仍不可见，这里用贴身检测代替。
     */
    context(nn: NonNullContext)
    private fun isFireworkBoosted(): Boolean {
        val box: AABB = player.boundingBox.inflate(1.0)
        for (rocket in level.getEntitiesOfClass(FireworkRocketEntity::class.java, box)) {
            if (rocket.isAlive) {
                return true
            }
        }
        return false
    }

    /**
     * 旧版本（服务端速度包放大本地速度）下会出现异常的水平速度，此时不应该继续加速。
     */
    private fun isLegacyVelocity(velocity: Vec3): Boolean {
        return abs(velocity.x) >= LEGACY_VELOCITY_THRESHOLD || abs(velocity.z) >= LEGACY_VELOCITY_THRESHOLD
    }

}
