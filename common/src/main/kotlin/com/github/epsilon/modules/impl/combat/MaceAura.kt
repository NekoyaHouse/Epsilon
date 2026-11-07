package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.*
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils.find
import com.github.epsilon.utils.player.InvUtils.findInHotbar
import com.github.epsilon.utils.player.InvUtils.invSwap
import com.github.epsilon.utils.player.InvUtils.invSwapBack
import com.github.epsilon.utils.player.InvUtils.swap
import com.github.epsilon.utils.player.InvUtils.swapBack
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RotationUtils.getEyeDistanceToEntity
import com.github.epsilon.utils.rotation.RotationUtils.getRotationsToEntity
import com.github.epsilon.utils.timer.TimerUtils
import kotlin.math.ceil
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * @author Moli
 * 用于无反香草服，如 LBLT
 */

object MaceAura : Module("Mace Aura", Category.COMBAT) {

    private enum class AttackMode {
        Normal,
        Mace,
    }

    private val mode by enumSetting("Mode", AttackMode.Mace)
    private val range by doubleSetting("Range", 3.0, 1.0..6.0, 0.1)
    private val moveDistance by doubleSetting("Move Distance", 8.0, 1.0..20.0, 0.1)
    private val paperServer by boolSetting("Paper Server", true)
    private val vclip by doubleSetting("VClip", 10.0, 1.0..512.0, 1.0)
    private val damageOverride by boolSetting("Damage VClip", true)
    private val overrideVClip by doubleSetting("Override VClip", 30.0, 1.0..512.0, 1.0)
    private val swingHand by boolSetting("Swing Hand", false)
    private val cooldown by boolSetting("Cooldown", true)
    private val cooldownBase by doubleSetting(
        "Cooldown Base", 0.75, 0.1..1.0, 0.05, { cooldown })
    private val attackDelay by intSetting("Attack Delay", 50, 1..2000, 1, { !cooldown })
    private val players by boolSetting("Players", true)
    private val animals by boolSetting("Animals", false)
    private val mobs by boolSetting("Mobs", false)
    private val villagers by boolSetting("Villagers", false)

    var target: LivingEntity? = null
    private val attackTimer = TimerUtils()

    init {
        onEnable {
            target = null
            attackTimer.reset()
        }

        onDisable {
            target = null
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (ElytraCombat.isControllingCombat()) {
                return@safeEventHandler
            }

            target = TargetManager.acquirePrimary(
                TargetRequest.of(
                    range,
                    360.0f,
                    players,
                    mobs,
                    animals,
                    villagers,
                    false,
                    false,
                    false,
                    true,
                    64
                )
            )

            val currentTarget = target
            if (currentTarget == null) {
                return@safeEventHandler
            }

            RotationManager.setRotations(
                getRotationsToEntity(currentTarget), 180.0, Priority.Medium
            )

            if (!isReadyToAttack()) return@safeEventHandler

            doAura(currentTarget)
        }
    }

    private fun NonNullContext.isReadyToAttack(): Boolean {
        val hitResult = RotationManager.getHitResult()
        if (hitResult == null || hitResult.type != HitResult.Type.ENTITY) {
            return false
        }
        if (cooldown) {
            return player.getAttackStrengthScale(0.5f) >= cooldownBase.toFloat()
        }
        return attackTimer.passedMillise(attackDelay.toLong())
    }

    private fun NonNullContext.doAura(currentTarget: LivingEntity) {
        if (getEyeDistanceToEntity(currentTarget) > range) return

        if (mode == AttackMode.Normal) {
            attack(currentTarget)
        } else {
            doMaceAttack(currentTarget, vclip)
            if (damageOverride) {
                doMaceAttack(currentTarget, overrideVClip)
            }
        }

        attackTimer.reset()
    }

    private fun NonNullContext.doMaceAttack(currentTarget: LivingEntity, vclipValue: Double) {
        val currentSlot = player.inventory.selectedSlot
        var swappedInventory = false

        val hotbar: FindItemResult = findInHotbar(Items.MACE)
        if (hotbar.found()) {
            if (hotbar.slot != currentSlot) {
                swap(hotbar.slot, true)
            }
        } else {
            val inv: FindItemResult = find(Items.MACE)
            if (!inv.found()) return
            invSwap(inv.slot)
            swappedInventory = true
            swap(player.inventory.selectedSlot, true)
        }

        val startPos: Vec3 = player.position()
        val targetPos: Vec3 = startPos.add(0.0, vclipValue, 0.0)

        // 何意味，，，
        if (paperServer) {
            for (i in 0 until 4) {
                connection.send(ServerboundMovePlayerPacket.StatusOnly(false, false))
            }
        }

        doTp(startPos, targetPos, moveDistance, false, 20)
        sendMovePacket(startPos, false)
        attack(currentTarget)
        sendMovePacket(player.x, player.y + 1.0E-4, player.z, false)

        if (swappedInventory) {
            invSwapBack()
        }
        if (hotbar.found() && hotbar.slot != currentSlot) {
            swapBack()
        }
    }

    private fun NonNullContext.doTp(from: Vec3, to: Vec3, maxDistance: Double, onGround: Boolean, maxPackets: Int) {
        val dist = from.distanceTo(to)
        if (dist <= 0.0 || maxDistance <= 0.0) {
            sendMovePacket(to, onGround)
            return
        }
        var steps = ceil(dist / maxDistance).toInt()
        if (maxPackets > 0 && steps > maxPackets) steps = maxPackets
        val delta = to.subtract(from)
        for (i in 1..steps) {
            val t = i.toDouble() / steps.toDouble()
            val stepPos = from.add(delta.x() * t, delta.y() * t, delta.z() * t)
            sendMovePacket(stepPos, onGround)
        }
    }

    private fun NonNullContext.sendMovePacket(pos: Vec3, onGround: Boolean) {
        sendMovePacket(pos.x(), pos.y(), pos.z(), onGround)
    }

    private fun NonNullContext.sendMovePacket(x: Double, y: Double, z: Double, onGround: Boolean) {
        connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, onGround, false))
    }

    private fun NonNullContext.attack(currentTarget: LivingEntity) {
        gameMode.attack(player, currentTarget)
        SwingMode.of(swingHand).attackSwing(InteractionHand.MAIN_HAND)
    }

}
