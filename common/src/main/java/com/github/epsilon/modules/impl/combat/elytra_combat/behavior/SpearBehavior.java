package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetAction;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 长矛 kinetic 空战状态机。
 *
 * <p>通过持续使用长矛组件等待 delayTicks，冲锋只修改 FlightIntent；不发送瞬移序列。
 * KINETIC_HIT 包确认命中后进入 PULL_OVER。</p>
 */
public class SpearBehavior implements ElytraCombatBehavior {

    private enum State {
        /**
         * 初始状态：尝试手持长矛并开始蓄力。
         */
        NONE,
        /**
         * 远距离追击目标眼部预测位置。
         */
        FOLLOW,
        /**
         * 进入长矛交战距离，处理冲锋和反向长矛。
         */
        NEAR_FOLLOW,
        /**
         * 命中后反向拉开距离，等待 kinetic 冷却。
         */
        PULL_OVER
    }

    private State state = State.NONE;
    private int pullOverTicks;
    private boolean lunging;
    /** 冲锋条件已连续成立的 tick 数；超过窗口仍未收到 KINETIC_HIT 就判定这一趟打空。 */
    private int lungeTicks;
    private String lastTrace;

    /** 冲锋未收到 KINETIC_HIT 时判定打空的最大连续 tick 数。 */
    private static final int LUNGE_MISS_TICKS = 10;

    /** 水平预测过渡区间（格）：近端使用当前位置，远端使用预测位置，中间线性插值。 */
    private static final double AIM_PREDICTION_NEAR = 3.0;
    private static final double AIM_PREDICTION_FAR = 9.0;

    /** 沿瞄准方向的相对速度下限（格/秒）；低于此值时脱战重整。 */
    private static final double SPEAR_MIN_RELATIVE_SPEED = 5.6;

