package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.level
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.mojang.authlib.GameProfile
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.Entity
import java.nio.charset.StandardCharsets
import java.util.*

object FakePlayer : Module("Fake Player", Category.PLAYER) {

    private val nameSetting by stringSetting("Name", "BC_zxy")
    private val copyInventory by boolSetting("Copy Inventory", true)
    private val record by boolSetting("Record", false)
    private val playback by boolSetting("Playback", false)

    var fakePlayer: RemotePlayer? = null

    private var movementTick = 0
    private var deathTime = 0
    private val positions: MutableList<PlayerState> = ArrayList()

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (record) {
                positions.add(PlayerState(player.x, player.y, player.z, player.yRot, player.xRot))
                return@safeEventHandler
            }

            val fake = fakePlayer ?: return@safeEventHandler
            if (playback && positions.isNotEmpty()) {
                if (movementTick >= positions.size) {
                    movementTick = 0
                }

                val state = positions[movementTick]
                movementTick++

                fake.snapTo(state.x, state.y, state.z, state.yaw, state.pitch)
                fake.yRot = state.yaw
                fake.xRot = state.pitch
                fake.setYHeadRot(state.yaw)
            } else {
                movementTick = 0
            }

            if (fake.isDeadOrDying) {
                deathTime++
                if (deathTime > 10) {
                    toggle()
                }
            } else {
                deathTime = 0
            }
        }
    }

    override fun onEnable() {
        val nn = NonNullContext.instance
        if (nn == null) {
            toggle()
            return
        }

        context(nn) {
            movementTick = 0
            deathTime = 0
            if (!record) {
                positions.clear()
            }

            val uuid = UUID.nameUUIDFromBytes(("fake-player:" + nameSetting).toByteArray(StandardCharsets.UTF_8))
            val fake = RemotePlayer(level, GameProfile(uuid, nameSetting))
            fakePlayer = fake
            fake.id = -66123666
            fake.copyPosition(player)
            fake.yRot = player.yRot
            fake.xRot = player.xRot
            fake.setYHeadRot(player.yHeadRot)
            fake.health = player.health
            fake.absorptionAmount = player.absorptionAmount

            if (copyInventory) {
                fake.inventory.replaceWith(player.inventory)
            }

            level.addEntity(fake)
        }
    }

    override fun onDisable() {
        val level = mc.level
        val fake = fakePlayer
        if (level != null && fake != null) {
            level.removeEntity(fake.id, Entity.RemovalReason.DISCARDED)
        }
        fakePlayer = null
        positions.clear()
        movementTick = 0
        deathTime = 0
    }

    private data class PlayerState(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
    )

}
