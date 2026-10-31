package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.rotation.RotationUtils
import kotlin.math.roundToInt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object SpearKill : Module("Spear Kill", Category.COMBAT) {

    private enum class LungeMode {
        DirectionBased,
        Above,
    }

    private val lungeMode by enumSetting("Lunge Mode", LungeMode.DirectionBased)
    private val maxRange by doubleSetting("Max Range", 6.0, 1.0, 16.0, 0.1)
    private val lungeStrength by doubleSetting("Lunge Strength", 3.7, 0.5, 15.0, 0.1)
    private val aboveHeight by doubleSetting(
        "Above Height", 10.0, 3.0, 30.0, 0.1, dependency = { lungeMode == LungeMode.Above })
    private val aboveHeightDistance by doubleSetting(
        "Above Height Distance", 3.0, 1.0, 10.0, 0.1, dependency = { lungeMode == LungeMode.Above })
    private val stopDistance by doubleSetting("Stop Distance", 0.6, 0.0, 10.0, 0.1)
    private val chargeTimeModifier by intSetting("Charge Time", 100, 0, 100, 1)
    private val stopOnTarget by boolSetting("Stop On Target", true)
    private val autoSwitch by boolSetting("Auto Switch", true)
    private val rotate by boolSetting("Rotate", true)
    private val players by boolSetting("Players", true)
    private val mobs by boolSetting("Mobs", false)
    private val animals by boolSetting("Animals", false)
    private val villagers by boolSetting("Villagers", false)
    private val invisible by boolSetting("Invisible", true)

    var killtarget: LivingEntity? = null
    private var currentlyCharging = false
    private var aboveTargetPos: Vec3? = null
    private var firstPhase = false

    init {
        // 原实现监听 PlayerTickEvent 基类（按事件精确 class 分发，子类事件不会命中），保持原样。
        nonNullHandler<PlayerTickEvent> {
            if (ElytraCombat.isControllingCombat()) {
                return@nonNullHandler
            }
            updateTarget()

            currentlyCharging = isUsingSpear()

            if (currentlyCharging) {
                if (killtarget == null) {
                    updateTarget()
                }
                val currentTarget = killtarget
                if (currentTarget != null && !currentTarget.isAlive) {
                    if (stopOnTarget) {
                        player.deltaMovement = Vec3.ZERO
                        player.isSprinting = false
                    }
                    killtarget = null
                    firstPhase = false
                    aboveTargetPos = null
                }
                if (killtarget != null) {
                    lunge()
                }
            } else {
                killtarget = null
                firstPhase = false
                aboveTargetPos = null
            }
        }
    }

    override fun onEnable() {
        killtarget = null
        currentlyCharging = false
        aboveTargetPos = null
        firstPhase = false
    }

    override fun onDisable() {
        killtarget = null
        currentlyCharging = false
        aboveTargetPos = null
        firstPhase = false
    }

    context(nn: NonNullContext)
    private fun updateTarget() {
        val currentTarget = killtarget
        if (currentTarget != null && !currentTarget.isAlive) {
            killtarget = null
        }

        if (killtarget == null || autoSwitch) {
            val rangeValue = maxRange
            val candidates = TargetManager.acquireTargets(
                TargetRequest.of(
                    rangeValue,
                    360.0f,
                    players,
                    mobs,
                    animals,
                    villagers,
                    false,
                    false,
                    false,
                    invisible,
                    64
                )
            )

            if (!candidates.isEmpty()) {
                killtarget = candidates.first()
            } else {
                killtarget = null
            }
        }
    }

    context(nn: NonNullContext)
    private fun isUsingSpear(): Boolean {
        val item: Item = player.useItem.item
        return item === Items.WOODEN_SPEAR || item === Items.STONE_SPEAR || item === Items.COPPER_SPEAR ||
            item === Items.IRON_SPEAR || item === Items.GOLDEN_SPEAR || item === Items.DIAMOND_SPEAR ||
            item === Items.NETHERITE_SPEAR
    }

    private fun getReadyTicks(item: Item): Int {
        val value: Int = when (item) {
            Items.WOODEN_SPEAR -> 14
            Items.STONE_SPEAR, Items.GOLDEN_SPEAR -> 13
            Items.COPPER_SPEAR -> 12
            Items.IRON_SPEAR -> 11
            Items.DIAMOND_SPEAR -> 9
            Items.NETHERITE_SPEAR -> 7
            else -> 10
        }

        return (value * (chargeTimeModifier / 100.0f)).roundToInt()
    }

    context(nn: NonNullContext)
    private fun lunge() {
        val currentTarget = killtarget ?: return

        val readyTicks = getReadyTicks(player.useItem.item)

        if (rotate) {
            rotateToTarget(currentTarget)
        }

        val useTime = player.ticksUsingItem
        if (useTime > readyTicks) {
            val playerBox: AABB = player.boundingBox.inflate(stopDistance)
            val targetBox: AABB = currentTarget.boundingBox
            val atTarget = playerBox.intersects(targetBox)

            if (atTarget) {
                if (stopOnTarget) {
                    killtarget = null
                    player.deltaMovement = Vec3.ZERO
                    player.isSprinting = false
                }
                firstPhase = false
                aboveTargetPos = null
                return
            }

            val lungeSpeed = lungeStrength

            when (lungeMode) {
                LungeMode.DirectionBased -> {
                    val targetDir = currentTarget.boundingBox.center.subtract(player.position()).normalize()

                    player.isSprinting = true
                    player.deltaMovement = Vec3(
                        targetDir.x() * lungeSpeed,
                        targetDir.y() * lungeSpeed,
                        targetDir.z() * lungeSpeed
                    )
                }

                LungeMode.Above -> {
                    var abovePos = aboveTargetPos
                    if (!firstPhase || abovePos == null) {
                        val targetCenter = currentTarget.boundingBox.center
                        abovePos = Vec3(targetCenter.x(), targetCenter.y() + aboveHeight, targetCenter.z())
                        aboveTargetPos = abovePos
                        firstPhase = true
                    }

                    val playerPos = player.position()
                    val distToAbove = playerPos.distanceTo(abovePos)

                    val viewDir: Vec3
                    if (distToAbove < aboveHeightDistance) {
                        viewDir = currentTarget.boundingBox.center.subtract(playerPos).normalize()
                        firstPhase = false
                    } else {
                        viewDir = abovePos.subtract(playerPos).normalize()
                    }

                    player.isSprinting = true
                    player.deltaMovement = Vec3(
                        viewDir.x() * lungeSpeed,
                        viewDir.y() * lungeSpeed,
                        viewDir.z() * lungeSpeed
                    )
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun rotateToTarget(target: LivingEntity) {
        val rotations = RotationUtils.getRotationsToEntity(target)
        player.yRot = rotations.yaw
        player.yHeadRot = rotations.yaw
        player.xRot = rotations.pitch
    }

}