    @Override
    public void reset() {
        this.state = State.NONE;
        this.pullOverTicks = 0;
        this.lunging = false;
        this.lungeTicks = 0;
        this.lastTrace = null;
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

        // 瞄准碰撞箱中心；水平预测按距离插值，垂直位置不使用预测。
        Vec3 predicted = target.predictedPosition();
        double aimDistance = bot.player().getEyePosition().distanceTo(predicted);
        double blend = bot.spearUsePredictor.getValue()
                ? Mth.clamp(
                        (aimDistance - AIM_PREDICTION_NEAR) / (AIM_PREDICTION_FAR - AIM_PREDICTION_NEAR),
                        0.0,
                        1.0)
                : 0.0;
        Vec3 aimBase = target.position().lerp(predicted, blend);
        Vec3 targetPoint = new Vec3(
                aimBase.x,
                target.position().y + target.entity().getBbHeight() * 0.5,
                aimBase.z
        );
        double distance = bot.player().getEyePosition().distanceTo(targetPoint);
        Vec3 desired;
        this.lunging = false;
        if (this.state != State.NEAR_FOLLOW) {
            this.lungeTicks = 0;
        }

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
                    if (!this.lunging) {
                        this.lungeTicks = 0;
                    } else if (relativeSpeed(bot, target, targetPoint) < SPEAR_MIN_RELATIVE_SPEED) {
                        // 仅在冲锋期间检查相对速度，避免脱战后的反向速度再次触发脱战。
                        this.state = State.PULL_OVER;
                        this.pullOverTicks = 0;
                        this.lungeTicks = 0;
                        ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.slow",
                                "rel=" + ElytraDebug.fmt(relativeSpeed(bot, target, targetPoint))
                                        + " < " + ElytraDebug.fmt(SPEAR_MIN_RELATIVE_SPEED) + " -> PULL_OVER");
                        desired = pullOverDirection(bot, targetPoint);
                    } else if (++this.lungeTicks > LUNGE_MISS_TICKS) {
                        this.state = State.PULL_OVER;
                        this.pullOverTicks = 0;
                        this.lungeTicks = 0;
                        ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.miss",
                                "no kinetic hit in " + LUNGE_MISS_TICKS + " ticks"
                                        + " d=" + ElytraDebug.fmt(distance)
                                        + " rel=" + ElytraDebug.fmt(relativeSpeed(bot, target, targetPoint))
                                        + " -> PULL_OVER");
                        desired = pullOverDirection(bot, targetPoint);
                    }
                }
            }
            case PULL_OVER -> {
                this.pullOverTicks++;
                boolean ticksUp = this.pullOverTicks > bot.spearPullOverTicks.getValue();
                boolean stillClose = distance <= bot.spearEngageRange.getValue();
                boolean outOfExtra = this.pullOverTicks > bot.spearPullOverTicks.getValue() + PULL_OVER_EXTRA_TICKS;
                // 尚未拉开距离时允许有限延长，避免无限脱战。
                boolean finished = ticksUp && (!stillClose || outOfExtra);
                if (finished) {
                    ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.pullover",
                            "end after=" + this.pullOverTicks + " d=" + ElytraDebug.fmt(distance));
                    this.state = State.FOLLOW;
                    this.pullOverTicks = 0;
                    desired = followDirection(bot, targetPoint);
                } else {
                    // 脱战期间重新蓄力，为下次冲锋准备。
                    CombatWeaponController.ensureSpearUse();
                    desired = pullOverDirection(bot, targetPoint);
                }
            }
            default -> throw new IllegalStateException("Unknown spear state " + this.state);
        }

        traceSpearState(bot, target);
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
    public void onHit(CombatHitTracker.HitType hitType) {
        if (hitType == CombatHitTracker.HitType.SPEAR) {
            CombatWeaponController.stopSpearUse();
            this.state = State.PULL_OVER;
            this.pullOverTicks = 0;
            this.lungeTicks = 0;
            ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.hit", "kinetic -> PULL_OVER");
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

    /** 脱战时的水平脱离量（格/tick）。 */
    private static final double PULL_OVER_HORIZONTAL = 3.5;
    /** 脱战时的爬升量（格/tick）。 */
    private static final double PULL_OVER_CLIMB = 1.5;
    /** 配置的脱战 tick 用完后，仍贴在交战距离内时允许额外延长的最大 tick 数。 */
    private static final int PULL_OVER_EXTRA_TICKS = 8;

    /** 以固定水平速度远离目标并小幅爬升，避免近身时方向退化为垂直。 */
    private Vec3 pullOverDirection(ElytraCombat bot, Vec3 targetPoint) {
        Vec3 delta = targetPoint.subtract(bot.player().position());
        Vec3 horizontal = new Vec3(-delta.x, 0.0, -delta.z);
        if (horizontal.lengthSqr() < 1.0E-4) {
            // 已经贴到目标正上/正下方：改用当前水平速度的反方向，避免退化成垂直爬升。
            Vec3 velocity = bot.player().getDeltaMovement();
            horizontal = new Vec3(-velocity.x, 0.0, -velocity.z);
        }
        if (horizontal.lengthSqr() < 1.0E-4) {
            horizontal = new Vec3(1.0, 0.0, 0.0);
        }
        return horizontal.normalize().scale(PULL_OVER_HORIZONTAL).add(0.0, PULL_OVER_CLIMB, 0.0);
    }

    /** 沿瞄准方向投影双方速度差，单位格/秒。 */
    private static double relativeSpeed(ElytraCombat bot, TargetSnapshot target, Vec3 targetPoint) {
        Vec3 aimDirection = targetPoint.subtract(bot.player().getEyePosition());
        if (aimDirection.lengthSqr() < 1.0E-8) {
            return 0.0;
        }
        aimDirection = aimDirection.normalize();
        return aimDirection.dot(bot.player().getDeltaMovement().scale(20.0))
                - aimDirection.dot(target.velocity().scale(20.0));
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

        this.lunging = CombatWeaponController.canUseSpearAttack()
                && bot.player().getEyePosition().distanceTo(targetPoint) <= bot.spearEngageRange.getValue() + 2.0;
        if (this.lunging) {
            return look.normalize().scale(bot.spearLungeStrength.getValue());
        }
        return look;
    }

    /** 决策摘要发生变化时输出，蓄力进度按区间量化。 */
    private void traceSpearState(ElytraCombat bot, TargetSnapshot target) {
        boolean using = CombatWeaponController.isUsingSpear(bot.player());
        boolean ready = CombatWeaponController.canUseSpearAttack();
        boolean aggressive = target.action() != TargetAction.AFK && target.action() != TargetAction.SLOW_SPEED;
        String charge = using && !ready
                ? " charge=" + bot.player().getTicksUsingItem() / 5 * 5
                        + "/" + CombatWeaponController.spearReadyTicks()
                        + "/" + CombatWeaponController.spearDamageWindowTicks()
                        + " hold=" + CombatWeaponController.isSpearChargeConsistent()
                        + " hand=" + bot.player().getUsedItemHand()
                        + " restarts=" + CombatWeaponController.spearRestartCount() / 20 * 20
                : "";
        String summary = this.state.name()
                + " action=" + target.action()
                + " using=" + using
                + " ready=" + ready
                + " aggressive=" + aggressive
                + " lunge=" + this.lunging
                + " turn=" + ElytraDebug.fmt(bot.maxTurnSpeed.getValue())
                + charge;
        if (summary.equals(this.lastTrace)) {
            return;
        }
        this.lastTrace = summary;
        ElytraDebug.log(ElytraDebug.SLOT_SPEAR_STATE, "spear.state", summary);
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
        // 用目标眼部预测位置和视线方向构造一根虚拟长矛射线。
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

        // 选取垂直于对方视线的水平侧移方向，优先能保持碰撞箱安全的一侧。
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
