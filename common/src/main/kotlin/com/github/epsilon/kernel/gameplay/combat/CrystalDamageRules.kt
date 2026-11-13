package com.github.epsilon.kernel.gameplay.combat

internal object CrystalDamageRules {
    fun rawDamage(distance: Double, exposure: Double): Float {
        if (!distance.isFinite() || !exposure.isFinite() || distance > 12.0) return 0.0f
        val impact = (1.0 - distance.coerceAtLeast(0.0) / 12.0) * exposure.coerceIn(0.0, 1.0)
        // ExplosionDamageCalculator 不再将结果截断为整数。
        return ((impact * impact + impact) * 42.0 + 1.0).toFloat()
    }

    fun scaleForDifficulty(damage: Float, difficulty: Int): Float = when (difficulty) {
        0 -> 0.0f
        1 -> minOf(damage * 0.5f + 1.0f, damage)
        3 -> damage * 1.5f
        else -> damage
    }

    fun reduceDamage(damage: Float, armor: Float, toughness: Float, resistance: Int, protection: Int): Float {
        val effectiveArmor = maxOf(armor * 0.2f, minOf(armor - damage / (2.0f + toughness / 4.0f), 20.0f))
        var reduced = damage * (1.0f - effectiveArmor / 25.0f)
        if (resistance >= 0) reduced *= (25 - (resistance + 1) * 5).coerceAtLeast(0) / 25.0f
        return (reduced * (1.0f - protection.coerceIn(0, 20) / 25.0f)).coerceAtLeast(0.0f)
    }

}
