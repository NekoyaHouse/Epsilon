package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 长矛 kinetic 空战状态机。
 *
 * <p>通过持续使用长矛组件等待 delayTicks，冲锋只修改 FlightIntent；不发送瞬移序列。
 * KINETIC_HIT 包确认命中后进入 PULL_OVER。</p>
 */
public final class SpearBehavior implements ElytraCombatBehavior {

    private enum State {
        NONE,
        FOLLOW,
        NEAR_FOLLOW,
        PULL_OVER
    }

    private State state = State.NONE;
    private int pullOverTicks;

    @Override
    public void reset() {
        this.state = State.NONE;
        this.pullOverTicks = 0;
    }

    @Override
    public FlightIntent tick(
            ElytraCombat bot,
            TargetSnapshot target,
            FlightIntentPlanner planner,
            FlightPlanConfig planConfig
    ) {
        if (target == null) {
            reset();
            CombatWeaponController.stopSpearUse();
            return FlightIntent.idle(bot.playerLook());
        }

        Vec3 targetPoint = bot.spearUsePredictor.getValue()
                ? target.predictedPosition().add(0.0, target.entity().getEyeHeight(target.entity().getPose()), 0.0)
                : target.entity().getBoundingBox().getCenter();
        double distance = bot.player().getEyePosition().distanceTo(targetPoint);
        Vec3 desired;

        switch (this.state) {
            case NONE -> {
                if (CombatWeaponController.ensureSpearUse()) {
                    this.state = State.FOLLOW;
                }
                desired = followDirection(bot, targetPoint);
            }
            case FOLLOW -> {
                if (distance <= bot.spearEngageRange.getValue()) {
                    this.state = State.NEAR_FOLLOW;
                    desired = nearFollowDirection(bot, target, targetPoint);
                } else {
                    desired = followDirection(bot, targetPoint);
                }
            }
            case NEAR_FOLLOW -> {
                if (distance > bot.spearEngageRange.getValue()) {
                    this.state = State.FOLLOW;
                    desired = followDirection(bot, targetPoint);
                } else {
                    desired = nearFollowDirection(bot, target, targetPoint);
                }
            }
            case PULL_OVER -> {
                this.pullOverTicks++;
                if (this.pullOverTicks > bot.spearPullOverTicks.getValue()) {
                    this.state = State.FOLLOW;
                    this.pullOverTicks = 0;
                    desired = followDirection(bot, targetPoint);
                } else {
                    Vec3 delta = targetPoint.subtract(bot.player().position());
                    desired = new Vec3(-delta.x, Math.abs(delta.y), -delta.z);
                }
            }
            default -> throw new IllegalStateException("Unknown spear state " + this.state);
        }

        if (desired.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(bot.playerLook());
        }
        FlightIntent raw = new FlightIntent(
                desired,
                desired.normalize(),
                bot.controlMode.is(ElytraCombat.ControlMode.DirectVelocity),
                false
        );
        return planner.plan(bot.player(), raw, target.predictedPosition(), planConfig);
    }

    @Override
    public void onHit(CombatHitTracker.HitType hitType) {
        if (hitType == CombatHitTracker.HitType.SPEAR) {
            CombatWeaponController.stopSpearUse();
            this.state = State.PULL_OVER;
            this.pullOverTicks = 0;
        }
    }

    @Override
    public String stateName() {
        return this.state.name();
    }

    private Vec3 followDirection(ElytraCombat bot, Vec3 targetPoint) {
        CombatWeaponController.ensureSpearUse();
        return ensureMinimumLength(targetPoint.subtract(bot.player().getEyePosition()), 6.0);
    }

    private Vec3 nearFollowDirection(ElytraCombat bot, TargetSnapshot target, Vec3 targetPoint) {
        CombatWeaponController.ensureSpearUse();

        if (bot.spearAntiSpear.getValue() && target.usingSpear()) {
            Vec3 antiSpear = antiSpearDirection(bot, target);
            if (antiSpear != null) {
                return antiSpear;
            }
        }

        Vec3 look = targetPoint.subtract(bot.player().getEyePosition());
        if (look.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        look = ensureMinimumLength(look, 6.0);

        boolean aggressive = target.action() != TargetAction.AFK && target.action() != TargetAction.SLOW_SPEED;
        if (CombatWeaponController.canUseSpearAttack()
                && aggressive
                && bot.player().getEyePosition().distanceTo(targetPoint) <= bot.spearEngageRange.getValue() + 2.0) {
            return look.normalize().scale(bot.spearLungeStrength.getValue());
        }
        return look;
    }

    /**
     * 预测对方矛射线是否穿过自身碰撞箱；危险时选择垂直于对方视线的安全侧移。
     */
    private Vec3 antiSpearDirection(ElytraCombat bot, TargetSnapshot target) {
        LivingEntity entity = target.entity();
        if (!(entity instanceof Player player) || !CombatWeaponController.isUsingSpear(player)) {
            return null;
        }

        double distance = player.distanceTo(bot.player());
        if (distance > bot.spearEngageRange.getValue() * 2.0 + bot.spearAntiSpearExtra.getValue()) {
            return null;
        }

        Vec3 predicted = target.predictedPosition();
        Vec3 eye = predicted.add(0.0, player.getEyeHeight(player.getPose()), 0.0);
        Vec3 facing = player.getLookAngle().normalize();
        double reach = target.velocity().dot(facing);
        Vec3 start = eye.add(facing.scale(bot.spearMinRange.getValue()));
        Vec3 end = eye.add(facing.scale(
                bot.spearEngageRange.getValue() + Math.max(0.0, reach) + bot.spearAntiSpearExtra.getValue()
        ));
        if (bot.player().getBoundingBox().clip(start, end).isEmpty()) {
            return null;
        }

        Vec3 delta = target.position().subtract(bot.player().position());
        Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
        if (horizontal.lengthSqr() < 1.0E-8) {
            horizontal = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 side = new Vec3(-horizontal.z, 0.0, horizontal.x).normalize();
        Vec3 playerPos = bot.player().position();
        for (Vec3 candidate : new Vec3[]{
                side.scale(bot.spearLungeStrength.getValue()),
                side.scale(-bot.spearLungeStrength.getValue())
        }) {
            if (LocalFlightAvoidance.isSegmentClear(bot.player(), playerPos, playerPos.add(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static Vec3 ensureMinimumLength(Vec3 vector, double minimum) {
        if (vector.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        return vector.length() < minimum ? vector.normalize().scale(minimum) : vector;
    }
}
