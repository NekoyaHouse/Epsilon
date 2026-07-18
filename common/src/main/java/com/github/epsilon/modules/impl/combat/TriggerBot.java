package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/*
 * Author Moli
 */

public class TriggerBot extends Module {

    public static final TriggerBot INSTANCE = new TriggerBot();

    private static final float MIN_CRIT_FALL = 1.0f;
    private static final float MAX_CRIT_FALL = 1.14f;

    private final DoubleSetting range = doubleSetting("Range", 3.0, 1.0, 7.0, 0.1);
    private final IntSetting minDelay = intSetting("Random Delay Min", 2, 0, 20, 1);
    private final IntSetting maxDelay = intSetting("Random Delay Max", 13, 0, 20, 1);
    private final DoubleSetting attackCooldown = doubleSetting("Attack Cooldown", 0.9, 0.5, 1.0, 0.05);
    private final DoubleSetting critFallDistance = doubleSetting("Crit Fall Distance", 0.0, 0.0, 1.0, 0.05);

    private int delay;

    private TriggerBot() {
        super("Trigger Bot", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        delay = 0;
    }

    @EventHandler
    public void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.screen != null) return;

        if (mc.player.isUsingItem() || mc.player.isBlocking()) {
            return;
        }

        Entity target = getCrosshairTarget();
        if (target == null) return;

        if (!shouldAttack()) {
            if (delay > 0) {
                delay--;
                return;
            }
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        delay = MathUtils.getRandom(minDelay.getValue(), maxDelay.getValue());
    }

    private Entity getCrosshairTarget() {
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float distance = range.getValue().floatValue();

        HitResult blockHit = rayTrace(distance, yaw, pitch);
        Vec3 eyePos = mc.player.getEyePosition();
        double maxDistance = distance * distance;

        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            maxDistance = eyePos.distanceToSqr(blockHit.getLocation());
        }

        Vec3 lookVec = getRotationVector(pitch, yaw);
        Vec3 endPos = eyePos.add(lookVec.scale(distance));
        AABB searchBox = mc.player.getBoundingBox().expandTowards(lookVec.scale(distance)).inflate(1.0, 1.0, 1.0);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.player,
                eyePos,
                endPos,
                searchBox,
                entity -> !entity.isSpectator() && entity.isPickable() && isValidTarget(entity),
                maxDistance
        );

        if (entityHit != null) {
            Entity entity = entityHit.getEntity();
            double distToEntity = eyePos.distanceToSqr(entityHit.getLocation());

            if (distToEntity < maxDistance || blockHit == null || blockHit.getType() == HitResult.Type.MISS) {
                if (entity instanceof LivingEntity) {
                    return entity;
                }
            }
        }

        return null;
    }

    private HitResult rayTrace(double distance, float yaw, float pitch) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getRotationVector(pitch, yaw);
        Vec3 endPos = eyePos.add(lookVec.scale(distance));
        return mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
    }

    private Vec3 getRotationVector(float pitch, float yaw) {
        float radPitch = pitch * ((float) Math.PI / 180f);
        float radYaw = -yaw * ((float) Math.PI / 180f);
        float cosYaw = Mth.cos(radYaw);
        float sinYaw = Mth.sin(radYaw);
        float cosPitch = Mth.cos(radPitch);
        float sinPitch = Mth.sin(radPitch);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;
        if (living == mc.player) return false;
        return true;
    }

    private boolean shouldAttack() {
        boolean canCrit = !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && mc.level.getBlockState(mc.player.blockPosition()).getBlock() != Blocks.COBWEB;

        if (mc.player.fallDistance > MIN_CRIT_FALL && mc.player.fallDistance < MAX_CRIT_FALL) {
            return false;
        }

        if (getAttackCooldown() < (mc.player.onGround() ? 1.0f : attackCooldown.getValue().floatValue())) {
            return false;
        }

        if (!mc.options.keyJump.isDown()) {
            return true;
        }

        if (mc.player.isInLava() || isAboveWater()) {
            return true;
        }

        if (canCrit) {
            return !mc.player.onGround() && mc.player.fallDistance > critFallDistance.getValue().floatValue();
        }
        return true;
    }

    private boolean isAboveWater() {
        BlockPos pos = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.4, mc.player.getZ());
        return mc.player.isInWater() || mc.level.getBlockState(pos).getBlock() == Blocks.WATER;
    }

    public float getAttackCooldown() {
        float attackSpeed = (float) mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        float cooldownPerTick = 1.0f / attackSpeed * 20.0f;
        float lastAttackTicks = mc.player.getAttackStrengthScale(0.0f) * cooldownPerTick;
        return Math.min(1.0f, lastAttackTicks / cooldownPerTick);
    }

}
