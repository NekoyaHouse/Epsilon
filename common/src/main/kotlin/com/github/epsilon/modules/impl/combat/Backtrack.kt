package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.Velocity
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.BundlePacket
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.VecDelta
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.ArrayDeque
import java.util.concurrent.ThreadLocalRandom

object Backtrack : Module("Backtrack", Category.COMBAT) {

    private enum class TargetMode {
        Attack,
        Range,
    }

    private val minRange by doubleSetting("Min Range", 2.5, 0.0, 10.0, 0.1)
    private val maxRange by doubleSetting("Max Range", 5.0, 0.0, 10.0, 0.1)
    private val minDelay by intSetting("Min Delay", 100, 0, 1000, 10)
    private val maxDelay by intSetting("Max Delay", 150, 0, 1000, 10)
    private val nextBacktrackMin by intSetting("Next Backtrack Min", 0, 0, 2000, 10)
    private val nextBacktrackMax by intSetting("Next Backtrack Max", 10, 0, 2000, 10)
    private val trackingBuffer by intSetting("Tracking Buffer", 500, 0, 2000, 10)
    private val chance by intSetting("Chance", 50, 0, 100, 1)
    private val pauseOnHurtTime by boolSetting("Pause On Hurt Time", false)
    private val hurtTime by intSetting(
        "Hurt Time", 3, 0, 10, 1, dependency = { pauseOnHurtTime })
    private val targetModeSetting: EnumSetting<TargetMode> = enumSetting("Target Mode", TargetMode.Attack)
    private val targetMode by targetModeSetting
    private val lastAttackTime by intSetting("Last Attack Time", 1000, 0, 5000, 50)
    private val players by boolSetting("Players", true, dependency = { targetMode == TargetMode.Range })
    private val mobs by boolSetting("Mobs", false, dependency = { targetMode == TargetMode.Range })
    private val animals by boolSetting("Animals", false, dependency = { targetMode == TargetMode.Range })
    private val villagers by boolSetting("Villagers", false, dependency = { targetMode == TargetMode.Range })
    private val ambient by boolSetting("Ambient", false, dependency = { targetMode == TargetMode.Range })
    private val water by boolSetting("Water", false, dependency = { targetMode == TargetMode.Range })
    private val others by boolSetting("Others", false, dependency = { targetMode == TargetMode.Range })
    private val invisible by boolSetting("Invisible", true, dependency = { targetMode == TargetMode.Range })
    private val esp by boolSetting("ESP", true)
    private val sideColor by colorSetting("Side Color", Color(255, 255, 255, 35), dependency = { esp })
    private val outlineColor by colorSetting("Outline Color", Color(255, 255, 255, 190), dependency = { esp })

    private val packetLock = Any()
    private val packetQueue: ArrayDeque<QueuedPacket> = ArrayDeque()

    private var target: LivingEntity? = null
    private var trackedPosition = Vec3.ZERO

    @Volatile
    private var backtracking = false

    private var chancePassed = false
    private var lastAttackAt = Long.MIN_VALUE
    private var lastInRangeAt = Long.MIN_VALUE
    private var nextAllowedAt = 0L
    private var currentDelay = 0

