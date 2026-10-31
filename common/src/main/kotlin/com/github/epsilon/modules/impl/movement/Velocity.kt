package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.AntiBot
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import java.util.*
import kotlin.math.atan2

object Velocity : Module("Velocity", Category.MOVEMENT) {

    // Packet receive events run on Netty while delayed packets are flushed on the client thread.
    private val packetLock = Any()

    enum class Mode {
        Cancel,
        Reduce,
        Delay,
    }

    private val modeSetting: EnumSetting<Mode> = enumSetting("Mode", Mode.Cancel, onChanged = ::onModeChanged)
    val mode by modeSetting
    private val serverMotion by boolSetting("Server Motion", true, { mode == Mode.Cancel })
    private val explosion by boolSetting("Explosion", true, { mode == Mode.Cancel })
    private val explosionOnlyBlock by boolSetting(
        "Explosion Only Block", false,
        { mode == Mode.Cancel && explosion }
    )
    val waterPush by boolSetting("No Water Push", true, { mode == Mode.Cancel })
    val entityPush by boolSetting("No Entity Push", true, { mode == Mode.Cancel })
    val blockPush by boolSetting("No Block Push", true, { mode == Mode.Cancel })
    private val attackCounts by intSetting("Attack Counts", 1, 1..5, 1, { mode == Mode.Reduce })
    private val sprintTicks by intSetting("Sprint Ticks", 3, 1..5, 1, { mode == Mode.Reduce })
    private val maxDelay by intSetting("Max Delay", 1000, 0..1000, 50, { mode == Mode.Reduce })
    private val swingHand by boolSetting("Swing Hand", false, { mode == Mode.Reduce })
    private val delayTicks by intSetting("Delay Ticks", 3, 1..5, 1, { mode == Mode.Delay })
    private val jumpReset by boolSetting("Jump Reset", false, { mode == Mode.Delay })

    @Volatile
    private var lag = 0L

    @Volatile
    private var delayLag = 0L

    @Volatile
    private var startDelay = 0L

    @Volatile
    var attackQueue = 0

    @Volatile
    private var sprintQueue = 0

    @Volatile
    var yaw = 0.0f

    @Volatile
    var delay = false

    @Volatile
    private var jump = false

    @Volatile
    private var delayTicksRemaining = 0

    private val packets: Queue<Packet<*>> = ArrayDeque()
    private val delayPackets: Queue<Packet<*>> = ArrayDeque()

