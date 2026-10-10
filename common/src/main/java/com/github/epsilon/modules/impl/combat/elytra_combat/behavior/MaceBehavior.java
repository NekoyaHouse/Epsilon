package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import net.minecraft.client.player.LocalPlayer;
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
 * <p>状态流转为 NONE -> PULL_UP -> FOLLOW。攻击本身交给 KillAura，本状态机只负责占位与高度：
 * 拉升到目标上方配置高度后在目标上方跟随，等待 KillAura 出手。</p>
 */
public class MaceBehavior implements ElytraCombatBehavior {

    private enum State {
        /**
         * 初始状态：根据当前高度差决定直接跟随还是先拉升。
         */
        NONE,
        /**
         * 持续拉升到目标上方安全高度。
         */
        PULL_UP,
        /**
         * 空中跟随或搜索地面目标的落点。
         */
        FOLLOW
    }

    private State state = State.NONE;
    private int pullUpStartTick;
    /** 头顶受阻的连续 tick 数，用于抑制状态在边界上逐 tick 互抢。 */
    private int headBlockedTicks;
    /** 已选中的地面落点；有效期间保持不变。 */
    private BlockPos lastGroundCandidate;
    /** 俯冲攻击后的改出剩余 tick 数。 */
    private int recoveryTicks;
    /** 本次俯冲进入攻击距离后还剩几个下压 tick；归零说明这一趟打空了。 */
    private int strikeTicks;

    /** 探测条件必须连续成立这么多 tick 才允许切换状态，避免拉升与下压互相抢转向。 */
    private static final int PROBE_LATCH_TICKS = 3;

    /** 进入攻击距离后等待 KillAura 出手的下压窗口（tick）。 */
    private static final int STRIKE_TICKS = 4;

    /** 未进入攻击距离时，判定俯冲打空的高度带（格）。 */
    private static final double MISS_ALTITUDE_BAND = 3.0;

    /** 拉升目标高度的到达容差（格）。 */
    private static final double PULL_UP_ARRIVE_TOLERANCE = 2.0;

