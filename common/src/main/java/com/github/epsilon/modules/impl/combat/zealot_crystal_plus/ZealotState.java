package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.AsyncResult;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.BreakPlan;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.PlaceInfo;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SnapshotData;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZealotCrystalPlus 的运行时状态。
 *
 * <p>这里只保存可变状态，Setting 仍由父 Module 持有。主线程与 {@code Zealot+} worker 之间交换的字段
 * 必须是 volatile：worker 只读快照、只写 {@link #asyncResult} 与 {@code cached*} 结果。
 */
final class ZealotState {

    /**
     * 单次快照的放置/破坏计划，由 worker 计算、主线程读取，因此必须 volatile。
     */
    volatile AsyncResult asyncResult = AsyncResult.EMPTY;
    volatile PlaceInfo cachedRotationPlaceInfo;
    volatile PlaceInfo cachedPlaceInfo;
    volatile BreakPlan cachedRotationBreakPlan;
    volatile BreakPlan cachedBreakPlan;

    /** 交给 worker 的最新快照；{@code pending} 只在 worker 取走前有效。 */
    volatile SnapshotData latestSnapshot;
    volatile SnapshotData pendingSnapshot;

    /** 放置候选列表带短 TTL 缓存，避免每帧重扫世界。 */
    volatile List<BlockPos> cachedRawPosList = List.of();
    volatile long rawPosListExpireAt;

    /**
     * 当前锁定的目标，供 HUD 与预测渲染读取；只由主线程写，因此不需要 volatile。
     */
    LivingEntity target;

    final TimerUtils placeTimer = new TimerUtils();
    final TimerUtils breakTimer = new TimerUtils();
    final TimerUtils snapshotTimer = new TimerUtils();
    final TimerUtils explosionSampleTimer = new TimerUtils();

    /** key 为方块位置，value 为该位置“视为自放”的截止时间。 */
    final Map<Long, Long> placedPosMap = new HashMap<>();
    /** key 为实体 id，value 为水晶的生成时间。 */
    final Map<Integer, Long> crystalSpawnMap = new HashMap<>();
    /** key 为实体 id，value 为已攻击标记的截止时间。 */
    final Map<Integer, Long> attackedCrystalMap = new HashMap<>();
    /** key 为方块位置，value 为已攻击标记的截止时间。 */
    final Map<Long, Long> attackedPosMap = new HashMap<>();

    long lastSwapTime;
    long lastActiveTime;

    /** 爆炸计数采样窗口，用于 HUD 的爆炸速率显示。 */
    final Deque<Integer> explosionSamples = new ArrayDeque<>();
    int explosionsThisWindow;

    void resetForEnable() {
        placedPosMap.clear();
        crystalSpawnMap.clear();
        attackedCrystalMap.clear();
        attackedPosMap.clear();
        explosionSamples.clear();
        explosionsThisWindow = 0;
        lastSwapTime = 0L;
        lastActiveTime = 0L;
        target = null;
        pendingSnapshot = null;
        latestSnapshot = null;
        asyncResult = AsyncResult.EMPTY;
        cachedRotationPlaceInfo = null;
        cachedPlaceInfo = null;
        cachedRotationBreakPlan = null;
        cachedBreakPlan = null;
    }

    void resetForDisable() {
        placedPosMap.clear();
        crystalSpawnMap.clear();
        attackedCrystalMap.clear();
        attackedPosMap.clear();
        explosionSamples.clear();
        explosionsThisWindow = 0;
        pendingSnapshot = null;
        latestSnapshot = null;
        asyncResult = AsyncResult.EMPTY;
        cachedRotationPlaceInfo = null;
        cachedPlaceInfo = null;
        cachedRotationBreakPlan = null;
        cachedBreakPlan = null;
        target = null;
    }

    /**
     * 清理过期的时间戳标记，避免集合在长时间启用后持续增长。
     */
    void updateTimeouts() {
        long current = System.currentTimeMillis();
        placedPosMap.values().removeIf(time -> time < current);
        crystalSpawnMap.values().removeIf(time -> time + 5000L < current);
        attackedCrystalMap.values().removeIf(time -> time < current);
        attackedPosMap.values().removeIf(time -> time < current);
    }

    double explosionSpeed() {
        if (explosionSamples.isEmpty()) {
            return 0.0;
        }

        int total = 0;
        for (int value : explosionSamples) {
            total += value;
        }
        return total / (double) explosionSamples.size() * 4.0;
    }
}
