package com.github.epsilon.managers.target

import com.github.epsilon.Constants.mc
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.modules.impl.combat.AntiBot
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import java.util.*

object TargetManager {

    private var sharedTarget: LivingEntity? = null

    init {
        handler<ClientTickEvent.Pre>(alwaysListening = true) { onClientTick() }
    }

    private fun onClientTick() {
        if (mc.player == null || mc.level == null) {
            sharedTarget = null
            return
        }

        if (!isSharedTargetAlive()) {
            sharedTarget = null
        }
    }

    fun acquirePrimary(request: TargetRequest): LivingEntity? {
        val targets = acquireTargets(request)
        return if (targets.isEmpty()) null else targets.first()
    }

    fun acquireTargets(request: TargetRequest): List<LivingEntity> {
        val nn = NonNullContext.current ?: return emptyList()
        return context(nn) {
            val candidates = collectTargets(request)
            if (candidates.isEmpty()) {
                candidates
            } else {
                if (!isSharedTargetAlive()) {
                    sharedTarget = null
                }

                val shared = sharedTarget
                if (shared != null && isValidTarget(shared, request)) {
                    if (candidates.remove(shared)) {
                        candidates.add(0, shared)
                    }
                } else if (sharedTarget == null) {
                    sharedTarget = candidates.first()
                }

                val maxTargets = maxOf(1, request.maxTargets)
                if (candidates.size > maxTargets) {
                    candidates.subList(0, maxTargets).toList()
                } else {
                    candidates.toList()
                }
            }
        }
    }

    fun getSharedTarget(): LivingEntity? {
        if (!isSharedTargetAlive()) {
            sharedTarget = null
        }
        return sharedTarget
    }

    context(nn: NonNullContext)
    private fun collectTargets(request: TargetRequest): MutableList<LivingEntity> {
        val targets = ArrayList<LivingEntity>()

        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || entity is ArmorStand) continue
            if (entity === player) continue
            if (!isValidTarget(entity, request)) continue
            targets.add(entity)
        }

        targets.sortWith(Comparator.comparingDouble { entity -> RotationUtils.getEyeDistanceToEntity(entity) })
        return targets
    }

    private fun isSharedTargetAlive(): Boolean {
        val shared = sharedTarget ?: return false
        if (shared === mc.player) return false
        if (!shared.isAlive || shared.isDeadOrDying) return false
        if (shared.level() !== mc.level) return false
        return !AntiBot.isBot(shared)
    }

    context(nn: NonNullContext)
    private fun isValidTarget(entity: LivingEntity, request: TargetRequest): Boolean {
        if (!entity.isAlive || entity.isDeadOrDying) return false
        if (AntiBot.isBot(entity)) return false
        if (isSameTeam(entity) &&
            !(request.allowFriends && entity is Player && FriendManager.isFriend(entity))
        ) {
            return false
        }

        val dist = RotationUtils.getEyeDistanceToEntity(entity)
        if (dist > request.range) return false

        if (request.fov < 360.0f && !RotationUtils.isInFov(entity, request.fov)) return false

        if (entity is Player) {
            if (!request.allowFriends && FriendManager.isFriend(entity)) return false
            if (!request.player) return false
            if (entity.isInvisible && !request.invisible) return false
        } else if (entity is Villager) {
            if (!request.villager) return false
        } else if (entity is Monster) {
            if (!request.mob) return false
        } else if (entity is Animal) {
            if (!request.animal) return false
        } else {
            val category = entity.type.category
            when (category) {
                MobCategory.AMBIENT, MobCategory.WATER_AMBIENT -> {
                    if (!request.ambient) return false
                }

                MobCategory.WATER_CREATURE, MobCategory.AXOLOTLS, MobCategory.UNDERGROUND_WATER_CREATURE -> {
                    if (!request.water) return false
                }

                else -> {
                    if (!request.others) return false
                }
            }
        }

        return request.extraFilter.test(entity)
    }

    fun isSameTeam(entity: Entity): Boolean {
        val teams = ClientSetting.teams
        if (teams.`is`(ClientSetting.Teams.None)) {
            return false
        }
        if (entity is Player) {
            return if (teams.`is`(ClientSetting.Teams.Color)) {
                entity.teamColor == mc.player!!.teamColor
            } else {
                Objects.equals(getTeam(entity), getTeam(mc.player!!))
            }
        }
        return false
    }

    private fun getTeam(entity: Entity): String? {
        val playerInfo: PlayerInfo = mc.connection!!.getPlayerInfo(entity.uuid) ?: return null
        val team = playerInfo.team ?: return null
        return team.name
    }

}
