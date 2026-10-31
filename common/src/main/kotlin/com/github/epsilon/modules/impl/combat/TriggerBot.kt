package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

object TriggerBot : Module("Trigger Bot", Category.COMBAT) {

    private const val MIN_CRIT_FALL = 1.0f
    private const val MAX_CRIT_FALL = 1.14f

    private val range by doubleSetting("Range", 3.0, 1.0, 7.0, 0.1)
    private val minDelay by intSetting("Random Delay Min", 2, 0, 20, 1)
    private val maxDelay by intSetting("Random Delay Max", 13, 0, 20, 1)
    private val attackCooldown by doubleSetting("Attack Cooldown", 0.9, 0.5, 1.0, 0.05)
    private val critFallDistance by doubleSetting("Crit Fall Distance", 0.0, 0.0, 1.0, 0.05)

    private var delay = 0

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            if (mc.gui.screen() != null) return@nonNullHandler

            if (player.isUsingItem || player.isBlocking) {
                return@nonNullHandler
            }

            val target = getCrosshairTarget() ?: return@nonNullHandler

            if (!shouldAttack()) {
                if (delay > 0) {
                    delay--
                    return@nonNullHandler
                }
            }

            interaction.attack(player, target)
            SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)

            delay = MathUtils.getRandom(minDelay, maxDelay)
        }
    }

    override fun onDisable() {
        delay = 0
    }

    context(nn: NonNullContext)
    private fun getCrosshairTarget(): Entity? {
        val yaw = player.yRot
        val pitch = player.xRot
        val distance = range.toFloat()

        val blockHit = rayTrace(distance.toDouble(), yaw, pitch)
        val eyePos: Vec3 = player.eyePosition
        var maxDistance = (distance * distance).toDouble()

        if (blockHit != null && blockHit.type != HitResult.Type.MISS) {
            maxDistance = eyePos.distanceToSqr(blockHit.location)
        }

        val lookVec = getRotationVector(pitch, yaw)
        val endPos = eyePos.add(lookVec.scale(distance.toDouble()))
        val searchBox: AABB =
            player.boundingBox.expandTowards(lookVec.scale(distance.toDouble())).inflate(1.0, 1.0, 1.0)

        val entityHit = ProjectileUtil.getEntityHitResult(
            player,
            eyePos,
            endPos,
            searchBox,
            { entity ->
                !entity.isSpectator && entity.isPickable &&
                        entity is LivingEntity && entity.isAlive && entity !== player
            },
            maxDistance
        )

        if (entityHit != null) {
            val entity: Entity = entityHit.entity
            val distToEntity = eyePos.distanceToSqr(entityHit.location)

            if (distToEntity < maxDistance || blockHit == null || blockHit.type == HitResult.Type.MISS) {
                if (entity is LivingEntity) {
                    return entity
                }
            }
        }

        return null
    }

    context(nn: NonNullContext)
    private fun rayTrace(distance: Double, yaw: Float, pitch: Float): HitResult {
        val eyePos: Vec3 = player.eyePosition
        val lookVec = getRotationVector(pitch, yaw)
        val endPos = eyePos.add(lookVec.scale(distance))
        return level.clip(
            ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        )
    }

    private fun getRotationVector(pitch: Float, yaw: Float): Vec3 {
        val radPitch = pitch * (PI.toFloat() / 180f)
        val radYaw = -yaw * (PI.toFloat() / 180f)
        val cosYaw = Mth.cos(radYaw.toDouble())
        val sinYaw = Mth.sin(radYaw.toDouble())
        val cosPitch = Mth.cos(radPitch.toDouble())
        val sinPitch = Mth.sin(radPitch.toDouble())
        return Vec3((sinYaw * cosPitch).toDouble(), (-sinPitch).toDouble(), (cosYaw * cosPitch).toDouble())
    }

    context(nn: NonNullContext)
    private fun shouldAttack(): Boolean {
        val canCrit = !player.abilities.flying &&
                !player.isFallFlying &&
                !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) &&
                level.getBlockState(player.blockPosition()).block !== Blocks.COBWEB

        if (player.fallDistance > MIN_CRIT_FALL.toDouble() && player.fallDistance < MAX_CRIT_FALL.toDouble()) {
            return false
        }

        if (getAttackCooldown() < (if (player.onGround()) 1.0f else attackCooldown.toFloat())) {
            return false
        }

        if (!mc.options.keyJump.isDown) {
            return true
        }

        if (player.isInLava || isAboveWater()) {
            return true
        }

        if (canCrit) {
            return !player.onGround() && player.fallDistance > critFallDistance.toFloat()
        }
        return true
    }

    context(nn: NonNullContext)
    private fun isAboveWater(): Boolean {
        val pos = BlockPos.containing(player.x, player.y - 0.4, player.z)
        return player.isInWater || level.getBlockState(pos).block === Blocks.WATER
    }

    fun getAttackCooldown(): Float {
        val nn = NonNullContext.current ?: return 0.0f
        return context(nn) {
            val attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED).toFloat()
            val cooldownPerTick = 1.0f / attackSpeed * 20.0f
            val lastAttackTicks = player.getAttackStrengthScale(0.0f) * cooldownPerTick
            minOf(1.0f, lastAttackTicks / cooldownPerTick)
        }
    }

}
