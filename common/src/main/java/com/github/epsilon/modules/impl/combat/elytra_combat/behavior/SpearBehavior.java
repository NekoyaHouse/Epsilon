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
    /** 本 tick 是否走了冲锋分支；仅用于调试摘要。 */
    private boolean lunging;
    /** 冲锋条件已连续成立的 tick 数；超过窗口仍未收到 KINETIC_HIT 就判定这一趟打空。 */
    private int lungeTicks;
    /** 上一次打印的调试摘要，只在离散判定变化时输出，避免逐 tick 刷屏。 */
    private String lastTrace;

    /**
     * 冲锋窗口：冲锋条件连续成立这么多 tick 仍未收到 KINETIC_HIT，就判定这一趟打空并直接脱战。
     *
     * <p>滑翔的最小转弯半径约等于"速度 / 最大角速度"，在 1~2 格的近距离上根本转不过来：冲过目标
     * 后会在其上方变成俯冲姿态再绕回来，表现为贴着目标画圈（实测冲锋持续 20+ tick 都打不中）。
     * 实测一次成功的冲锋通常 3~4 tick 内就命中，因此留到 10 tick 已经足够宽松。</p>
     */
    private static final int LUNGE_MISS_TICKS = 10;

    /**
     * 水平提前量的过渡区间（格）：该距离以内完全用当前位置，以外完全用预测位置，中间线性插值。
     *
     * <p>不能用硬阈值切换：跨越边界的瞬间瞄准点会跳一下，表现为接近目标时"姿态突然偏一下"。
     * 判定射线长度 = 3 + 前向速度（通常 4~5 格），所以近处必须用当前位置，否则射线会从目标身边滑过。</p>
     */
    private static final double AIM_PREDICTION_NEAR = 3.0;
    private static final double AIM_PREDICTION_FAR = 9.0;

    /**
     * 沿瞄准方向的相对速度下限（格/秒）；低于它就脱战重整。
     *
     * <p>与 26.3 的 kinetic 判定同一尺度：原版用 {@code look·(getKnownSpeed()*20)} 算攻守双方的
     * 投影速度来决定是否造成伤害，下界合金矛的伤害条件是相对速度 ≥ 4.6。低于这个下限继续贴上去
     * 也打不出伤害（横着绕圈时投影速度接近 0），只会白挨一轮。</p>
     */
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

        // 瞄准碰撞箱中心而不是眼部：判定射线从我的眼部出发，瞄准中心时上下各留半个身高的容错，
        // 而瞄准眼部只剩头顶约 0.2 格的余量，俯仰稍偏就会从头顶擦过。
        // 垂直分量不跟随预测：预测的 y 在目标走动/跳跃时会抖，会直接放大成俯仰抖动。
        // 水平提前量按距离线性过渡（见 AIM_PREDICTION_NEAR/FAR），避免跨越阈值时姿态跳变。
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
        // 只有近身段才累计冲锋窗口，离开该状态就清零，避免把追击时间也算成冲锋。
        if (this.state != State.NEAR_FOLLOW) {
            this.lungeTicks = 0;
        }

        switch (this.state) {
            case NONE -> {
                // 长矛命中依赖持续使用物品，未成功手持前停留在远程跟随。
                if (CombatWeaponController.ensureSpearUse()) {
                    this.state = State.FOLLOW;
                }
                desired = followDirection(bot, targetPoint);
            }
            case FOLLOW -> {
                // 进入交战距离后切换近身逻辑，准备蓄力完成后的冲锋。
                if (distance <= bot.spearEngageRange.getValue()) {
                    this.state = State.NEAR_FOLLOW;
                    desired = nearFollowDirection(bot, target, targetPoint);
                } else {
                    desired = followDirection(bot, targetPoint);
                }
            }
            case NEAR_FOLLOW -> {
                if (distance > bot.spearEngageRange.getValue()) {
                    // 目标脱离范围则回到普通追击，避免持续贴脸。
                    this.state = State.FOLLOW;
                    desired = followDirection(bot, targetPoint);
                } else {
                    desired = nearFollowDirection(bot, target, targetPoint);
                    if (!this.lunging) {
                        this.lungeTicks = 0;
                    } else if (relativeSpeed(bot, target, targetPoint) < SPEAR_MIN_RELATIVE_SPEED) {
                        // 只在冲锋中（蓄力就绪、距离也够）才用相对速度判定：刚脱战回来时速度还是
                        // 背着目标的，那时判定会立刻再次脱战、形成死循环。冲锋中相对速度仍低于
                        // 伤害门槛，说明是在横着绕圈而不是撞向目标，继续贴只会白挨一轮。
                        this.state = State.PULL_OVER;
                        this.pullOverTicks = 0;
                        this.lungeTicks = 0;
                        ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.slow",
                                "rel=" + ElytraDebug.fmt(relativeSpeed(bot, target, targetPoint))
                                        + " < " + ElytraDebug.fmt(SPEAR_MIN_RELATIVE_SPEED) + " -> PULL_OVER");
                        desired = pullOverDirection(bot, targetPoint);
                    } else if (++this.lungeTicks > LUNGE_MISS_TICKS) {
                        // 冲锋一直打不中：继续贴只会绕着目标画圈（滑翔转弯半径大于目标距离），
                        // 直接按打空处理，脱战重整后再重新进入。
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
                // 命中后水平远离、小幅爬升，快速脱离对方长矛反击范围。
                this.pullOverTicks++;
                boolean ticksUp = this.pullOverTicks > bot.spearPullOverTicks.getValue();
                boolean stillClose = distance <= bot.spearEngageRange.getValue();
                boolean outOfExtra = this.pullOverTicks > bot.spearPullOverTicks.getValue() + PULL_OVER_EXTRA_TICKS;
                // 配置的 tick 用完时若仍贴在对手身上，就还没真正脱开，允许再脱一段；
                // 硬上限保证被追死时不会无限脱战。
                boolean finished = ticksUp && (!stillClose || outOfExtra);
                if (finished) {
                    // 带上结束距离：脱战有没有真的拉开空间，一眼可查。
                    ElytraDebug.log(ElytraDebug.SLOT_SPEAR_HIT, "spear.pullover",
                            "end after=" + this.pullOverTicks + " d=" + ElytraDebug.fmt(distance));
                    this.state = State.FOLLOW;
                    this.pullOverTicks = 0;
                    desired = followDirection(bot, targetPoint);
                } else {
                    // 脱战期间继续保持蓄力：命中时已经为了让服务端结算而松手一次，这里立刻重新起手，
                    // 脱战结束时蓄力已经攒够，不必再等一个 delayTicks 才有下一次冲锋可用。
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
                // 与 Follow / Mace 一致：是否真的放烟花交给 ElytraCombat.shouldUseFirework() 统一裁决。
                // 这里若写死 false，会把 Allow Firework 直接与掉，导致该开关在 Spear 模式下永远不生效。
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

    /** 脱战时的水平脱离量（格/tick）：贴脸命中时 delta 的水平分量趋近 0，必须用固定值兜底。 */
    private static final double PULL_OVER_HORIZONTAL = 3.5;
    /** 脱战时的爬升量（格/tick）：保持小幅，否则脱战会变成原地垂直爬升。 */
    private static final double PULL_OVER_CLIMB = 1.5;
    /** 配置的脱战 tick 用完后，仍贴在交战距离内时允许额外延长的最大 tick 数。 */
    private static final int PULL_OVER_EXTRA_TICKS = 8;

    /**
     * 脱战方向：水平远离目标为主 + 小幅爬升。
     *
     * <p>原来直接对 {@code targetPoint - playerPos} 取 {@code (-x, |y|, -z)}：近距离命中时水平分量
     * 趋近 0，意图退化成"原地垂直爬升"，而爬升又让 {@code |Δy|} 继续变大形成正反馈，水平距离反而
     * 拉不开（实测 7 tick 只从 0.90 拉到 2.52，轨迹是向上绕而不是远离）。</p>
     */
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

    /**
     * 沿瞄准方向的相对速度（格/秒），与 26.3 的 kinetic 判定保持同一尺度与符号。
     *
     * <p>原版在 {@code KineticWeapon.damageEntities} 里用 {@code look·(getKnownSpeed()*20)} 分别算
     * 攻守双方的投影速度并取差值作为伤害条件；这里用"我的眼部到瞄准点"的方向近似服务端视线
     * （静默旋转下服务端用的就是我们请求的旋转）。</p>
     */
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

        // 不再用"目标是否在动"挡冲锋：kinetic 判定只看相对速度，静止目标反而更容易满足（我的
        // 前向速度本身就是相对速度）；而接近逻辑又是直冲，两者相叠必然变成"贴上去却永远不打"。
        this.lunging = CombatWeaponController.canUseSpearAttack()
                && bot.player().getEyePosition().distanceTo(targetPoint) <= bot.spearEngageRange.getValue() + 2.0;
        if (this.lunging) {
            return look.normalize().scale(bot.spearLungeStrength.getValue());
        }
        return look;
    }

    /**
     * 打印离散化的 Spear 决策摘要。
     *
     * <p>只保留会变化的判定项（状态、目标动作、是否真的在使用长矛、是否已进入伤害窗口、是否满足冲锋
     * 条件），并在内容变化时才输出。这样聊天栏里每一行都代表一次真实的决策切换，同时能直接区分三种
     * "看起来像卡死"的情况：找不到长矛（state=NONE）、接口谎报成功（state=FOLLOW 但 using=false）、
     * 蓄力就绪却因目标静止而不冲锋（ready=true 但 aggressive=false）。</p>
     */
    private void traceSpearState(ElytraCombat bot, TargetSnapshot target) {
        boolean using = CombatWeaponController.isUsingSpear(bot.player());
        boolean ready = CombatWeaponController.canUseSpearAttack();
        boolean aggressive = target.action() != TargetAction.AFK && target.action() != TargetAction.SLOW_SPEED;
        // 未就绪时附带蓄力诊断（ticksUsed / 起手延迟 / 窗口上界，按 5 tick 量化以免逐 tick 刷屏）：
        // hold 是 updatingUsingItem 的继续判据，为 false 说明蓄力每 tick 被客户端自己中断；
        // restarts 按 20 次量化，持续增长就说明 ensureSpearUse 在反复重新起手。
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
                // Max Turn Speed >= 360 会让意图层完全跳过转向限速，期望方向可以一 tick 翻转 180°，
                // 服务端视线也跟着跳。这里把它打出来，便于确认是不是配置导致转头抽。
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
