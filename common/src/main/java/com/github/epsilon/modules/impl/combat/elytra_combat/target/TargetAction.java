package com.github.epsilon.modules.impl.combat.elytra_combat.target;

/**
 * 根据目标最近运动轨迹推断出的战术状态。
 */
public enum TargetAction {
    /** 最近 20 tick 基本没有位移。 */
    AFK,
    /** 相邻采样位移都小于 0.75 格。 */
    SLOW_SPEED,
    /** 运动方向朝向本地玩家。 */
    TOWARDS,
    /** 运动方向远离本地玩家。 */
    ESCAPING,
    /** 最近转向角度达到 60 度，视为绕圈。 */
    CIRCLING
}
