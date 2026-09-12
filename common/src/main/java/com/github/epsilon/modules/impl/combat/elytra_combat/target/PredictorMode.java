package com.github.epsilon.modules.impl.combat.elytra_combat.target;

/**
 * 目标轨迹外推模型。
 */
public enum PredictorMode {
    /** 对历史样本做线性回归。 */
    Linear,
    /** 拟合 x = a*t^2 + b*t + c，样本不足时退化为线性。 */
    Quadratic,
    /** 对相邻速度差做指数加权平均后线性外推。 */
    NV
}
