package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.gameplay.player.PlayerState.isInBlock
import com.github.epsilon.kernel.gameplay.player.SwingMode
import com.github.epsilon.kernel.gameplay.rotation.Rotation
import com.github.epsilon.kernel.gameplay.rotation.RotationPriority
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.settings.impl.EnumSetting
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2

object Velocity : Module("Velocity", Category.MOVEMENT) {

    enum class Mode {
        Cancel,
        Reduce
    }

    private val modeSetting: EnumSetting<Mode> = enumSetting("Mode", Mode.Cancel)
    val mode by modeSetting
    private val serverMotion by boolSetting("Server Motion", true, { mode == Mode.Cancel })
    private val explosion by boolSetting("Explosion", true, { mode == Mode.Cancel })
    private val explosionOnlyBlock by boolSetting("Explosion Only Block", false, { mode == Mode.Cancel && explosion })
    val waterPush by boolSetting("No Water Push", true, { mode == Mode.Cancel })
    val entityPush by boolSetting("No Entity Push", true, { mode == Mode.Cancel })
    val blockPush by boolSetting("No Block Push", true, { mode == Mode.Cancel })
    private val attackCounts by intSetting("Attack Counts", 3, 1..5, 1, { mode == Mode.Reduce })
    private val sprintTicks by intSetting("Sprint Ticks", 3, 1..5, 1, { mode == Mode.Reduce })
    private val maxDelay by intSetting("Max Delay", 1000, 0..1000, 50, { mode == Mode.Reduce })
    private val swingHand by boolSetting("Swing Hand", false, { mode == Mode.Reduce })

    var attackQueue: Int = 0
    var sprintQueue: Int = 0
    var delay: Boolean = false
    var jump: Boolean = false
    var yaw: Float = 0f

    private var lag: Long = 0
    private var startDelay: Long = 0

    private val packets: Queue<Packet<in ClientPacketListener>> = ConcurrentLinkedQueue()

    override fun getInfo(): String {
        return modeSetting.translatedValue + when (mode) {
            Mode.Cancel -> ""
            Mode.Reduce -> " " + (if (delay) (System.currentTimeMillis() - startDelay).toString() + "ms" else "")
        }
    }

    init {
        onDisable {
            delay = false
            packets.clear()
            attackQueue = 0
            jump = false
        }

        safeEventHandler<PacketEvent.Receive> { event ->
            when (mode) {
                Mode.Cancel -> cancelPacket(event)
                Mode.Reduce -> reducePacket(event)
            }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            if (delay && (player.onGround() || System.currentTimeMillis() - lag < 100 || System.currentTimeMillis() - startDelay >= maxDelay)
            ) {
                flush()
                if (System.currentTimeMillis() - lag >= 100) {
                    attackQueue = attackCounts
                    sprintQueue = sprintTicks
                }
            }
            if (sprintQueue >= 1 && KillAura.target == null && !Scaffold.isEnabled) {
                RotationManager.setRotations(
                    Rotation(yaw, RotationManager.getRotation().pitch),
                    180.0,
                    RotationPriority.Highest
                )
            }
        }

        safeEventHandler<PlayerTickEvent.Pre>(EventPriority.LOW) {
            if (attackQueue >= 1) {
                val hitResult = RotationManager.getHitResult()
                if (hitResult is EntityHitResult && hitResult.entity is Player && hitResult.entity.isAlive) {
                    gameMode.attack(player, hitResult.entity)
                    SwingMode.of(swingHand).attackSwing(InteractionHand.MAIN_HAND)
                }
                attackQueue--
            }
        }

        safeEventHandler<KeyboardInputEvent>(EventPriority.LOW) { event ->
            val hitResult = RotationManager.getHitResult()
            if (delay && player.fallDistance == 0.0 && hitResult is EntityHitResult && hitResult.entity is Player) {
                if (System.currentTimeMillis() - lag >= 100) {
                    event.forward = 1.0f
                    event.strafe = 0.0f
                }
            }

            if (jump && player.onGround()) {
                if (System.currentTimeMillis() - lag >= 100) {
                    event.jump = true
                    event.forward = 1.0f
                    event.strafe = 0.0f
                }
                jump = false
            }

            if (sprintQueue-- >= 1 && KillAura.target == null && !Scaffold.isEnabled && System.currentTimeMillis() - lag >= 100) {
                event.forward = 1.0f
                event.strafe = 0.0f
            }
        }
    }

    private fun NonNullContext.cancelPacket(event: PacketEvent.Receive) {
        val packet = event.packet

        if (serverMotion && packet is ClientboundSetEntityMotionPacket && packet.id == player.id) {
            event.cancel()
            return
        }

        if (explosion && packet is ClientboundExplodePacket && (!explosionOnlyBlock || isInBlock())) {
            event.packet = ClientboundExplodePacket(
                packet.center,
                packet.radius,
                packet.blockCount,
                Optional.empty(),
                packet.explosionParticle,
                packet.explosionSound,
                packet.blockParticles,
                packet.playSound
            )
        }
    }

    private fun NonNullContext.reducePacket(event: PacketEvent.Receive) {
        val packet = event.packet

        if (packet is ClientboundSetEntityMotionPacket
            || packet is ClientboundMoveEntityPacket
            || packet is ClientboundTeleportEntityPacket
            || packet is ClientboundPingPacket
            || packet is ClientboundPlayerLookAtPacket
            || packet is ClientboundPlayerPositionPacket
        ) {
            if (delay) {
                event.cancel()
                packets.add(packet as Packet<in ClientPacketListener?>?)
            }
        }

        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundExplodePacket) {
            lag = System.currentTimeMillis()
        }

        if (packet is ClientboundSetEntityMotionPacket) {
            if (packet.id == player.id && System.currentTimeMillis() - lag >= 100) {
                val chackFall = packet.movement.y > 0.0 && (packet.movement.x != 0.0 || packet.movement.z != 0.0)
                if (chackFall && !delay) {
                    delay = true
                    event.cancel()
                    packets.add(packet as Packet<in ClientPacketListener?>)
                    startDelay = System.currentTimeMillis()
                }
                yaw = Mth.wrapDegrees(Math.toDegrees(atan2(packet.movement.z, packet.movement.x)) + 90.0).toFloat();
            }
        }
    }

    private fun NonNullContext.flush() {
        if (delay) {
            while (!packets.isEmpty()) {
                val packet = packets.poll()
                if (packet != null) {
                    packet.handle(connection)
                }
            }
            delay = false
            jump = true
        }
    }

}
