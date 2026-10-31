package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.level.block.Blocks
import java.util.*

object KillEffect : Module("Kill Effect", Category.RENDER) {

    private const val SQUID_BASE_OFFSET = 0.0
    private const val SQUID_RISE_HEIGHT = 0.9
    private const val SQUID_RISE_DURATION_MS = 450L

    private val lightning by boolSetting("Lightning", true)
    private val explosion by boolSetting("Explosion", true)
    private val squidSetting by boolSetting("Squid", true)
    private val blood by boolSetting("Blood", true)

    private val pendingTargets: MutableSet<LivingEntity> =
        Collections.newSetFromMap(IdentityHashMap<LivingEntity, Boolean>())
    private val effectSquids: MutableSet<Entity> =
        Collections.newSetFromMap(IdentityHashMap<Entity, Boolean>())
    private val squidEffects: MutableList<SquidEffect> = ArrayList()
    private var localEntityIdCounter = -8

    init {
        safeEventHandler<AttackEntityEvent> { onAttackEntity(it) }
        safeEventHandler<PlayerTickEvent.Pre> { onPlayerTick(it) }
    }

    override fun onEnable() {
        clearState()
    }

    override fun onDisable() {
        clearState()
    }

    private fun onAttackEntity(event: AttackEntityEvent) {
        val entity = event.entity
        if (!effectSquids.contains(entity) && entity is LivingEntity && entity.isAlive) {
            pendingTargets.add(entity)
        }
    }

    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        tickKillEffect()
    }

    private fun tickKillEffect() {
        if (nullCheck()) return

        if (squidSetting) {
            tickSquids()
        } else {
            clearSquids()
        }

        processRemovedTargets()
    }

    private fun processRemovedTargets() {
        if (pendingTargets.isEmpty()) return

        val level = mc.level ?: return
        val iterator = pendingTargets.iterator()
        while (iterator.hasNext()) {
            val target = iterator.next()
            if (target.isRemoved || level.getEntity(target.id) !== target) {
                iterator.remove()
                killEffect(target)
            }
        }
    }

    private fun tickSquids() {
        val iterator = squidEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            if (effect.tick()) {
                effect.remove()
                iterator.remove()
            }
        }
    }

    private fun killEffect(target: LivingEntity) {
        val level = mc.level ?: return
        val player = mc.player ?: return

        if (lightning) {
            val bolt = LightningBolt(EntityTypes.LIGHTNING_BOLT, level)
            bolt.id = nextLocalEntityId()
            bolt.setVisualOnly(true)
            bolt.setPos(target.x, target.y, target.z)
            bolt.setOldPosAndRot()
            level.addEntity(bolt)
            level.playLocalSound(
                player.x, player.y, player.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 1.0F, false
            )
        }

        if (explosion) {
            for (i in 0..8) {
                mc.particleEngine.createTrackingEmitter(target, ParticleTypes.FLAME)
            }
            level.playLocalSound(
                player.x, player.y, player.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F, false
            )
        }

        if (squidSetting) {
            squidEffects.add(SquidEffect(target))
        }

        if (blood) {
            level.addParticle(
                BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                target.x,
                target.y + target.bbHeight - 0.75,
                target.z,
                0.0,
                0.0,
                0.0
            )
        }
    }

    private fun clearState() {
        clearSquids()
        effectSquids.clear()
        pendingTargets.clear()
    }

    private fun clearSquids() {
        for (effect in squidEffects) {
            effect.remove()
        }
        squidEffects.clear()
    }

    private fun nextLocalEntityId(): Int = localEntityIdCounter--

    private class SquidEffect(target: LivingEntity) {

        private val entity: Squid
        private val baseY: Double
        private val animation: Animation

        init {
            val level = mc.level!!
            entity = Squid(EntityTypes.SQUID, level)
            entity.id = nextLocalEntityId()
            entity.isNoAi = true
            baseY = target.y + SQUID_BASE_OFFSET
            entity.setPos(target.x, baseY, target.z)
            entity.setOldPosAndRot()
            animation = Animation(Easing.SMOOTH_STEP, SQUID_RISE_DURATION_MS)
            animation.startValue = 0.0F
            effectSquids.add(entity)
            level.addEntity(entity)
        }

        fun tick(): Boolean {
            val level = mc.level
            if (entity.isRemoved || level == null || level.getEntity(entity.id) !== entity) {
                return true
            }

            animation.run(1.0F)
            entity.setPos(entity.x, baseY + animation.value * SQUID_RISE_HEIGHT, entity.z)
            entity.xBodyRot = 0.0F
            entity.xBodyRotO = 0.0F
            entity.zBodyRot = 0.0F
            entity.zBodyRotO = 0.0F

            if (animation.isFinished) {
                for (i in 0..8) {
                    mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.FLAME)
                }
                return true
            }
            return false
        }

        fun remove() {
            effectSquids.remove(entity)
            val level = mc.level
            if (level != null && level.getEntity(entity.id) === entity) {
                level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
            }
        }

    }

}
