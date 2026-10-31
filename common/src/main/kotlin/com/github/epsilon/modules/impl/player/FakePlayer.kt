package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.mojang.authlib.GameProfile
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.Entity
import java.nio.charset.StandardCharsets
import java.util.UUID

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
        nonNullHandler<PlayerTickEvent.Pre> {
            if (record) {
                positions.add(PlayerState(player.x, player.y, player.z, player.yRot, player.xRot))
                return@nonNullHandler
            }

            val fake = fakePlayer ?: return@nonNullHandler
            if (playback && positions.isNotEmpty()) {
                if (movementTick >= positions.size) {
                    movementTick = 0
                }

                val state = positions[movementTick]
                movementTick++

                fake.snapTo(state.x, state.y, state.z, state.yaw, state.pitch)
                fake.setYRot(state.yaw)
                fake.setXRot(state.pitch)
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
        val nn = NonNullContext.current
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
            fake.setId(-66123666)
            fake.copyPosition(player)
            fake.setYRot(player.yRot)
            fake.setXRot(player.xRot)
            fake.setYHeadRot(player.yHeadRot)
            fake.setHealth(player.health)
            fake.setAbsorptionAmount(player.absorptionAmount)

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
