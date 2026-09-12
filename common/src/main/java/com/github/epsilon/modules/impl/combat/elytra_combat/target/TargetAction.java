package com.github.epsilon.modules.impl.combat.elytra_combat.target;

/**
 * 根据目标最近运动轨迹推断出的战术状态。
 */
public enum TargetAction {
    AFK,
    SLOW_SPEED,
    TOWARDS,
    ESCAPING,
    CIRCLING
}