    init {
        nonNullListener<AttackEntityEvent> { event ->
            val now = System.currentTimeMillis()
            synchronized(packetLock) {
                lastAttackAt = now
                chancePassed = rollChance()
            }

            if (targetMode == TargetMode.Attack) {
                val living = event.entity as? LivingEntity
                if (living != null) {
                    processTarget(living, now)
                }
            }
        }

        nonNullListener<ClientTickEvent.Pre>(EventPriority.HIGH) {
            val now = System.currentTimeMillis()
            if (targetMode == TargetMode.Range) {
                val rangeTarget = findRangeTarget()
                if (rangeTarget == null) {
                    clearAndReplay(true)
                } else {
                    processTarget(rangeTarget, now)
                }
            }

            var pending: MutableList<Packet<ClientPacketListener>> = ArrayList()
            synchronized(packetLock) {
                if (Velocity.ownsIncomingDelayQueue()) {
                    backtracking = false
                    pending = drainAllLocked()
                } else {
                    backtracking = target != null && shouldBacktrackLocked(target!!, now)
                    if (backtracking) {
                        pending = drainExpiredLocked(now - currentDelay)
                    } else if (!packetQueue.isEmpty()) {
                        pending = drainAllLocked()
                        clearTargetLocked(true)
                    } else {
                        pending = ArrayList()
                    }
                }

                if (packetQueue.isEmpty()) {
                    currentDelay = randomBetween(minDelay, maxDelay)
                }
            }
            handlePackets(pending)
        }

        handler<PacketEvent.Receive>(EventPriority.LOWEST) { event ->
            val packet = event.packet
            var pending: MutableList<Packet<ClientPacketListener>> = ArrayList()
            var skipSchedule = false

            synchronized(packetLock) {
                when {
                    packet is ClientboundDisconnectPacket -> {
                        packetQueue.clear()
                        resetStateLocked(false)
                        skipSchedule = true
                    }

                    NonNullContext.current == null -> {
                        packetQueue.clear()
                        resetStateLocked(false)
                        skipSchedule = true
                    }

                    Velocity.ownsIncomingDelayQueue() -> {
                        skipSchedule = true
                    }

                    packet is ClientboundPlayerPositionPacket ||
                        (packet is ClientboundSetHealthPacket && packet.health <= 0.0f) -> {
                        pending = drainAllLocked()
                        clearTargetLocked(true)
                    }

                    isPassThrough(packet) -> {
                        skipSchedule = true
                    }

                    backtracking || !packetQueue.isEmpty() -> {
                        val newTrackedPosition = trackTargetPosition(packet)
                        if (newTrackedPosition != null && isServerPositionCloser(newTrackedPosition)) {
                            pending = drainAllLocked()
                        } else {
                            event.cancel()
                            @Suppress("UNCHECKED_CAST")
                            packetQueue.addLast(
                                QueuedPacket(
                                    packet as Packet<ClientPacketListener>,
                                    System.currentTimeMillis()
                                )
                            )
                            skipSchedule = true
                        }
                    }
                }
            }

            if (!skipSchedule) {
                schedulePackets(pending)
            }
        }

        handler<Render3DEvent> {
            var box: AABB
            synchronized(packetLock) {
                val currentTarget = target
                if (!esp || currentTarget == null || !currentTarget.isAlive) return@handler
                box = currentTarget.boundingBox.move(trackedPosition.subtract(currentTarget.position()))
            }
            Render3DScheduler.addFilledBox(box, sideColor)
            Render3DScheduler.addOutlineBox(box, outlineColor, 1.5f)
        }

        handler<GameLeftEvent> {
            clearWithoutReplay()
        }

        handler<LevelUpdateEvent> {
            clearWithoutReplay()
        }
    }

    override fun getInfo(): String? {
        synchronized(packetLock) {
            return if (backtracking && !packetQueue.isEmpty()) currentDelay.toString() + "ms"
            else targetModeSetting.translatedValue
        }
    }

    override fun onEnable() {
        synchronized(packetLock) {
            resetStateLocked(true)
            chancePassed = rollChance()
            currentDelay = randomBetween(minDelay, maxDelay)
        }
    }

    override fun onDisable() {
        val pending: MutableList<Packet<ClientPacketListener>>
        synchronized(packetLock) {
            pending = drainAllLocked()
            resetStateLocked(false)
        }
        handlePackets(pending)
    }

    fun isLagging(): Boolean {
        synchronized(packetLock) {
            return isEnabled && !packetQueue.isEmpty()
        }
    }

    context(nn: NonNullContext)
    private fun processTarget(enemy: LivingEntity, now: Long) {
        val pending: MutableList<Packet<ClientPacketListener>>
        synchronized(packetLock) {
            if (!shouldBacktrackLocked(enemy, now)) return

            pending = ArrayList()
            if (enemy !== target) {
                pending.addAll(drainAllLocked())
                target = enemy
                trackedPosition = enemy.getPositionCodec().getBase()
            }
            backtracking = true
        }
        handlePackets(pending)
    }