    @Override
    public void reset() {
        this.state = State.NONE;
        this.pullUpStartTick = 0;
        this.headBlockedTicks = 0;
        this.lastGroundCandidate = null;
        this.recoveryTicks = 0;
        this.strikeTicks = 0;
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
                // 已经处于俯冲高度时无需再拉升，直接进入跟随段。
                if (bot.player().fallDistance > 4.0
                        && bot.player().getY() > target.entity().getY() + 4.0) {
                    this.state = State.FOLLOW;
                } else {
                    enterPullUp(tick);
                }
                desired = pullUp(bot, targetPos, target);
            }
            case PULL_UP -> {
                if (this.pullUpStartTick <= 0) {
                    this.pullUpStartTick = tick;
                    this.recoveryTicks = RECOVERY_TICKS;
                }
                if (headBlocked(bot)) {
                    this.headBlockedTicks++;
                } else {
                    this.headBlockedTicks = 0;
                }
                if (this.headBlockedTicks >= PROBE_LATCH_TICKS) {
                    this.state = State.FOLLOW;
                    desired = follow(bot, targetPos, target);
                    break;
                }

                // 到达高度与拉升目标使用同一配置值。
                double followHeight = target.supported()
                        ? bot.maceGroundHeight.getValue()
                        : bot.maceHeight.getValue();
                double aimY = target.entity().getY() + followHeight;
                boolean arrived = bot.player().getY() > target.entity().getY()
                        && aimY - bot.player().getY() <= PULL_UP_ARRIVE_TOLERANCE;
                boolean mayFollow = arrived
                        || (bot.player().getY() > target.entity().getY()
                        && tick - this.pullUpStartTick > bot.macePullUpTicks.getValue());
                if (mayFollow) {
                    this.state = State.FOLLOW;
                    desired = target.supported() && !inAttackRange(bot, target)
                            ? groundApproach(bot, target)
                            : followOrHold(bot, targetPos, target);
                } else {
                    if (this.recoveryTicks > 0) {
                        this.recoveryTicks--;
                        desired = recover(bot, targetPos, target);
                    } else {
                        desired = pullUp(bot, targetPos, target);
                    }
                }
            }
            case FOLLOW -> {
                // 地面目标需要先找可攻击落点；空中目标直接追预测位置。
                if (target.supported()) {
                    desired = strikeOrReapproach(bot, targetPos, target, tick);
                } else if (bot.player().fallDistance < 1.0E-6 && bot.lastFallDistance > 1.0E-6) {
                    enterPullUp(tick);
                    desired = pullUp(bot, targetPos, target);
                } else {
                    desired = follow(bot, targetPos, target);
                }
            }
            default -> throw new IllegalStateException("Unknown mace state " + this.state);
        }

        bot.lastFallDistance = bot.player().fallDistance;
        ElytraDebug.log(ElytraDebug.SLOT_MACE_STATE, "mace.state",
                this.state.name()
                        + " head=" + this.headBlockedTicks
                        + " y=" + ElytraDebug.fmt(bot.player().getY())
                        + " ty=" + ElytraDebug.fmt(targetPos.y)
                        + " fall=" + ElytraDebug.fmt(bot.player().fallDistance));
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

    /** 监听本地攻击事件进入拉升；滑翔时不能依赖猛击伤害包或 fallDistance 归零。 */
    @Override
    public void onAttack(LivingEntity target) {
        beginRecoveryPullUp("attack");
    }

    @Override
    public void onHit(CombatHitTracker.HitType hitType) {
        if (hitType == CombatHitTracker.HitType.MACE) {
            beginRecoveryPullUp("mace hit");
        }
    }

    /**
     * 进入带改出阶段的拉升：{@code pullUpStartTick} 置 -1，下一 tick 由 {@link #tick} 初始化，
     * 先沿当前航向爬升 {@link #RECOVERY_TICKS} 个 tick 再回头瞄准目标上方高度。
     */
    private void beginRecoveryPullUp(String reason) {
        this.state = State.PULL_UP;
        this.pullUpStartTick = -1;
        ElytraDebug.log(ElytraDebug.SLOT_MACE_STATE, "mace.trigger", reason + " -> PULL_UP");
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

    private boolean headBlocked(ElytraCombat bot) {
        Vec3 position = bot.player().position();
        boolean blocked = !LocalFlightAvoidance.isSegmentClear(bot.player(), position, position.add(0.0, 0.1, 0.0));
        if (ElytraDebug.enabled) {
            ElytraDebug.log(ElytraDebug.SLOT_PROBE, "probe.head",
                    blocked + " run=" + (blocked ? this.headBlockedTicks + 1 : 0));
        }
        return blocked;
    }

    /** 进入攻击距离后保持配置高度，攻击由 KillAura 负责。 */
    private Vec3 followOrHold(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target) {
        return pullUp(bot, targetPos, target);
    }

    /** 保持下压窗口等待 KillAura 出手；窗口结束仍未攻击则改出并重新拉升。 */
    private Vec3 strikeOrReapproach(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target, int tick) {
        if (inAttackRange(bot, target)) {
            if (this.strikeTicks <= 0) {
                this.strikeTicks = STRIKE_TICKS;
                return groundApproach(bot, target);
            }
            if (--this.strikeTicks > 0) {
                return groundApproach(bot, target);
            }
            this.strikeTicks = 0;
            beginRecoveryPullUp(missReason(bot, target));
            return recover(bot, targetPos, target);
        }

        if (Math.abs(bot.player().getY() - target.entity().getY()) < MISS_ALTITUDE_BAND) {
            this.strikeTicks = 0;
            beginRecoveryPullUp(missReason(bot, target));
            return recover(bot, targetPos, target);
        }

        this.strikeTicks = 0;
        return groundApproach(bot, target);
    }

    private static String missReason(ElytraCombat bot, TargetSnapshot target) {
        return "miss d=" + ElytraDebug.fmt(bot.player().distanceTo(target.entity()))
                + " dy=" + ElytraDebug.fmt(bot.player().getY() - target.entity().getY());
    }

    private boolean inAttackRange(ElytraCombat bot, TargetSnapshot target) {
        return bot.player().isWithinEntityInteractionRange(target.entity().getBoundingBox(), 0.5);
    }

    /** 俯冲攻击后先沿当前水平航向爬升，再转向目标上方。 */
    private Vec3 recover(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target) {
        LocalPlayer player = bot.player();
        Vec3 velocity = player.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontal.lengthSqr() < 1.0E-4) {
            return pullUp(bot, targetPos, target);
        }
        double height = target.supported() ? bot.maceGroundHeight.getValue() : bot.maceHeight.getValue();
        double climb = targetPos.y + height - player.getY();
        return horizontal.normalize().scale(RECOVERY_HORIZONTAL_SPEED)
                .add(0.0, Math.max(RECOVERY_CLIMB, climb), 0.0);
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

        // 有效落点保持不变，避免在相邻候选间频繁切换。
        if (this.lastGroundCandidate != null) {
            Vec3 center = Vec3.atCenterOf(this.lastGroundCandidate);
            if (playerPos.distanceToSqr(center) < 2.25 || !isGroundCandidateUsable(bot, target, center)) {
                this.lastGroundCandidate = null;
            } else {
                return ensureMinimumLength(center.subtract(playerPos), 5.0);
            }
        }

        for (BlockPos candidatePos : candidates) {
            Vec3 center = Vec3.atCenterOf(candidatePos);
            if (!isGroundCandidateUsable(bot, target, center)) {
                continue;
            }
            this.lastGroundCandidate = candidatePos;
            return ensureMinimumLength(center.subtract(playerPos), 5.0);
        }
        this.lastGroundCandidate = null;
        return ensureMinimumLength(targetEye.subtract(playerPos), 5.0);
    }

    /**
     * 落点是否仍然可用：满足攻击距离（或视线可达）且玩家碰撞箱能放下。
     */
    private boolean isGroundCandidateUsable(ElytraCombat bot, TargetSnapshot target, Vec3 center) {
        if (!bot.player().isWithinEntityInteractionRange(target.entity().getBoundingBox(), 0.5)
                && center.distanceToSqr(target.entity().getEyePosition()) > bot.maceEngageRange.getValue() * bot.maceEngageRange.getValue()) {
            return false;
        }
        if (bot.player().level().clip(new net.minecraft.world.level.ClipContext(
                bot.player().position(),
                center,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                bot.player()
        )).getType() != HitResult.Type.MISS) {
            return false;
        }
        AABB box = bot.player().getDimensions(bot.player().getPose()).makeBoundingBox(center);
        return bot.player().level().noBlockCollision(bot.player(), box);
    }

    private static Vec3 ensureMinimumLength(Vec3 vector, double minimum) {
        if (vector.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        return vector.length() < minimum ? vector.normalize().scale(minimum) : vector;
    }

    /** 俯冲攻击后的改出阶段：持续 tick 数、水平速度与抬升分量。 */
    private static final int RECOVERY_TICKS = 5;
    private static final double RECOVERY_HORIZONTAL_SPEED = 6.0;
    private static final double RECOVERY_CLIMB = 8.0;

    private Vec3 pullUp(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target) {
        Vec3 result = pullUpDirection(bot, targetPos, target);
        ElytraDebug.log(ElytraDebug.SLOT_MACE_MANEUVER, "maneuver.pullup", vec(result));
        return result;
    }

    private Vec3 follow(ElytraCombat bot, Vec3 targetPos, TargetSnapshot target) {
        Vec3 result = followDirection(bot, targetPos, target);
        ElytraDebug.log(ElytraDebug.SLOT_MACE_MANEUVER, "maneuver.follow", vec(result));
        return result;
    }

    private static String vec(Vec3 value) {
        return value == null ? "null"
                : "(" + ElytraDebug.fmt(value.x) + "," + ElytraDebug.fmt(value.y) + "," + ElytraDebug.fmt(value.z) + ")";
    }

}