    init {
        safeEventHandler<PacketEvent.Receive> { event ->
            synchronized(packetLock) {
                when (mode) {
                    Mode.Cancel -> cancelPacket(event)
                    Mode.Reduce -> reducePacket(event)
                    Mode.Delay -> delayPacket(event)
                }
            }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            if (mode != Mode.Reduce) return@safeEventHandler

            if (delay && (player.onGround() || System.currentTimeMillis() - lag < 100 ||
                        System.currentTimeMillis() - startDelay >= maxDelay) && flush()
            ) {
                if (System.currentTimeMillis() - lag >= 100L) {
                    attackQueue = attackCounts
                    sprintQueue = sprintTicks
                }
            }

            if (sprintQueue >= 1 && KillAura.target == null && !Scaffold.isEnabled) {
                RotationManager.setRotations(
                    Rotation(yaw, RotationManager.getRotation().pitch),
                    180.0,
                    Priority.Highest
                )
            }
        }

        safeEventHandler<PlayerTickEvent.Pre>(EventPriority.LOW) {
            var pending: List<Packet<*>> = emptyList()
            synchronized(packetLock) {
                if (mode != Mode.Delay || delayTicksRemaining <= 0) return@safeEventHandler
                if (--delayTicksRemaining == 0) {
                    pending = drain(delayPackets)
                }
            }
            handlePackets(pending)
        }

        safeEventHandler<PlayerTickEvent.Pre>(EventPriority.LOW) {
            if (mode != Mode.Reduce) return@safeEventHandler

            if (attackQueue >= 1) {
                val hitResult = RotationManager.getHitResult()
                if (hitResult is EntityHitResult && hitResult.entity is Player) {
                    val target = hitResult.entity as Player
                    if (target.isAlive && !AntiBot.isBot(target)) {
                        gameMode.attack(player, target)
                        SwingMode.of(swingHand).attackSwing(InteractionHand.MAIN_HAND)
                    }
                }
                attackQueue--
            }
        }

        safeEventHandler<KeyboardInputEvent> { event ->
            if (mode == Mode.Delay && jumpReset && player.onGround() && player.hurtTime == 9) {
                event.jump = true
            }

            if (mode != Mode.Reduce) return@safeEventHandler

            val hitResult = RotationManager.getHitResult()
            if (delay && player.fallDistance == 0.0 && hitResult is EntityHitResult &&
                hitResult.entity is Player && !AntiBot.isBot(hitResult.entity as Player)
            ) {
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

    fun ownsIncomingDelayQueue(): Boolean {
        return isEnabled && (mode == Mode.Reduce && delay || mode == Mode.Delay && delayTicksRemaining > 0)
    }

    fun blocksBacktrack(): Boolean {
        return isEnabled && (mode == Mode.Reduce && (delay || attackQueue > 0) ||
                mode == Mode.Delay && delayTicksRemaining > 0)
    }

    override fun getInfo(): String {
        return modeSetting.translatedValue + when (mode) {
            Mode.Cancel -> ""
            Mode.Reduce -> " " + (if (delay) (System.currentTimeMillis() - startDelay).toString() + "ms" else "")
            Mode.Delay -> " " + delayTicksRemaining + "t"
        }
    }

    override fun onDisable() {
        flush()
        flushDelay()
        resetReduceState()
        resetDelayState()
    }

    private fun onModeChanged(newMode: Mode) {
        if (newMode != Mode.Reduce) resetReduceState()
        if (newMode != Mode.Delay) resetDelayState()
    }

    context(nn: NonNullContext)
    private fun cancelPacket(event: PacketEvent.Receive) {
        val packet = event.packet
        if (serverMotion && packet is ClientboundSetEntityMotionPacket && packet.id == player.id) {
            event.cancel()
            return
        }

        if (explosion && packet is ClientboundExplodePacket &&
            (!explosionOnlyBlock || PlayerUtils.isInBlock())
        ) {
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

    context(nn: NonNullContext)
    private fun reducePacket(event: PacketEvent.Receive) {
        val packet = event.packet

        if (packet is ClientboundDisconnectPacket) {
            resetReduceState()
            return
        }

        if (delay && (packet is ClientboundSetEntityMotionPacket ||
                    packet is ClientboundMoveEntityPacket ||
                    packet is ClientboundTeleportEntityPacket ||
                    packet is ClientboundPingPacket ||
                    packet is ClientboundPlayerLookAtPacket ||
                    packet is ClientboundPlayerPositionPacket)
        ) {
            event.cancel()
            packets.add(packet)
        }

        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundExplodePacket) {
            lag = System.currentTimeMillis()
        }

        if (packet is ClientboundSetEntityMotionPacket && packet.id == player.id) {
            val movement = packet.movement
            if (System.currentTimeMillis() - lag >= 100L) {
                val knockback = movement.y > 0.0 && (movement.x != 0.0 || movement.z != 0.0)
                if (knockback && !delay) {
                    delay = true
                    event.cancel()
                    packets.add(packet)
                    startDelay = System.currentTimeMillis()
                }
            }
            yaw = Mth.wrapDegrees((atan2(movement.z, movement.x)).toDegrees().toFloat() + 90.0f)
        }
    }

    context(nn: NonNullContext)
    private fun delayPacket(event: PacketEvent.Receive) {
        val packet = event.packet

        if (packet is ClientboundDisconnectPacket) {
            resetDelayState()
            return
        }

        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundExplodePacket) {
            delayLag = System.currentTimeMillis()
        }

        if (delayTicksRemaining > 0) {
            event.cancel()
            delayPackets.add(packet)
            return
        }

        if (packet is ClientboundSetEntityMotionPacket && packet.id == player.id &&
            System.currentTimeMillis() - delayLag >= 100L
        ) {
            val movement = packet.movement
            val knockback = movement.y > 0.0 && (movement.x != 0.0 || movement.z != 0.0)
            if (knockback) {
                delayTicksRemaining = delayTicks
            }
        }
    }

    private fun flush(): Boolean {
        val pending: List<Packet<*>>
        synchronized(packetLock) {
            if (!delay) return false

            delay = false
            pending = drain(packets)
        }

        handlePackets(pending)
        jump = true
        return true
    }

    private fun flushDelay() {
        val pending: List<Packet<*>>
        synchronized(packetLock) {
            delayTicksRemaining = 0
            pending = drain(delayPackets)
        }
        handlePackets(pending)
    }

    private fun resetReduceState() {
        synchronized(packetLock) {
            delay = false
            packets.clear()
            lag = 0L
            startDelay = 0L
            attackQueue = 0
            sprintQueue = 0
            jump = false
            yaw = 0.0f
        }
    }

    private fun resetDelayState() {
        synchronized(packetLock) {
            delayTicksRemaining = 0
            delayPackets.clear()
            delayLag = 0L
        }
    }

    private fun <T> drain(queue: Queue<T>): List<T> {
        val drained: MutableList<T> = ArrayList(queue.size)
        while (true) {
            val packet = queue.poll() ?: break
            drained.add(packet)
        }
        return drained
    }

    @Suppress("UNCHECKED_CAST")
    private fun handlePackets(packets: List<Packet<*>>) {
        val listener = mc.connection ?: return

        for (packet in packets) {
            (packet as Packet<ClientPacketListener>).handle(listener)
        }
    }

}