    context(nn: NonNullContext)
    private fun shouldBacktrackLocked(candidate: LivingEntity, now: Long): Boolean {
        if (!isValidTarget(candidate)) return false

        val distance = candidate.boundingBox.distanceToSqr(player.eyePosition)
        val min = Math.min(minRange, maxRange)
        val max = Math.max(minRange, maxRange)
        val inRange = distance >= min * min && distance <= max * max
        if (inRange) {
            lastInRangeAt = now
        }

        val withinTrackingBuffer =
            lastInRangeAt != Long.MIN_VALUE && now - lastInRangeAt <= trackingBuffer
        val attackedRecently =
            lastAttackAt != Long.MIN_VALUE && now - lastAttackAt <= lastAttackTime

        return (inRange || withinTrackingBuffer) &&
            player.tickCount > 10 &&
            chancePassed &&
            now >= nextAllowedAt &&
            attackedRecently &&
            !Velocity.blocksBacktrack() &&
            (!pauseOnHurtTime || candidate.hurtTime < hurtTime)
    }

    context(nn: NonNullContext)
    private fun isValidTarget(candidate: LivingEntity): Boolean {
        if (candidate === player || !candidate.isAlive || candidate.isDeadOrDying) return false
        if (candidate.level() !== level || AntiBot.isBot(candidate)) return false
        if (TargetManager.isSameTeam(candidate)) return false
        return candidate !is Player || !FriendManager.isFriend(candidate)
    }

    context(nn: NonNullContext)
    private fun findRangeTarget(): LivingEntity? {
        val min = Math.min(minRange, maxRange)
        val max = Math.max(minRange, maxRange)
        val minSquared = min * min
        val maxSquared = max * max
        val eyePosition = player.eyePosition
        var nearest: LivingEntity? = null
        var nearestDistance = Double.MAX_VALUE

        for (entity in level.entitiesForRendering()) {
            val living = entity as? LivingEntity ?: continue
            if (entity is ArmorStand) continue
            if (!isValidTarget(living) || !isEnabledRangeTarget(living)) continue

            val distance = living.boundingBox.distanceToSqr(eyePosition)
            if (distance < minSquared || distance > maxSquared || distance >= nearestDistance) continue
            nearest = living
            nearestDistance = distance
        }
        return nearest
    }

    private fun isEnabledRangeTarget(entity: LivingEntity): Boolean {
        if (entity is Player) {
            return players && (invisible || !entity.isInvisible)
        }
        if (entity is Villager) return villagers
        if (entity is Monster) return mobs
        if (entity is Animal) return animals

        val category: MobCategory = entity.type.getCategory()
        return when (category) {
            MobCategory.AMBIENT, MobCategory.WATER_AMBIENT -> ambient
            MobCategory.WATER_CREATURE, MobCategory.AXOLOTLS, MobCategory.UNDERGROUND_WATER_CREATURE -> water
            else -> others
        }
    }

    private fun isPassThrough(packet: Packet<*>): Boolean {
        if (packet is ClientboundSystemChatPacket || packet is ClientboundPlayerChatPacket) {
            return true
        }
        return packet is ClientboundSoundPacket && packet.sound.value() === SoundEvents.PLAYER_HURT
    }

    private fun trackTargetPosition(packet: Packet<*>): Vec3? {
        val currentTarget = target ?: return null
        var position = trackSinglePacket(packet)

        if (packet is BundlePacket<*>) {
            for (subPacket in packet.subPackets()) {
                val subPosition = trackSinglePacket(subPacket)
                if (subPosition != null) position = subPosition
            }
        }
        return position
    }

    private fun trackSinglePacket(packet: Packet<*>): Vec3? {
        val currentTarget = target ?: return null
        val position: Vec3
        if (packet is ClientboundMoveEntityPacket && packet.getEntity(mc.level ?: return null) === currentTarget) {
            position = decodeMovementDelta(trackedPosition, packet.positionDelta)
        } else if (packet is ClientboundTeleportEntityPacket && packet.id() == currentTarget.id) {
            position = packet.change().position()
        } else if (packet is ClientboundEntityPositionSyncPacket && packet.id() == currentTarget.id) {
            position = packet.position().endPosition()
        } else {
            return null
        }

        trackedPosition = position
        return position
    }

