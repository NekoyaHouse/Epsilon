package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.network.NetworkUtils
import com.github.epsilon.utils.safety.player
import com.github.epsilon.kernel.jvm.runSafe
import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.screens.RecoverWorldDataScreen
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.handshake.ClientIntentionPacket
import net.minecraft.network.protocol.login.ServerboundHelloPacket
import net.minecraft.world.entity.Entity
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object Blink : Module("Blink", Category.MOVEMENT) {

    private val tick by doubleSetting("Tick", 30.0, 5.0..200.0, 1.0)
    private val SlowRelease by boolSetting("Slow Release", true)
    private val slowMove by boolSetting("Slow Move", false)
    private val slowMoveTick by doubleSetting("Slow Move Tick", 5.0, 2.0..5.0, 1.0, { slowMove })
    private val fakePlayer by boolSetting("Fake Player", false)

    private val packets: Queue<Packet<*>> = ConcurrentLinkedQueue()
    var localPlayer: RemotePlayer? = null

    private var serverX = 0.0
    private var serverY = 0.0
    private var serverZ = 0.0
    private var prevServerX = 0.0
    private var prevServerY = 0.0
    private var prevServerZ = 0.0
    private var serverYRot = 0.0f
    private var serverXRot = 0.0f
    private var serverYHeadRot = 0.0f
    private var prevServerYRot = 0.0f
    private var prevServerXRot = 0.0f
    private var prevServerYHeadRot = 0.0f
    private var lastLerpX = 0.0
    private var lastLerpY = 0.0
    private var lastLerpZ = 0.0

    init {
        nonNullHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet is ServerboundHelloPacket || packet is ClientIntentionPacket) return@nonNullHandler
            event.cancel()
            packets.add(packet)
        }

        nonNullHandler<Render3DEvent> {
            val fake = localPlayer
            if (fake != null && fakePlayer) {
                val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
                val lerpX = prevServerX + (serverX - prevServerX) * pt
                val lerpY = prevServerY + (serverY - prevServerY) * pt
                val lerpZ = prevServerZ + (serverZ - prevServerZ) * pt
                fake.xOld = lastLerpX
                fake.yOld = lastLerpY
                fake.zOld = lastLerpZ
                fake.setPos(lerpX, lerpY, lerpZ)
                lastLerpX = lerpX
                lastLerpY = lerpY
                lastLerpZ = lerpZ

                val lerpYRot = prevServerYRot + (serverYRot - prevServerYRot) * pt
                val lerpXRot = prevServerXRot + (serverXRot - prevServerXRot) * pt
                val lerpYHeadRot = prevServerYHeadRot + (serverYHeadRot - prevServerYHeadRot) * pt
                fake.yRot = lerpYRot
                fake.xRot = lerpXRot
                fake.yHeadRot = lerpYHeadRot
                fake.yBodyRot = lerpYRot
            }
        }

        nonNullHandler<SendPositionEvent> {
            if (mc.gui.screen() is RecoverWorldDataScreen && isEnabled) {
                isEnabled = false
            }

            if (SlowRelease) {
                if (getBlinkTicks() > tick) {
                    releaseTick()
                }
            } else {
                if (getBlinkTicks() > tick) {
                    releaseAll()
                }
            }
            if (slowMove) {
                if (player.tickCount % slowMoveTick.toInt() == 0) {
                    releaseTick()
                }
            }
        }
    }

    override fun onEnable() {
        runSafe {
            serverX = player.x
            prevServerX = serverX
            serverY = player.y
            prevServerY = serverY
            serverZ = player.z
            prevServerZ = serverZ
            serverYRot = player.yRot
            prevServerYRot = serverYRot
            serverXRot = player.xRot
            prevServerXRot = serverXRot
            serverYHeadRot = player.yHeadRot
            prevServerYHeadRot = serverYHeadRot
            if (fakePlayer) {
                val fake = RemotePlayer(
                    level,
                    GameProfile(UUID.nameUUIDFromBytes("".toByteArray(StandardCharsets.UTF_8)), "")
                )
                fake.id = -1337
                fake.copyPosition(player)
                fake.yRot = player.yRot
                fake.xRot = player.xRot
                fake.yHeadRot = player.yHeadRot
                fake.health = player.health
                fake.absorptionAmount = player.absorptionAmount
                level.addEntity(fake)
                localPlayer = fake
            }
        }
    }

    override fun onDisable() {
        runSafe {
            if (fakePlayer) {
                localPlayer?.let { level.removeEntity(it.id, Entity.RemovalReason.DISCARDED) }
            }
            releaseAll()
            packets.clear()
        }
    }

    private fun handlePlayerMove(packet: ServerboundMovePlayerPacket) {
        prevServerX = serverX
        prevServerY = serverY
        prevServerZ = serverZ
        serverX = packet.getX(serverX)
        serverY = packet.getY(serverY)
        serverZ = packet.getZ(serverZ)

        prevServerYRot = serverYRot
        prevServerXRot = serverXRot
        prevServerYHeadRot = serverYHeadRot
        serverYRot = packet.getYRot(serverYRot)
        serverXRot = packet.getXRot(serverXRot)
        if (packet.hasRotation()) {
            serverYHeadRot = packet.getYRot(serverYHeadRot)
        }
    }

    private fun getBlinkTicks(): Int {
        return Math.toIntExact(packets.stream().filter { it is ServerboundMovePlayerPacket }.count())
    }

    private fun releaseTick() {
        while (packets.isNotEmpty()) {
            val poll = packets.poll() ?: break
            NetworkUtils.sendPacketNoEvent(poll)
            if (poll is ServerboundMovePlayerPacket) {
                handlePlayerMove(poll)
                break
            }
        }
    }

    private fun releaseAll() {
        if (packets.isNotEmpty()) {
            for (packet in packets) {
                NetworkUtils.sendPacketNoEvent(packet)
                if (packet is ServerboundMovePlayerPacket) {
                    handlePlayerMove(packet)
                }
            }
        }
    }

}
