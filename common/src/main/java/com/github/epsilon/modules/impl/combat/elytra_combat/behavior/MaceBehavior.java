package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 重锤空袭状态机。
 *
 * <p>状态流转为 NONE -> PULL_UP -> FOLLOW -> WAIT_ATTACK。拉升阶段先检查头顶空间，
 * 攻击阶段只使用当前实体交互 reach，不执行 Slimefun 的 VClip/TP 扩展。</p>
 */
public final class MaceBehavior implements ElytraCombatBehavior {

    private enum State {
        NONE,
        PULL_UP,
        FOLLOW,
        WAIT_ATTACK
    }

    private State state = State.NONE;
    private int pullUpStartTick;
    private int waitAttackTicks;

    @Override
    public void reset() {
        this.state = State.NONE;
        this.pullUpStartTick = 0;
        this.waitAttackTicks = 0;
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
            return FlightIntent.idle(bot.playerLook());
        }

        int tick = bot.player().tickCount;
        Vec3 targetPos = bot.maceUsePredictor.getValue() ? target.predictedPosition() : target.position();
        Vec3 desired;

        switch (this.state) {
            case NONE -> {
                if (bot.player().fallDistance > 4.0
                        && bot.player().getY() > target.entity().getY() + 4.0) {
                    this.state = State.FOLLOW;
                } else {
                    enterPullUp(tick);
                }
                desired = pullUpDirection(bot, targetPos, target);
            }
            case PULL_UP -> {
                if (this.pullUpStartTick <= 0) {
                    this.pullUpStartTick = tick;
                }
                if (headBlocked(bot)) {
                    this.state = State.FOLLOW;
                    desired = followDirection(bot, targetPos, target);
                    break;
                }

                boolean mayFollow = bot.player().getY() >= target.entity().getY() + bot.maceHeight.getValue()
                        || (bot.player().getY() > target.entity().getY()
                        && tick - this.pullUpStartTick > bot.macePullUpTicks.getValue() + bot.maceHeight.getValue());
                if (mayFollow) {
                    this.state = State.FOLLOW;
                    desired = target.supported()
                            ? groundApproach(bot, target)
                            : followDirection(bot, targetPos, target);
                } else {
                    desired = pullUpDirection(bot, targetPos, target);
                }
            }
            case FOLLOW -> {
                if (target.supported()) {
                    desired = groundApproach(bot, target);
                } else if (bot.player().fallDistance < 1.0E-6 && bot.lastFallDistance > 1.0E-6) {
                    enterPullUp(tick);
                    desired = pullUpDirection(bot, targetPos, target);
                } else {
                    desired = followDirection(bot, targetPos, target);
                }

                if (canAttack(bot, target.entity())) {
                    CombatWeaponController.attackMace(
                            target.entity(),
                            bot.maceAntiShield.getValue(),
                            bot.maceSwingHand.getValue(),
                            0.5
                    );
                    this.state = State.WAIT_ATTACK;
                    this.waitAttackTicks = 0;
                }
            }
            case WAIT_ATTACK -> {
                this.waitAttackTicks++;
                desired = target.supported()
                        ? groundApproach(bot, target)
                        : pullUpDirection(bot, targetPos, target);
                if (this.waitAttackTicks > 2) {
                    this.state = target.supported() ? State.NONE : State.FOLLOW;
                }
            }
            default -> throw new IllegalStateException("Unknown mace state " + this.state);
        }

