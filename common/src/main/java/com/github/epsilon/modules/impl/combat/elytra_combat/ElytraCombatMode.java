package com.github.epsilon.modules.impl.combat.elytra_combat;

/**
 * ElytraCombat 的显式模式；模式之间不会自动切换。
 */
public enum ElytraCombatMode {
    /** 直接追击目标预测位置。 */
    Follow,
    /** 拉升后进行重锤空袭或地面落点攻击。 */
    Mace,
    /** 使用长矛 kinetic 蓄力进行近身冲锋。 */
    Spear
}
