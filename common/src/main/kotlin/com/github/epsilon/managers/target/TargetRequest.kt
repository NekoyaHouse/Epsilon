package com.github.epsilon.managers.target

import net.minecraft.world.entity.LivingEntity
import java.util.function.Predicate

/**
 * 目标筛选请求。原 Java record 在紧凑构造器中做范围钳制，因此这里用普通 class 保留钳制语义，
 * 并手工实现 record 的 equals/hashCode/toString。
 */
class TargetRequest(
    range: Double,
    fov: Float,
    val player: Boolean,
    val mob: Boolean,
    val animal: Boolean,
    val villager: Boolean,
    val ambient: Boolean,
    val water: Boolean,
    val others: Boolean,
    val invisible: Boolean,
    val allowFriends: Boolean,
    extraFilter: Predicate<LivingEntity>?,
    maxTargets: Int,
) {

    val range: Double = if (range < 0.0) 0.0 else range
    val fov: Float = when {
        fov < 0.0f -> 0.0f
        fov > 360.0f -> 360.0f
        else -> fov
    }
    val extraFilter: Predicate<LivingEntity> = extraFilter ?: Predicate { true }
    val maxTargets: Int = if (maxTargets < 1) 1 else maxTargets

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TargetRequest) return false
        return range == other.range &&
                fov == other.fov &&
                player == other.player &&
                mob == other.mob &&
                animal == other.animal &&
                villager == other.villager &&
                ambient == other.ambient &&
                water == other.water &&
                others == other.others &&
                invisible == other.invisible &&
                allowFriends == other.allowFriends &&
                extraFilter == other.extraFilter &&
                maxTargets == other.maxTargets
    }

    override fun hashCode(): Int {
        var result = range.hashCode()
        result = 31 * result + fov.hashCode()
        result = 31 * result + player.hashCode()
        result = 31 * result + mob.hashCode()
        result = 31 * result + animal.hashCode()
        result = 31 * result + villager.hashCode()
        result = 31 * result + ambient.hashCode()
        result = 31 * result + water.hashCode()
        result = 31 * result + others.hashCode()
        result = 31 * result + invisible.hashCode()
        result = 31 * result + allowFriends.hashCode()
        result = 31 * result + extraFilter.hashCode()
        result = 31 * result + maxTargets
        return result
    }

    override fun toString(): String {
        return "TargetRequest[range=$range, fov=$fov, player=$player, mob=$mob, animal=$animal, " +
                "villager=$villager, ambient=$ambient, water=$water, others=$others, invisible=$invisible, " +
                "allowFriends=$allowFriends, extraFilter=$extraFilter, maxTargets=$maxTargets]"
    }

    companion object {

        fun of(
            range: Double, fov: Float,
            player: Boolean, mob: Boolean, animal: Boolean, villager: Boolean, ambient: Boolean,
            water: Boolean, others: Boolean, invisible: Boolean, maxTargets: Int,
        ): TargetRequest {
            return TargetRequest(
                range, fov, player, mob, animal, villager, ambient, water, others, invisible, false,
                Predicate { true }, maxTargets
            )
        }

        fun of(
            range: Double, fov: Float,
            player: Boolean, mob: Boolean, animal: Boolean, villager: Boolean, ambient: Boolean,
            water: Boolean, others: Boolean, invisible: Boolean,
            extraFilter: Predicate<LivingEntity>, maxTargets: Int,
        ): TargetRequest {
            return TargetRequest(
                range, fov, player, mob, animal, villager, ambient, water, others, invisible, false,
                extraFilter, maxTargets
            )
        }

        fun of(
            range: Double, fov: Float,
            player: Boolean, mob: Boolean, animal: Boolean, villager: Boolean, ambient: Boolean,
            water: Boolean, others: Boolean, invisible: Boolean, allowFriends: Boolean,
            extraFilter: Predicate<LivingEntity>, maxTargets: Int,
        ): TargetRequest {
            return TargetRequest(
                range, fov, player, mob, animal, villager, ambient, water, others, invisible, allowFriends,
                extraFilter, maxTargets
            )
        }

    }

}
