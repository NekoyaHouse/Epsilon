package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.kernel.gameplay.combat.CrystalDamage.damage
import com.github.epsilon.kernel.gameplay.combat.CrystalDamage.snapshot
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.mojang.authlib.GameProfile
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.util.*

object FakePlayer : Module("Fake Player", Category.PLAYER) {

    private val recordButton by buttonSetting("Record / Stop", { recording = !recording }, { !playing })
    private val playButton by buttonSetting("Play / Stop", { playing = !playing }, { !recording })
    private val copyInventory by boolSetting("Copy Inventory", true)
    private val playerName by stringSetting("Name", "OLD_GOD")

    private var fakePlayer: RemotePlayer? = null
    private val frames = ArrayList<MovementFrame>()
    private var playbackIndex = 0
    private var recording by mutableStateOf(false)
    private var playing by mutableStateOf(false)

    init {
        onEnable {
            runSafe {
                val fake = RemotePlayer(
                    level,
                    GameProfile(UUID.fromString("66123666-6666-6666-6666-613376666600"), playerName)
                )
                fake.id = -4204204
                fake.copyPosition(player)
                fake.yHeadRot = player.yHeadRot
                fake.yBodyRot = player.yBodyRot
                if (copyInventory) {
                    for (slot in arrayOf(
                        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
                    )) {
                        fake.setItemSlot(slot, player.getItemBySlot(slot).copy())
                    }
                    fake.attributes.assignAllValues(player.attributes)
                }
                level.addEntity(fake)
                fake.addEffect(MobEffectInstance(MobEffects.REGENERATION, 9999, 2))
                fake.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 9999, 4))
                fake.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 9999, 1))
                fakePlayer = fake
            } ?: run { isEnabled = false }
        }

        onDisable {
            fakePlayer?.let {
                it.setRemoved(Entity.RemovalReason.DISCARDED)
                it.onClientRemoval()
            }
            fakePlayer = null
            frames.clear()
            playbackIndex = 0
            recording = false
            playing = false
        }

        handler<GameLeftEvent> {
            isEnabled = false
        }

        handler<LevelUpdateEvent> {
            isEnabled = false
        }

        safeEventHandler<SendPositionEvent> {
            val fake = fakePlayer ?: return@safeEventHandler
            if (recording) {
                frames.add(MovementFrame(player.position(), player.yRot, player.xRot))
                return@safeEventHandler
            }

            if (fake.tickCount % 8 == 0) {
                fake.health += 0.5f
            }
            if (fake.position().distanceToSqr(player.position()) > 400.0) {
                isEnabled = false
                return@safeEventHandler
            }

            if (playing && frames.isNotEmpty()) {
                val frame = frames[playbackIndex]
                fake.yRot = frame.yaw
                fake.xRot = frame.pitch
                fake.yHeadRot = frame.yaw
                fake.syncPacketPositionCodec(frame.position.x, frame.position.y, frame.position.z)
                fake.moveOrInterpolateTo(frame.position, frame.yaw, frame.pitch)
                playbackIndex = (playbackIndex + 1) % frames.size
            } else {
                playbackIndex = 0
            }

            if (!fake.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack(Items.TOTEM_OF_UNDYING))
            }
        }

        safeEventHandler<PacketEvent.Apply> { event ->
            val packet = event.packet as? ClientboundExplodePacket ?: return@safeEventHandler
            val fake = fakePlayer ?: return@safeEventHandler
            if (fake.hurtTime != 0) return@safeEventHandler

            val explosionDamage = damage(snapshot(fake), packet.center()).toFloat()
            fake.handleDamageEvent(level.damageSources().generic())
            fake.health += fake.absorptionAmount - explosionDamage
            if (fake.isDeadOrDying) {
                fake.health = 10.0f
                val popPacket = ClientboundEntityEventPacket(fake, EntityEvent.PROTECTED_FROM_DEATH)
                val popEvent = PacketEvent.Receive(popPacket)
                popEvent.post()
                if (!popEvent.isCancelled) {
                    popPacket.handle(connection)
                }
            }
        }
    }

    private data class MovementFrame(val position: Vec3, val yaw: Float, val pitch: Float)

}
