package com.github.epsilon.modules.impl.combat.elytra_combat.combat

import com.github.epsilon.interfaces.ClientboundEntityEventPacketAccessor
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 将网络线程收到的命中包转成客户端 tick 可消费的状态事件。
 */
class CombatHitTracker {

    /**
     * 行为层只需要区分重锤和长矛两类命中。
     */
    enum class HitType {
        MACE,
        SPEAR,
    }

    private val pendingHits = ConcurrentLinkedQueue<HitType>()

    /**
     * 网络线程只写 volatile 上下文，队列在客户端 tick 中消费。
     */
    @Volatile
    private var localPlayerId: Int = -1

    @Volatile
    private var targetId: Int = -1

    @Volatile
    private var lastAttackTick: Int = Int.MIN_VALUE

    fun setContext(localPlayer: Entity?, target: LivingEntity?) {
        this.localPlayerId = localPlayer?.id ?: -1
        this.targetId = target?.id ?: -1
    }

    fun markAttack(target: LivingEntity?, tick: Int) {
        // 记录本地出手时间，供攻击后短时间内的包过滤使用。
        if (target != null && target.id == this.targetId) {
            this.lastAttackTick = tick
        }
    }

    fun onDamagePacket(packet: ClientboundDamageEventPacket) {
        // 伤害目标必须是当前目标、来源必须是本地玩家，再检查 MACE_SMASH 类型。
        if (packet.entityId() != this.targetId || packet.sourceCauseId() != this.localPlayerId) {
            return
        }
        val maceSmash = packet.sourceType().unwrapKey()
            .map { key -> key == DamageTypes.MACE_SMASH }
            .orElse(false)
        if (maceSmash) {
            this.pendingHits.offer(HitType.MACE)
        }
    }

    fun onEntityStatusPacket(packet: ClientboundEntityEventPacket) {
        // KINETIC_HIT 的实体 id 在 accessor 中，命中本地玩家时才算长矛命中。
        if (packet.eventId != EntityEvent.KINETIC_HIT) {
            return
        }
        if (packet is ClientboundEntityEventPacketAccessor && packet.`epsilon$getEntityId`() == this.localPlayerId) {
            this.pendingHits.offer(HitType.SPEAR)
        }
    }

    fun poll(): HitType? {
        // 从网络线程队列取状态事件；调用方保证只在客户端 tick 消费。
        return this.pendingHits.poll()
    }

    fun attackedTargetRecently(currentTick: Int, ticks: Int): Boolean {
        return currentTick - this.lastAttackTick <= ticks
    }

    fun clear() {
        this.pendingHits.clear()
        this.localPlayerId = -1
        this.targetId = -1
        this.lastAttackTick = Int.MIN_VALUE
    }

}
