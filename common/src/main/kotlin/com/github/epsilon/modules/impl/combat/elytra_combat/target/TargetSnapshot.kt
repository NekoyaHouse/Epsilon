package com.github.epsilon.modules.impl.combat.elytra_combat.target

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * 每 tick 生成的不可变目标快照。
 *
 * [predictedPosition] 由 [TargetMotionTracker] 计算；行为层只读快照，不再直接访问轨迹历史，
 * 避免网络线程与客户端 tick 状态交叉。
 */
data class TargetSnapshot(
    val entity: LivingEntity,
    val position: Vec3,
    val velocity: Vec3,
    val predictedPosition: Vec3,
    val action: TargetAction,
    val onGround: Boolean,
    val supported: Boolean,
    val usingSpear: Boolean,
)
