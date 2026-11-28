package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.kernel.gameplay.player.DamageUtils
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.crystalaura.AutoCrystal
import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Explosion
import net.minecraft.world.phys.Vec3
import java.util.*

object FakePlayer : Module("Fake Player", Category.PLAYER) {
    private val playerName by stringSetting("Name", "BC_zxy")
    private val copyInventory by boolSetting("Copy Inventory", true)
    private val recordButton = buttonSetting("Record / Stop", { recording = !recording }, { !playing })
    private val playButton = buttonSetting("Play / Stop", { playing = !playing }, { !recording })

    private var recording = false
    private var playing = false
    private val frames = ArrayList<Frame>()
    private var playbackIndex = 0
    var fakePlayer: RemotePlayer? = null; private set

    override fun getInfo(): String? = when {
        recording -> recordButton.displayName
        playing -> playButton.displayName
        else -> null
    }

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
                    fake.setItemInHand(InteractionHand.MAIN_HAND, player.mainHandItem.copy())
                    fake.setItemInHand(InteractionHand.OFF_HAND, player.offhandItem.copy())
                    for (slot in DamageUtils.armorSlots) fake.setItemSlot(slot, player.getItemBySlot(slot).copy())
                    fake.attributes.assignAllValues(player.attributes)
                }
                level.addEntity(fake)
                fake.addEffect(MobEffectInstance(MobEffects.REGENERATION, 9999, 2))
                fake.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 9999, 4))
                fake.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 9999, 1))
                fakePlayer = fake
            } ?: run {
                toggle()
            }
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
            toggle()
        }

        safeEventHandler<SendPositionEvent> {
            val fake = fakePlayer
            if (fake == null || fake.isRemoved) {
                toggle()
            } else if (recording) {
                frames.add(Frame(player.position(), player.yRot, player.xRot))
            } else {
                if (fake.tickCount % 8 == 0) fake.health += 0.5f
                if (fake.position().distanceToSqr(player.position()) > 400.0) {
                    isEnabled = false
                } else {
                    if (playing && frames.isNotEmpty()) {
                        playbackIndex = (playbackIndex + 1) % frames.size
                        val frame = frames[playbackIndex]
                        fake.yRot = frame.yaw
                        fake.xRot = frame.pitch
                        fake.yHeadRot = frame.yaw
                        fake.syncPacketPositionCodec(frame.position.x, frame.position.y, frame.position.z)
                        fake.moveOrInterpolateTo(frame.position, frame.yaw, frame.pitch)
                    } else {
                        playbackIndex = 0
                    }
                    if (!fake.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                        fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack(Items.TOTEM_OF_UNDYING))
                    }
                }
            }
        }

        safeEventHandler<PacketEvent.Receive> { event ->
            val packet = event.packet as? ClientboundExplodePacket ?: return@safeEventHandler

            val fake = fakePlayer
            if (fake != null && !fake.isRemoved && fake.hurtTime == 0) {
                fake.handleDamageEvent(level.damageSources().generic())
                fake.health += fake.absorptionAmount - calculateDamage(
                    level,
                    packet.center(),
                    fake,
                    AutoCrystal.assumeBestArmor
                )
                if (fake.isDeadOrDying) {
                    fake.health = 10f
                    val pop = PacketEvent.Receive(ClientboundEntityEventPacket(fake, 35.toByte()))
                    pop.post()
                    if (!pop.isCancelled) {
                        (pop.packet as? ClientboundEntityEventPacket)?.let { connection.handleEntityEvent(it) }
                    }
                }
            }
        }
    }

    private fun calculateDamage(level: ClientLevel, center: Vec3, target: Player, bestArmor: Boolean): Float {
        val distance = target.position().distanceTo(center)
        if (level.difficulty.id == 0 || distance > 12.0 || target.isCreative) return 0f
        var amount = DamageUtils.rawDamage(
            distance,
            DamageUtils.exposure(level, center, target, target.boundingBox, emptySet(), null, ignoreWeakBlocks = false)
        ).toFloat()
        amount = when (level.difficulty.id) {
            1 -> minOf(amount / 2f + 1f, amount)
            3 -> amount * 1.5f
            else -> amount
        }
        val source = level.damageSources().explosion(null as Explosion?)
        var reduced = CombatRules.getDamageAfterAbsorb(
            target, amount, source, target.armorValue.toFloat(),
            target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
        )
        target.getEffect(MobEffects.RESISTANCE)?.let {
            reduced = (reduced * (25 - (it.amplifier + 1) * 5) / 25f).coerceAtLeast(0f)
        }
        var protection = 0
        for (slot in DamageUtils.armorSlots) {
            val stack = target.getItemBySlot(slot)
            if (bestArmor) {
                if (!stack.isEmpty) protection += 8
            } else for (entry in stack.enchantments.entrySet()) {
                if (entry.key.`is`(Enchantments.PROTECTION)) protection += entry.intValue
                if (entry.key.`is`(Enchantments.BLAST_PROTECTION)) protection += entry.intValue * 2
            }
        }
        return CombatRules.getDamageAfterMagicAbsorb(reduced, protection.toFloat()).coerceAtLeast(0f)
    }

    private data class Frame(val position: Vec3, val yaw: Float, val pitch: Float)
}
