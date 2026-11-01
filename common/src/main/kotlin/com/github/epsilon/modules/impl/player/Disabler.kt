package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.kernel.jvm.*
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.ViaVersionComparison
import com.github.epsilon.settings.viaOnly
import com.github.epsilon.utils.network.NetworkUtils
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

object Disabler : Module("Disabler", Category.PLAYER) {

    private val multiActionsC by boolSetting("MultiActionsC", true).viaOnly(ViaVersionComparison.LESS_OR_EQUAL, "1.20.6")

    private var sprintPause: SprintPause? = null

    val isInventorySprintPaused: Boolean
        get() = isEnabled && multiActionsC && sprintPause != null

    init {
        onEnable {
            sprintPause = null
        }

        onDisable {
            sprintPause = null
        }

        safeEventHandler<ClientTickEvent.Pre> {
            val pause = sprintPause ?: return@safeEventHandler
            if (!multiActionsC) {
                sprintPause = null
                return@safeEventHandler
            }

            player.isSprinting = false
            if (++pause.ticksWithoutClick <= 1 || !pause.movementObserved) return@safeEventHandler

            // 先结束暂停，原版 canStartSprinting 才不会被暂停守卫拦截。
            sprintPause = null
            if (pause.resumeSprinting && player.canStartSprinting()) player.isSprinting = true
        }

        safeEventHandler<PacketEvent.Send>(EventPriority.HIGH) { event ->
            if (event.isCancelled || !multiActionsC) return@safeEventHandler
            val packet = event.packet as? ServerboundContainerClickPacket ?: return@safeEventHandler
            if (!player.wasSprinting && sprintPause == null) return@safeEventHandler

            event.cancel()
            withSprintStopped { NetworkUtils.sendPacketNoEvent(packet) }
        }

        // 最后观察移动包，跳过已被其他模块取消的包。
        safeEventHandler<PacketEvent.Send>(-10000) { event ->
            if (event.isCancelled) return@safeEventHandler
            val pause = sprintPause ?: return@safeEventHandler
            val packet = event.packet as? ServerboundMovePlayerPacket ?: return@safeEventHandler
            if (isMovementBoundary(packet, pause)) pause.movementObserved = true
        }
    }

    /** ViaFabricPlus 的旧版点击绕过原生发包事件，必须包住整个容器操作。 */
    fun handleInventoryClick(actor: Player, action: Runnable) {
        runSafe {
            if (isEnabled && multiActionsC && actor === player) {
                withSprintStopped(action)
            } else {
                action.run()
            }
        } ?: action.run()
    }

    context(nn: NonNullContext)
    private fun withSprintStopped(action: Runnable) {
        if (sprintPause == null && !nn.player.wasSprinting && !nn.player.isSprinting) {
            action.run()
            return
        }

        val pause = sprintPause ?: SprintPause(
            nn.player.position(), nn.player.onGround(), nn.player.isSprinting
        ).also { sprintPause = it }
        pause.ticksWithoutClick = 0
        nn.player.isSprinting = false

        if (nn.player.wasSprinting) {
            NetworkUtils.sendPacketNoEvent(
                ServerboundPlayerCommandPacket(nn.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
            )
            nn.player.wasSprinting = false
        }
        action.run()
    }

    private fun isMovementBoundary(packet: ServerboundMovePlayerPacket, pause: SprintPause): Boolean = when {
        packet is ServerboundMovePlayerPacket.Pos || packet is ServerboundMovePlayerPacket.Rot -> true
        packet.isOnGround != pause.onGround -> true
        packet !is ServerboundMovePlayerPacket.PosRot -> false
        // 旧协议可能忽略同位置的 PosRot，不能据此恢复疾跑。
        else -> pause.position.distanceToSqr(
            packet.getX(pause.position.x), packet.getY(pause.position.y), packet.getZ(pause.position.z)
        ) > 0.03 * 0.03
    }

    private class SprintPause(
        val position: Vec3,
        val onGround: Boolean,
        val resumeSprinting: Boolean,
    ) {
        var ticksWithoutClick = 0
        var movementObserved = false
    }

}
