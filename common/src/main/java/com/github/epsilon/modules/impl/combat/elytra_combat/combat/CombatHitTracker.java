package com.github.epsilon.modules.impl.combat.elytra_combat.combat;

import com.github.epsilon.interfaces.ClientboundEntityEventPacketAccessor;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 将网络线程收到的命中包转成客户端 tick 可消费的状态事件。
 */
public final class CombatHitTracker {

    /** 行为层只需要区分重锤和长矛两类命中。 */
    public enum HitType {
        MACE,
        SPEAR
    }

    private final ConcurrentLinkedQueue<HitType> pendingHits = new ConcurrentLinkedQueue<>();
    /** 网络线程只写 volatile 上下文，队列在客户端 tick 中消费。 */
    private volatile int localPlayerId = -1;
    private volatile int targetId = -1;
    private volatile int lastAttackTick = Integer.MIN_VALUE;

    public void setContext(Entity localPlayer, LivingEntity target) {
        this.localPlayerId = localPlayer != null ? localPlayer.getId() : -1;
        this.targetId = target != null ? target.getId() : -1;
    }

    public void markAttack(LivingEntity target, int tick) {
        // 记录本地出手时间，供攻击后短时间内的包过滤使用。
        if (target != null && target.getId() == this.targetId) {
            this.lastAttackTick = tick;
        }
    }

    public void onDamagePacket(ClientboundDamageEventPacket packet) {
        // 伤害目标必须是当前目标、来源必须是本地玩家，再检查 MACE_SMASH 类型。
        if (packet.entityId() != this.targetId || packet.sourceCauseId() != this.localPlayerId) {
            return;
        }
        boolean maceSmash = packet.sourceType().unwrapKey()
                .map(key -> key.equals(DamageTypes.MACE_SMASH))
                .orElse(false);
        if (maceSmash) {
            this.pendingHits.offer(HitType.MACE);
        }
    }

    public void onEntityStatusPacket(ClientboundEntityEventPacket packet) {
        // KINETIC_HIT 的实体 id 在 accessor 中，命中本地玩家时才算长矛命中。
        if (packet.getEventId() != EntityEvent.KINETIC_HIT) {
            return;
        }
        if (packet instanceof ClientboundEntityEventPacketAccessor accessor
                && accessor.epsilon$getEntityId() == this.localPlayerId) {
            this.pendingHits.offer(HitType.SPEAR);
        }
    }

    public HitType poll() {
        // 从网络线程队列取状态事件；调用方保证只在客户端 tick 消费。
        return this.pendingHits.poll();
    }

    public boolean attackedTargetRecently(int currentTick, int ticks) {
        return currentTick - this.lastAttackTick <= ticks;
    }

    public void clear() {
        this.pendingHits.clear();
        this.localPlayerId = -1;
        this.targetId = -1;
        this.lastAttackTick = Integer.MIN_VALUE;
    }
}
