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

    public enum HitType {
        MACE,
        SPEAR
    }

    private final ConcurrentLinkedQueue<HitType> pendingHits = new ConcurrentLinkedQueue<>();
    private volatile int localPlayerId = -1;
    private volatile int targetId = -1;
    private volatile int lastAttackTick = Integer.MIN_VALUE;

    public void setContext(Entity localPlayer, LivingEntity target) {
        this.localPlayerId = localPlayer != null ? localPlayer.getId() : -1;
        this.targetId = target != null ? target.getId() : -1;
    }

    public void markAttack(LivingEntity target, int tick) {
        if (target != null && target.getId() == this.targetId) {
            this.lastAttackTick = tick;
        }
    }

    public void onDamagePacket(ClientboundDamageEventPacket packet) {
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
        if (packet.getEventId() != EntityEvent.KINETIC_HIT) {
            return;
        }
        if (packet instanceof ClientboundEntityEventPacketAccessor accessor
                && accessor.epsilon$getEntityId() == this.localPlayerId) {
            this.pendingHits.offer(HitType.SPEAR);
        }
    }

    public HitType poll() {
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
