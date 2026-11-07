package com.github.epsilon.modules.impl.combat.crystalaura

import com.github.epsilon.utils.combat.CrystalDamageUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

internal data class CrystalTarget(val snapshot: CrystalDamageUtils.Snapshot, val rules: CrystalCombatRules.Target)

internal data class CrystalCandidate(
    val pos: BlockPos, val target: CrystalTarget, val damage: Double, val selfDamage: Double,
    val crystalId: Int? = null, val supportHit: BlockHitResult? = null, val antiSurround: Boolean = false,
)

internal data class CrystalPlacePoint(val pos: BlockPos, val supportHit: BlockHitResult?, val surroundTargets: Set<Int>)

/** 完整扫描，不截断目标数或位置数；输入在客户端线程生成，后台不读取实体或世界。 */
internal class CrystalPlacement(
    private val points: List<CrystalPlacePoint>, private val targets: List<CrystalTarget>,
    private val self: CrystalDamageUtils.Snapshot, private val world: CrystalDamageUtils.WorldSnapshot,
    private val rules: CrystalCombatRules, private val shieldBypass: Boolean,
    private val maxOverride: Double, private val antiSurround: Boolean,
) {
    fun calculate(): CrystalCandidate? {
        var best: CrystalCandidate? = null
        var surround: CrystalCandidate? = null
        for (point in points) {
            if (Thread.currentThread().isInterrupted) return null
            val pos = point.pos
            val center = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
            val local = CrystalDamageUtils.calculate(self, center, world, pos)
            val unsafe = rules.unsafe(local, self.health, self.creative)
            if (unsafe && !rules.safetyOverride) continue
            for (target in targets) {
                if (Vec3.atLowerCornerOf(pos).distanceToSqr(target.snapshot.position) > 144.0) continue
                if (shieldBypass && target.snapshot.blocksExplosion(center)) continue
                val damage = CrystalDamageUtils.calculate(target.snapshot, center, world, pos)
                val surrounding = target.snapshot.id in point.surroundTargets
                if (!rules.accepts(damage, local, unsafe, target.rules, surrounding)) continue
                val candidate = CrystalCandidate(
                    pos,
                    target,
                    damage,
                    local,
                    supportHit = point.supportHit,
                    antiSurround = surrounding
                )
                if (best == null || damage > best.damage ||
                    point.supportHit == null && best.supportHit != null && damage > maxOverride
                ) best = candidate
                if (point.supportHit == null && surrounding && (surround == null ||
                            Vec3.atCenterOf(pos).distanceToSqr(self.position) < Vec3.atCenterOf(surround.pos)
                        .distanceToSqr(self.position))
                ) surround = candidate
            }
        }
        return best?.takeIf { it.damage >= rules.minimum(it.target.rules, it.damage) }
            ?: surround.takeIf { antiSurround }
    }
}
