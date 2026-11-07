package com.github.epsilon.modules.impl.combat.crystalaura

internal data class CrystalCombatRules(
    val minimum: Double, val maximumSelf: Double, val ratio: Double, val safety: Boolean,
    val safetyOverride: Boolean, val force: Boolean, val forceHealth: Double, val forceDamage: Double,
    val armorBreaker: Boolean, val armorScale: Int, val lethalMultiplier: Double,
    val lethalDamageTick: Boolean, val lethalTotem: Boolean, val attackElapsed: Long,
) {
    data class Target(
        val health: Double,
        val minArmor: Double,
        val maxArmor: Double,
        val recentTotem: Boolean,
        val nearShulker: Boolean
    )

    fun unsafe(damage: Double, health: Double, creative: Boolean): Boolean = !creative &&
            ((safety && damage >= health - 0.5) || damage > maximumSelf)

    fun accepts(damage: Double, self: Double, unsafe: Boolean, target: Target, antiSurround: Boolean = false): Boolean {
        if (!damage.isFinite() || !self.isFinite()) return false
        if (unsafe && !safetyOverride) return false
        val forceReject = force && target.health <= forceHealth &&
                !(armorBreaker && target.maxArmor > armorScale) && safetyOverride && unsafe && damage < target.health + 0.5
        if (forceReject) return false
        return antiSurround || safetyOverride || !(damage / self < ratio)
    }

    fun lethal(damage: Double, target: Target): Boolean =
        (lethalDamageTick && attackElapsed > 500L) ||
                (lethalTotem && target.health <= 2.0 && target.health - damage < 0.5 && target.recentTotem) ||
                damage * (1.0 + lethalMultiplier) >= target.health + 0.5 || target.nearShulker

    fun minimum(target: Target, damage: Double): Double = when {
        lethal(damage, target) -> 1.5
        force && target.health <= forceHealth -> forceDamage
        armorBreaker && target.minArmor < armorScale -> 1.5
        else -> minimum
    }
}
