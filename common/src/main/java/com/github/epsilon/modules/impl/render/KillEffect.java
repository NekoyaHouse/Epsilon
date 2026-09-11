package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class KillEffect extends Module {

    public static final KillEffect INSTANCE = new KillEffect();

    private static final double SQUID_BASE_OFFSET = 0.0;
    private static final double SQUID_RISE_HEIGHT = 0.9;
    private static final long SQUID_RISE_DURATION_MS = 450L;

    private final BoolSetting lightning = boolSetting("Lightning", true);
    private final BoolSetting explosion = boolSetting("Explosion", true);
    private final BoolSetting squidSetting = boolSetting("Squid", true);
    private final BoolSetting blood = boolSetting("Blood", true);

    private final Set<LivingEntity> pendingTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Entity> effectSquids = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<SquidEffect> squidEffects = new ArrayList<>();
    private int nextLocalEntityId = -8;

    private KillEffect() {
        super("Kill Effect", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        clearState();
    }

    @Override
    protected void onDisable() {
        clearState();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        Entity entity = event.getEntity();
        if (!effectSquids.contains(entity) && entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
            pendingTargets.add(livingEntity);
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        tickKillEffect();
    }

    private void tickKillEffect() {
        if (nullCheck()) return;

        if (squidSetting.getValue()) {
            tickSquids();
        } else {
            clearSquids();
        }

        processRemovedTargets();
    }

    private void processRemovedTargets() {
        if (pendingTargets.isEmpty()) return;

        Iterator<LivingEntity> iterator = pendingTargets.iterator();
        while (iterator.hasNext()) {
            LivingEntity target = iterator.next();
            if (target.isRemoved() || mc.level.getEntity(target.getId()) != target) {
                iterator.remove();
                killEffect(target);
            }
        }
    }

    private void tickSquids() {
        Iterator<SquidEffect> iterator = squidEffects.iterator();
        while (iterator.hasNext()) {
            SquidEffect effect = iterator.next();
            if (effect.tick()) {
                effect.remove();
                iterator.remove();
            }
        }
    }

    private void killEffect(LivingEntity target) {
        if (lightning.getValue()) {
            LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, mc.level);
            bolt.setId(nextLocalEntityId());
            bolt.setVisualOnly(true);
            bolt.setPos(target.getX(), target.getY(), target.getZ());
            bolt.setOldPosAndRot();
            mc.level.addEntity(bolt);
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 1.0F, false);
        }

        if (explosion.getValue()) {
            for (int i = 0; i <= 8; i++) {
                mc.particleEngine.createTrackingEmitter(target, ParticleTypes.FLAME);
            }
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }

        if (squidSetting.getValue()) {
            squidEffects.add(new SquidEffect(target));
        }

        if (blood.getValue()) {
            mc.level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    target.getX(),
                    target.getY() + target.getBbHeight() - 0.75,
                    target.getZ(),
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    private void clearState() {
        clearSquids();
        effectSquids.clear();
        pendingTargets.clear();
    }

    private void clearSquids() {
        for (SquidEffect effect : squidEffects) {
            effect.remove();
        }
        squidEffects.clear();
    }

    private int nextLocalEntityId() {
        return nextLocalEntityId--;
    }

    private final class SquidEffect {

        private final Squid entity;
        private final double baseY;
        private final Animation animation;

        private SquidEffect(LivingEntity target) {
            entity = new Squid(EntityTypes.SQUID, mc.level);
            entity.setId(nextLocalEntityId());
            entity.setNoAi(true);
            baseY = target.getY() + SQUID_BASE_OFFSET;
            entity.setPos(target.getX(), baseY, target.getZ());
            entity.setOldPosAndRot();
            animation = new Animation(Easing.SMOOTH_STEP, SQUID_RISE_DURATION_MS);
            animation.setStartValue(0.0F);
            effectSquids.add(entity);
            mc.level.addEntity(entity);
        }

        private boolean tick() {
            if (entity.isRemoved() || mc.level.getEntity(entity.getId()) != entity) {
                return true;
            }

            animation.run(1.0F);
            entity.setPos(entity.getX(), baseY + animation.getValue() * SQUID_RISE_HEIGHT, entity.getZ());
            entity.xBodyRot = 0.0F;
            entity.xBodyRotO = 0.0F;
            entity.zBodyRot = 0.0F;
            entity.zBodyRotO = 0.0F;

            if (animation.isFinished()) {
                for (int i = 0; i <= 8; i++) {
                    mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.FLAME);
                }
                return true;
            }
            return false;
        }

        private void remove() {
            effectSquids.remove(entity);
            if (mc.level != null && mc.level.getEntity(entity.getId()) == entity) {
                mc.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }

}
