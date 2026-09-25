package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotDamage.ResistantBlockCache;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.BreakMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceBypass;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwitchMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.AsyncResult;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.BreakChoice;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.BreakPlan;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.CrystalSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.PlaceChoice;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.PlaceInfo;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SnapshotData;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.TargetSnapshot;
import com.github.epsilon.modules.orchestration.ModuleDeclaration;
import com.github.epsilon.modules.orchestration.ModulePart;
import com.github.epsilon.modules.orchestration.NodeRef;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * DECIDE 阶段：只读取快照并生成放置、破坏与旋转计划。
 *
 * <p>本 Part 不发送网络包、不攻击、不放置、不切换物品栏，也不写入最终旋转；旋转只以“请求”的形式
 * 提交给 {@link RotationManager}，实际动作在 COMMIT 阶段重新校验后执行。
 *
 * <p>{@link #evaluateSnapshot} 运行在 {@code Zealot+} worker 线程上，因此它以及它调用的所有方法都
 * 只能读取不可变快照，不得触碰 Minecraft 世界、玩家或 Setting。
 */
final class ZealotDecidePart extends ZealotPartBase implements ModulePart {

    private static final float ROTATION_READY_EPSILON = 1.0f;

    /**
     * 本 Part 声明的节点引用；worker 线程不使用它，仅供后续节点表达依赖。
     */
    private NodeRef<PlayerTickEvent.Pre> planNode;

    ZealotDecidePart(ZealotCrystalPlus module) {
        super(module);
    }

    @Override
    public void declare(ModuleDeclaration declaration) {
        // 节点统一由 ZealotSettingsPart 声明，保证节点图集中可见。
    }

    void bindPlanNode(NodeRef<PlayerTickEvent.Pre> planNode) {
        this.planNode = planNode;
    }

    NodeRef<PlayerTickEvent.Pre> planNode() {
        return planNode;
    }

    /**
     * DECIDE 节点：解析当前目标并按需提交预旋转请求。
     *
     * <p>这里会对 {@link RotationManager} 发一次 {@code Priority.High} 的旋转请求，与设计文档
     * “DECIDE 不写共享 Manager 状态”存在张力。保留在该阶段是刻意为之：预旋转必须在 COMMIT 之前
     * 就跑起来，跨 tick 把角度转到位，COMMIT 才能通过 {@link #isRotationReady} 判定并执行动作；
     * 若挪到 COMMIT，预旋转会永远晚一帧，等于删掉该特性。放在这里的是“请求”，最终应用仍在 COMMIT。
     */
    void decidePlan(PlayerTickEvent.Pre event) {
        if (module.observePart.isEatingPaused()) return;
        AsyncResult result = state().asyncResult;
        PlaceInfo prePlace = getValidPlaceInfo(state().cachedRotationPlaceInfo, false);
        state().target = resolveCurrentTarget(result, prePlace);
        if (module.preRotation.getValue()) {
            prepareRotation(getValidBreakPlan(state().cachedRotationBreakPlan), prePlace);
        }
    }

    // ------------------------------------------------------------------
    // worker 线程：候选评估
    // ------------------------------------------------------------------

    /**
     * 在不可变快照上求解放置与破坏候选；该方法及其调用链必须保持无副作用。
     */
    AsyncResult evaluateSnapshot(SnapshotData snapshot, long startTime) {
        if (snapshot.targets().isEmpty()) {
            return new AsyncResult(snapshot.id(), null, null, null, null, null, System.nanoTime() - startTime);
        }

        PlaceInfo rotationPlaceInfo = evaluatePlace(snapshot, false);
        PlaceInfo placeInfo = evaluatePlace(snapshot, true);
        BreakPlan rotationBreakPlan = evaluateBreak(snapshot, rotationPlaceInfo, false);
        BreakPlan breakPlan = evaluateBreak(snapshot, placeInfo, true);
        TargetSnapshot primary = placeInfo != null
                ? snapshot.targets().stream().filter(targetInfo -> targetInfo.entity() == placeInfo.target()).findFirst().orElse(snapshot.targets().getFirst())
                : (rotationPlaceInfo != null
                ? snapshot.targets().stream().filter(targetInfo -> targetInfo.entity() == rotationPlaceInfo.target()).findFirst().orElse(snapshot.targets().getFirst())
                : snapshot.targets().getFirst());

        return new AsyncResult(snapshot.id(), rotationPlaceInfo, placeInfo, rotationBreakPlan, breakPlan, primary, System.nanoTime() - startTime);
    }

    /**
     * 选出最优放置点：lethal → safe → max 三级回退，并剔除会自杀或超出自身伤害上限的候选。
     */
    private PlaceInfo evaluatePlace(SnapshotData snapshot, boolean requireRotation) {
        if (snapshot.placePositions().isEmpty()) return null;

        PlaceChoice max = new PlaceChoice();
        PlaceChoice safe = new PlaceChoice();
        PlaceChoice lethal = new PlaceChoice();

        for (BlockPos pos : snapshot.placePositions()) {
            if (requireRotation && !checkPlaceRotation(pos, snapshot.self().currentRotation(), snapshot.settings().placeRotationRange()))
                continue;

            AABB placeBox = ZealotMath.crystalPlaceBox(pos);
            Vec3 crystalPos = ZealotMath.crystalPos(pos);

            float selfDamage = ZealotDamage.calcDamage(snapshot.self(), crystalPos, snapshot.resistantBlocks());
            float collidingDamage = calcCollidingCrystalDamage(snapshot, placeBox);
            float adjustedDamage = Math.max(selfDamage, collidingDamage - snapshot.settings().collidingCrystalExtraSelfDamageThreshold());

            if (snapshot.self().totalHealth() - adjustedDamage <= snapshot.settings().noSuicide()) continue;
            if (snapshot.self().totalHealth() - collidingDamage <= snapshot.settings().noSuicide()) continue;
            if (!snapshot.settings().lethalOverride() && adjustedDamage > snapshot.settings().placeMaxSelfDamage())
                continue;

            for (TargetSnapshot targetInfo : snapshot.targets()) {
                if (targetInfo.box().intersects(placeBox)) continue;
                float targetDamage = ZealotDamage.calcDamage(targetInfo, crystalPos, snapshot.resistantBlocks(), snapshot.self().difficulty());

                if (snapshot.settings().lethalOverride()
                        && targetDamage - targetInfo.totalHealth() > snapshot.settings().lethalThresholdAddition()
                        && selfDamage < lethal.selfDamage()
                        && selfDamage <= snapshot.settings().lethalMaxSelfDamage()) {
                    lethal.update(targetInfo.entity(), pos, adjustedDamage, targetDamage);
                }

                if (adjustedDamage > snapshot.settings().placeMaxSelfDamage()) continue;

                boolean force = ZealotDamage.shouldForcePlace(snapshot.self(), targetInfo, snapshot.settings());
                float minDamage = force ? snapshot.settings().forcePlaceMinDamage() : snapshot.settings().placeMinDamage();
                float balance = force ? snapshot.settings().forcePlaceBalance() : snapshot.settings().placeBalance();

                if (targetDamage < minDamage || targetDamage - adjustedDamage < balance) continue;

                float score = snapshot.settings().damagePriority().score(adjustedDamage, targetDamage);
                float maxScore = snapshot.settings().damagePriority().score(max.selfDamage(), max.targetDamage());
                if (score > maxScore) {
                    max.update(targetInfo.entity(), pos, adjustedDamage, targetDamage);
                } else if (max.targetDamage() - targetDamage <= snapshot.settings().safeMaxTargetDamageReduction()
                        && max.selfDamage() - adjustedDamage >= snapshot.settings().safeMinSelfDamageReduction()) {
                    safe.update(targetInfo.entity(), pos, adjustedDamage, targetDamage);
                }
            }
        }

        if (max.targetDamage() - safe.targetDamage() > snapshot.settings().safeMaxTargetDamageReduction()
                || max.selfDamage() - safe.selfDamage() <= snapshot.settings().safeMinSelfDamageReduction()) {
            safe.clear();
        }

        PlaceChoice choice = lethal.takeValid();
        if (choice == null) choice = safe.takeValid();
        if (choice == null) choice = max.takeValid();
        if (choice == null) return null;

        return buildPlaceInfo(choice, snapshot.settings().placeSideBypass(), snapshot.self().eyePos());
    }

    /**
     * 选出破坏目标：Target/Own/Smart 优先取与放置盒相交的水晶，All 取离参考实体最近的。
     */
    private BreakPlan evaluateBreak(SnapshotData snapshot, PlaceInfo placeInfo, boolean requireRotation) {
        if (snapshot.crystals().isEmpty()) return null;

        List<CrystalSnapshot> crystalList = snapshot.crystals().stream()
                .filter(CrystalSnapshot::breakable)
                .filter(crystal -> !requireRotation
                        || checkCrystalRotation(crystal.pos(), snapshot.settings().breakRotationRange(), snapshot.self().currentRotation()))
                .toList();
        if (crystalList.isEmpty()) return null;

        CrystalSnapshot crystal = switch (snapshot.settings().breakMode()) {
            case Own -> {
                CrystalSnapshot targetCrystal = getTargetCrystal(placeInfo, crystalList);
                yield targetCrystal != null ? targetCrystal : evaluateBestBreak(snapshot, crystalList.stream().filter(CrystalSnapshot::ownPlaced).toList());
            }
            case Target -> getTargetCrystal(placeInfo, crystalList);
            case Smart -> {
                CrystalSnapshot targetCrystal = getTargetCrystal(placeInfo, crystalList);
                yield targetCrystal != null ? targetCrystal : evaluateBestBreak(snapshot, crystalList);
            }
            case All -> {
                net.minecraft.world.entity.Entity ref = placeInfo != null ? placeInfo.target() : snapshot.self().player();
                yield crystalList.stream().min(Comparator.comparingDouble(info -> ref.distanceToSqr(info.entity()))).orElse(null);
            }
            case Off -> null;
        };

        if (crystal == null) return null;
        float selfDamage = ZealotDamage.calcDamage(snapshot.self(), crystal.pos(), snapshot.resistantBlocks());
        float targetDamage = 0.0f;
        if (!snapshot.targets().isEmpty()) {
            targetDamage = ZealotDamage.calcDamage(snapshot.targets().getFirst(), crystal.pos(), snapshot.resistantBlocks(), snapshot.self().difficulty());
        }
        return new BreakPlan(crystal.entity(), crystal.id(), crystal.pos(), selfDamage, targetDamage);
    }

    private CrystalSnapshot evaluateBestBreak(SnapshotData snapshot, List<CrystalSnapshot> crystalList) {
        if (crystalList.isEmpty()) return null;

        BreakChoice max = new BreakChoice();
        BreakChoice safe = new BreakChoice();
        BreakChoice lethal = new BreakChoice();

        for (CrystalSnapshot crystal : crystalList) {
            float selfDamage = ZealotDamage.calcDamage(snapshot.self(), crystal.pos(), snapshot.resistantBlocks());
            if (snapshot.self().totalHealth() - selfDamage <= snapshot.settings().noSuicide()) continue;
            if (!snapshot.settings().lethalOverride() && selfDamage > snapshot.settings().breakMaxSelfDamage())
                continue;

            for (TargetSnapshot targetInfo : snapshot.targets()) {
                float targetDamage = ZealotDamage.calcDamage(targetInfo, crystal.pos(), snapshot.resistantBlocks(), snapshot.self().difficulty());
                if (snapshot.settings().lethalOverride()
                        && targetDamage - targetInfo.totalHealth() > snapshot.settings().lethalThresholdAddition()
                        && selfDamage < lethal.selfDamage()
                        && selfDamage <= snapshot.settings().lethalMaxSelfDamage()) {
                    lethal.update(crystal, selfDamage, targetDamage);
                }

                if (selfDamage > snapshot.settings().breakMaxSelfDamage()) continue;

                boolean force = ZealotDamage.shouldForcePlace(snapshot.self(), targetInfo, snapshot.settings());
                float minDamage = force ? snapshot.settings().forcePlaceMinDamage() : snapshot.settings().breakMinDamage();
                float balance = force ? snapshot.settings().forcePlaceBalance() : snapshot.settings().breakBalance();
                if (targetDamage < minDamage || targetDamage - selfDamage < balance) continue;

                float score = snapshot.settings().damagePriority().score(selfDamage, targetDamage);
                float maxScore = snapshot.settings().damagePriority().score(max.selfDamage(), max.targetDamage());
                if (score > maxScore) {
                    max.update(crystal, selfDamage, targetDamage);
                } else if (max.targetDamage() - targetDamage <= snapshot.settings().safeMaxTargetDamageReduction()
                        && max.selfDamage() - selfDamage >= snapshot.settings().safeMinSelfDamageReduction()) {
                    safe.update(crystal, selfDamage, targetDamage);
                }
            }
        }

        if (max.targetDamage() - safe.targetDamage() > snapshot.settings().safeMaxTargetDamageReduction()
                || max.selfDamage() - safe.selfDamage() <= snapshot.settings().safeMinSelfDamageReduction()) {
            safe.clear();
        }

        BreakChoice choice = lethal.takeValid();
        if (choice == null) choice = safe.takeValid();
        if (choice == null) choice = max.takeValid();
        return choice != null ? choice.crystal() : null;
    }

    /**
     * 放置盒与水晶体重叠时，会把水晶爆炸伤害一并计算到自身风险里。
     */
    private float calcCollidingCrystalDamage(SnapshotData snapshot, AABB placeBox) {
        float max = 0.0f;
        for (CrystalSnapshot crystal : snapshot.crystals()) {
            if (!placeBox.intersects(crystal.box())) continue;
            float damage = ZealotDamage.calcDamage(snapshot.self(), crystal.pos(), snapshot.resistantBlocks());
            if (damage > max) {
                max = damage;
            }
        }
        return max;
    }

    /**
     * 把评分结果转换为可执行的放置信息，并按绕过模式确定命中面与命中点。
     */
    private PlaceInfo buildPlaceInfo(PlaceChoice choice, PlaceBypass bypass, Vec3 eyePos) {
        Direction side;
        Vec3 hitVec;

        switch (bypass) {
            case Up -> {
                side = Direction.UP;
                hitVec = new Vec3(choice.blockPos().getX() + 0.5, choice.blockPos().getY() + 1.0, choice.blockPos().getZ() + 0.5);
            }
            case Down -> {
                side = Direction.DOWN;
                hitVec = new Vec3(choice.blockPos().getX() + 0.5, choice.blockPos().getY(), choice.blockPos().getZ() + 0.5);
            }
            case Closest -> {
                side = ZealotMath.calcDirection(eyePos, Vec3.atCenterOf(choice.blockPos()));
                hitVec = new Vec3(
                        choice.blockPos().getX() + 0.5 + side.getStepX() * 0.5,
                        choice.blockPos().getY() + 0.5 + side.getStepY() * 0.5,
                        choice.blockPos().getZ() + 0.5 + side.getStepZ() * 0.5
                );
            }
            default -> throw new IllegalStateException("Unexpected value: " + bypass);
        }

        return new PlaceInfo(choice.target(), choice.blockPos(), choice.selfDamage(), choice.targetDamage(), side, hitVec, RotationUtils.calculate(hitVec));
    }

    private LivingEntity resolveCurrentTarget(AsyncResult result, PlaceInfo prePlace) {
        if (prePlace != null) {
            return prePlace.target();
        }
        return result.primaryTarget() != null ? result.primaryTarget().entity() : null;
    }

    // ------------------------------------------------------------------
    // 有效性校验：COMMIT 也复用这些查询
    // ------------------------------------------------------------------

    PlaceInfo getValidPlaceInfo(PlaceInfo placeInfo) {
        return getValidPlaceInfo(placeInfo, true);
    }

    /**
     * 重新校验放置信息是否仍然成立：范围、支撑方块，以及（可选）是否可放置。
     */
    PlaceInfo getValidPlaceInfo(PlaceInfo placeInfo, boolean requirePlaceable) {
        if (placeInfo == null || nullCheck() || mc().player == null || mc().level == null) return null;
        if (module.observePart.placeDistanceSq(mc().player, placeInfo.hitVec().x, placeInfo.hitVec().y, placeInfo.hitVec().z) > module.placeRange.getValue() * module.placeRange.getValue()) {
            return null;
        }
        if (!module.observePart.isCrystalSupport(placeInfo.blockPos())) {
            return null;
        }
        return !requirePlaceable || module.observePart.isPlaceable(placeInfo.blockPos()) ? placeInfo : null;
    }

    /**
     * 重新解析破坏计划：实体必须仍然存活且在破坏范围内。
     */
    BreakPlan getValidBreakPlan(BreakPlan breakPlan) {
        if (breakPlan == null || nullCheck() || mc().level == null) return null;
        Entity entity = mc().level.getEntity(breakPlan.entityId());
        if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) {
            return null;
        }
        return module.observePart.checkBreakRange(crystal.position())
                ? new BreakPlan(crystal, crystal.getId(), crystal.position(), breakPlan.selfDamage(), breakPlan.targetDamage())
                : null;
    }

    /**
     * 优先使用已完成旋转的方案，退化到“旋转已就位”的方案。
     */
    PlaceInfo getActionPlaceInfo() {
        PlaceInfo action = getValidPlaceInfo(state().cachedPlaceInfo, true);
        if (action != null) return action;

        PlaceInfo fallback = getValidPlaceInfo(state().cachedRotationPlaceInfo, true);
        if (fallback != null && checkPlaceRotation(fallback.blockPos(), state().cachedRotationPlaceInfo.rotation(), module.placeRotationRange.getValue())) {
            return fallback;
        }

        return null;
    }

    BreakPlan getActionBreakPlan() {
        BreakPlan action = getValidBreakPlan(state().cachedBreakPlan);
        if (action != null) return action;

        BreakPlan fallback = getValidBreakPlan(state().cachedRotationBreakPlan);
        if (fallback != null && checkCrystalRotation(fallback.pos(), module.breakRotationRange.getValue())) {
            return fallback;
        }

        return null;
    }

    /**
     * 纯函数版本：worker 线程不得调用 {@link RotationManager}，只能使用快照中的旋转。
     */
    private static boolean checkPlaceRotation(BlockPos pos, Rot2f currentRotation, double range) {
        if (range <= 0.0) return true;
        return getRotationDelta(currentRotation, RotationUtils.calculate(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5))) <= range;
    }

    boolean checkCrystalRotation(Vec3 crystalPos, double range) {
        if (range <= 0.0) return true;
        return checkCrystalRotation(crystalPos, range, RotationManager.INSTANCE.getRotation());
    }

    static boolean checkCrystalRotation(Vec3 crystalPos, double range, Rot2f currentRotation) {
        if (range <= 0.0) return true;
        return getRotationDelta(currentRotation, RotationUtils.calculate(crystalPos)) <= range;
    }

    // ------------------------------------------------------------------
    // 旋转请求
    // ------------------------------------------------------------------

    double getRotationSpeed() {
        return Math.max(1.8, module.yawSpeed.getValue());
    }

    /**
     * 预旋转：可破坏时优先朝向水晶，否则朝向放置点。
     */
    private void prepareRotation(BreakPlan breakPlan, PlaceInfo placeInfo) {
        if (shouldPrioritizeBreak(breakPlan)) {
            RotationManager.INSTANCE.setRotations(
                    RotationUtils.calculate(breakPlan.pos()),
                    getRotationSpeed(),
                    null,
                    Priority.High
            );
            return;
        }

        if (module.placeMode.getValue() != PlaceMode.Off && placeInfo != null) {
            RotationManager.INSTANCE.setRotations(placeInfo.rotation(), getRotationSpeed(), null, Priority.High);
        }
    }

    private boolean shouldPrioritizeBreak(BreakPlan breakPlan) {
        return module.preRotation.getValue()
                && module.breakMode.getValue() != BreakMode.Off
                && state().breakTimer.passedMillise(module.breakDelay.getValue())
                && breakPlan != null
                && canAttemptBreak();
    }

    /**
     * 判断此刻是否允许破坏：换手冷却未过，或缺少反虚弱武器时都不能破坏。
     */
    boolean canAttemptBreak() {
        if (module.placeSwitchMode.getValue() != SwitchMode.Ghost
                && module.antiWeakness.getValue() != SwitchMode.Ghost
                && System.currentTimeMillis() - state().lastSwapTime < module.swapDelay.getValue() * 50L) {
            return false;
        }

        if (mc().player == null || !mc().player.hasEffect(MobEffects.WEAKNESS) || module.observePart.isHoldingTool()) {
            return true;
        }

        return module.antiWeakness.getValue() != SwitchMode.Off && module.commitPart.findWeaponSlot() != -1;
    }

    boolean isRotationReady(Rot2f targetRotation) {
        return getRotationDelta(RotationManager.INSTANCE.getRotation(), targetRotation) <= ROTATION_READY_EPSILON;
    }

    static float getRotationDelta(Rot2f from, Rot2f to) {
        float yawDiff = Math.abs(Mth.wrapDegrees(to.getYaw() - from.getYaw()));
        float pitchDiff = Math.abs(to.getPitch() - from.getPitch());
        return (float) Math.hypot(yawDiff, pitchDiff);
    }

    private CrystalSnapshot getTargetCrystal(PlaceInfo placeInfo, List<CrystalSnapshot> crystalList) {
        if (placeInfo == null) return null;
        for (CrystalSnapshot crystal : crystalList) {
            if (ZealotMath.crystalPlaceBoxIntersects(placeInfo.blockPos(), crystal.box())) {
                return crystal;
            }
        }
        return null;
    }
}