        bot.lastFallDistance = bot.player().fallDistance;
        if (desired.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(bot.playerLook());
        }
        FlightIntent raw = new FlightIntent(
                desired,
                desired.normalize(),
                bot.controlMode.is(ElytraCombat.ControlMode.DirectVelocity),
                true
        );
        return planner.plan(bot.player(), raw, target.predictedPosition(), planConfig);
    }

    @Override
    public void onAttack(LivingEntity target) {
        this.state = State.WAIT_ATTACK;
        this.waitAttackTicks = 0;
    }

    @Override
    public void onHit(CombatHitTracker.HitType hitType) {
        if (hitType == CombatHitTracker.HitType.MACE) {
            this.state = State.PULL_UP;
            this.pullUpStartTick = -1;
        }
    }

    @Override
    public String stateName() {
        return this.state.name();
    }

    private void enterPullUp(int tick) {
        this.state = State.PULL_UP;
        this.pullUpStartTick = tick;
    }

    private Vec3 pullUpDirection(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target) {
        double height = target.supported() ? bot.maceGroundHeight.getValue() : bot.maceHeight.getValue();
        Vec3 movement = new Vec3(targetPos.x, targetPos.y + height, targetPos.z)
                .subtract(bot.player().position());
        return ensureMinimumLength(movement, 5.0);
    }

    private Vec3 followDirection(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target) {
        Vec3 movement = targetPos.subtract(bot.player().position());
        if (bot.maceYBias.getValue() != 0.0 && !target.supported()) {
            movement = movement.add(0.0, bot.maceYBias.getValue(), 0.0);
        }
        if (bot.maceSmoothFlight.getValue()
                && movement.y < 0.0
                && bot.player().getY() > target.entity().getY() + bot.maceFollowMinHeight.getValue()) {
            double horizontal = Math.max(0.001, movement.horizontalDistance());
            double downAngle = bot.maceAngleOptimize.getValue() ? bot.maceDownAngle.getValue() : 30.5;
            movement = new Vec3(
                    movement.x,
                    -horizontal * Math.tan(Math.toRadians(downAngle)),
                    movement.z
            );
        }
        return ensureMinimumLength(movement, 5.0);
    }

    private boolean canAttack(ElytraCombat bot, LivingEntity target) {
        if (!bot.player().isWithinEntityInteractionRange(target, 0.25)) {
            return false;
        }
        if (bot.player().getAttackStrengthScale(0.5f) < bot.maceAttackThreshold.getValue()) {
            return false;
        }
        return bot.player().fallDistance > 1.5;
    }

    private boolean headBlocked(ElytraCombat bot) {
        Vec3 position = bot.player().position();
        return !LocalFlightAvoidance.isSegmentClear(bot.player(), position, position.add(0.0, 0.1, 0.0));
    }

    /**
     * 地面目标被遮挡时，在射线命中点周围搜索满足攻击距离、视线和碰撞空间的候选落点。
     */
    private Vec3 groundApproach(ElytraCombat bot, TargetSnapshot target) {
        Vec3 playerPos = bot.player().position();
        Vec3 targetEye = target.entity().getEyePosition();
        BlockHitResult hit = bot.player().level().clip(new net.minecraft.world.level.ClipContext(
                bot.player().getEyePosition(),
                targetEye,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                bot.player()
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return ensureMinimumLength(targetEye.subtract(playerPos), 5.0);
        }

        int radius = Math.min(4, Math.max(1, (int) Math.ceil(bot.maceEngageRange.getValue())));
        BlockPos hitPos = hit.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1));
        for (int x = hitPos.getX() - radius; x <= hitPos.getX() + radius; x++) {
            for (int y = hitPos.getY() - radius; y <= hitPos.getY() + radius; y++) {
                for (int z = hitPos.getZ() - radius; z <= hitPos.getZ() + radius; z++) {
                    candidates.add(new BlockPos(x, y, z));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(targetEye)));

        for (BlockPos candidatePos : candidates) {
            Vec3 center = Vec3.atCenterOf(candidatePos);
            if (!bot.player().isWithinEntityInteractionRange(target.entity().getBoundingBox(), 0.5)
                    && center.distanceToSqr(targetEye) > bot.maceEngageRange.getValue() * bot.maceEngageRange.getValue()) {
                continue;
            }
            if (bot.player().level().clip(new net.minecraft.world.level.ClipContext(
                    playerPos,
                    center,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    bot.player()
            )).getType() != HitResult.Type.MISS) {
                continue;
            }
            AABB box = bot.player().getDimensions(bot.player().getPose()).makeBoundingBox(center);
            if (!bot.player().level().noBlockCollision(bot.player(), box)) {
                continue;
            }
            return ensureMinimumLength(center.subtract(playerPos), 5.0);
        }
        return ensureMinimumLength(targetEye.subtract(playerPos), 5.0);
    }

    private static Vec3 ensureMinimumLength(Vec3 vector, double minimum) {
        if (vector.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        return vector.length() < minimum ? vector.normalize().scale(minimum) : vector;
    }
}