    private fun isServerPositionCloser(serverPosition: Vec3): Boolean {
        val currentTarget = target ?: return false
        val eyePosition = mc.player?.eyePosition ?: return false
        val currentBox = currentTarget.boundingBox
        val serverBox = currentBox.move(serverPosition.subtract(currentTarget.position()))
        return serverBox.distanceToSqr(eyePosition) < currentBox.distanceToSqr(eyePosition)
    }

    private fun clearAndReplay(applyCooldown: Boolean) {
        val pending: MutableList<Packet<ClientPacketListener>>
        synchronized(packetLock) {
            pending = drainAllLocked()
            clearTargetLocked(applyCooldown)
        }
        handlePackets(pending)
    }

    private fun clearWithoutReplay() {
        synchronized(packetLock) {
            packetQueue.clear()
            resetStateLocked(false)
        }
    }

    private fun resetStateLocked(resetTimers: Boolean) {
        target = null
        trackedPosition = Vec3.ZERO
        backtracking = false
        lastInRangeAt = Long.MIN_VALUE
        if (resetTimers) {
            lastAttackAt = Long.MIN_VALUE
            nextAllowedAt = 0L
        }
    }

    private fun clearTargetLocked(applyCooldown: Boolean) {
        if (target != null && applyCooldown) {
            nextAllowedAt = System.currentTimeMillis() + randomBetween(nextBacktrackMin, nextBacktrackMax)
        }
        target = null
        trackedPosition = Vec3.ZERO
        backtracking = false
        lastInRangeAt = Long.MIN_VALUE
    }

    private fun drainExpiredLocked(timestamp: Long): MutableList<Packet<ClientPacketListener>> {
        if (packetQueue.isEmpty() || packetQueue.peekFirst().timestamp > timestamp) return ArrayList()

        val pending: MutableList<Packet<ClientPacketListener>> = ArrayList()
        while (!packetQueue.isEmpty() && packetQueue.peekFirst().timestamp <= timestamp) {
            pending.add(packetQueue.removeFirst().packet)
        }
        return pending
    }

    private fun drainAllLocked(): MutableList<Packet<ClientPacketListener>> {
        if (packetQueue.isEmpty()) return ArrayList()

        val pending: MutableList<Packet<ClientPacketListener>> = ArrayList(packetQueue.size)
        while (!packetQueue.isEmpty()) {
            pending.add(packetQueue.removeFirst().packet)
        }
        return pending
    }

    private fun schedulePackets(packets: MutableList<Packet<ClientPacketListener>>) {
        if (packets.isNotEmpty()) mc.execute { handlePackets(packets) }
    }

    private fun handlePackets(packets: MutableList<Packet<ClientPacketListener>>) {
        val listener = mc.connection ?: return
        for (packet in packets) {
            packet.handle(listener)
        }
    }

    private fun rollChance(): Boolean {
        return ThreadLocalRandom.current().nextInt(100) < chance
    }

    private fun randomBetween(first: Int, second: Int): Int {
        val min = Math.min(first, second)
        val max = Math.max(first, second)
        return if (min == max) min else ThreadLocalRandom.current().nextInt(min, max + 1)
    }

    private fun decodeRelative(base: Vec3, x: Long, y: Long, z: Long): Vec3 {
        val decodedX = if (x == 0L) base.x else (Math.round(base.x * 4096.0) + x) / 4096.0
        val decodedY = if (y == 0L) base.y else (Math.round(base.y * 4096.0) + y) / 4096.0
        val decodedZ = if (z == 0L) base.z else (Math.round(base.z * 4096.0) + z) / 4096.0
        return Vec3(decodedX, decodedY, decodedZ)
    }

    private fun decodeMovementDelta(base: Vec3, delta: VecDelta): Vec3 {
        var position = base
        if (delta is VecDelta.Linear) {
            return decodeRelative(position, delta.xa().toLong(), delta.ya().toLong(), delta.za().toLong())
        }
        if (delta is VecDelta.Stepped) {
            for (step in delta.steps()) {
                position = decodeRelative(position, step.xa().toLong(), step.ya().toLong(), step.za().toLong())
            }
        }
        return position
    }

    private data class QueuedPacket(val packet: Packet<ClientPacketListener>, val timestamp: Long)

}
