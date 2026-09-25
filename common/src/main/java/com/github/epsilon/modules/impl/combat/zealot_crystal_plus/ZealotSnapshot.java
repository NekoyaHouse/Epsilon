package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotDamage.DamageReductionData;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.BreakMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.DamagePriority;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PacketPlaceMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceBypass;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotDamage.ResistantBlockCache;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * ZealotCrystalPlus 的不可变数据契约。
 * <p>
 * OBSERVE 阶段在主线程构建 {@link SnapshotData} 并交给 worker 线程；worker 只读取这些不可变快照，
 * 不触碰世界状态。主线程再通过 {@link AsyncResult} 取回决策结果，因此这里所有类型都必须是不可变的。
 */
final class ZealotSnapshot {

    private ZealotSnapshot() {
    }

    /**
     * 单帧的决策设置快照：worker 线程不得读取 Setting，只能读取这里的值。
     */
    record SettingsSnapshot(
            int globalDelayNanos,
            float noSuicide,
            float placeMaxSelfDamage,
            float breakMaxSelfDamage,
            float placeMinDamage,
            float breakMinDamage,
            float placeBalance,
            float breakBalance,
            float forcePlaceMinDamage,
            float forcePlaceBalance,
            float forcePlaceHealth,
            float forcePlaceMotion,
            int forcePlaceArmorRate,
            boolean forcePlaceWhileSwording,
            boolean lethalOverride,
            float lethalThresholdAddition,
            float lethalMaxSelfDamage,
            float safeMaxTargetDamageReduction,
            float safeMinSelfDamageReduction,
            float collidingCrystalExtraSelfDamageThreshold,
            float placeRotationRange,
            float breakRotationRange,
            DamagePriority damagePriority,
            DamageUtils.ArmorEnchantmentMode armorMode,
            PlaceBypass placeSideBypass,
            PacketPlaceMode packetPlaceMode,
            BreakMode breakMode,
            BreakMode packetBreakMode
    ) {
    }

    record SelfSnapshot(
            Player player,
            Vec3 pos,
            Vec3 eyePos,
            AABB box,
            float totalHealth,
            boolean weaknessActive,
            boolean holdingTool,
            boolean swording,
            DamageReductionData reduction,
            Difficulty difficulty,
            Rot2f currentRotation
    ) {
    }

    record TargetSnapshot(
            LivingEntity entity,
            Vec3 pos,
            AABB box,
            Vec3 currentPos,
            Vec3 predictMotion,
            float totalHealth,
            boolean player,
            double speed,
            int minArmorRate,
            DamageReductionData reduction
    ) {
    }

    record CrystalSnapshot(
            EndCrystal entity,
            int id,
            Vec3 pos,
            AABB box,
            boolean breakable,
            boolean ownPlaced
    ) {
    }

    record SnapshotData(
            long id,
            SettingsSnapshot settings,
            SelfSnapshot self,
            List<TargetSnapshot> targets,
            List<BlockPos> placePositions,
            List<CrystalSnapshot> crystals,
            ResistantBlockCache resistantBlocks,
            long capturedAt
    ) {
        static final SnapshotData EMPTY = new SnapshotData(-1L, null, null, List.of(), List.of(), List.of(), ResistantBlockCache.EMPTY, 0L);
    }

    /**
     * worker 线程产出的决策结果集合，字段均为不可变引用。
     */
    record AsyncResult(
            long snapshotId,
            PlaceInfo rotationPlaceInfo,
            PlaceInfo placeInfo,
            BreakPlan rotationBreakPlan,
            BreakPlan breakPlan,
            TargetSnapshot primaryTarget,
            long calculationNanos
    ) {
        static final AsyncResult EMPTY = new AsyncResult(-1L, null, null, null, null, null, 0L);
    }

    record PlaceInfo(
            LivingEntity target,
            BlockPos blockPos,
            float selfDamage,
            float targetDamage,
            Direction side,
            Vec3 hitVec,
            Rot2f rotation
    ) {
    }

    record BreakPlan(
            EndCrystal crystal,
            int entityId,
            Vec3 pos,
            float selfDamage,
            float targetDamage
    ) {
    }

    /**
     * 放置候选的评分累加器；只在单次 evaluate 内使用，不跨线程。
     */
    static final class PlaceChoice {
        private LivingEntity target;
        private BlockPos blockPos;
        private float selfDamage = Float.MAX_VALUE;
        private float targetDamage = Float.NEGATIVE_INFINITY;

        void update(LivingEntity target, BlockPos blockPos, float selfDamage, float targetDamage) {
            this.target = target;
            this.blockPos = blockPos;
            this.selfDamage = selfDamage;
            this.targetDamage = targetDamage;
        }

        void clear() {
            target = null;
            blockPos = null;
            selfDamage = Float.MAX_VALUE;
            targetDamage = Float.NEGATIVE_INFINITY;
        }

        PlaceChoice takeValid() {
            return target != null && blockPos != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY ? this : null;
        }

        LivingEntity target() {
            return target;
        }

        BlockPos blockPos() {
            return blockPos;
        }

        float selfDamage() {
            return selfDamage;
        }

        float targetDamage() {
            return targetDamage;
        }
    }

    /**
     * 破坏候选的评分累加器；只在单次 evaluate 内使用，不跨线程。
     */
    static final class BreakChoice {
        private CrystalSnapshot crystal;
        private float selfDamage = Float.MAX_VALUE;
        private float targetDamage = Float.NEGATIVE_INFINITY;

        void update(CrystalSnapshot crystal, float selfDamage, float targetDamage) {
            this.crystal = crystal;
            this.selfDamage = selfDamage;
            this.targetDamage = targetDamage;
        }

        void clear() {
            crystal = null;
            selfDamage = Float.MAX_VALUE;
            targetDamage = Float.NEGATIVE_INFINITY;
        }

        BreakChoice takeValid() {
            return crystal != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY ? this : null;
        }

        CrystalSnapshot crystal() {
            return crystal;
        }

        float selfDamage() {
            return selfDamage;
        }

        float targetDamage() {
            return targetDamage;
        }
    }
}
