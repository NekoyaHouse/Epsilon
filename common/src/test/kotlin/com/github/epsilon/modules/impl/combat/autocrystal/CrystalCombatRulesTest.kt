package com.github.epsilon.modules.impl.combat.autocrystal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrystalCombatRulesTest {
    private val rules = CrystalCombatRules(4.0, 12.0, 1.2, true, false, false, 4.0, 2.0, true, 5, 1.5, false, false, 0)
    private val armored = CrystalCombatRules.Target(20.0, 100.0, 100.0, false, false)

    @Test
    fun lethalMultiplierAddsOneAndLowersThresholdToOnePointFive() {
        assertFalse(rules.lethal(8.19, armored))
        assertTrue(rules.lethal(8.2, armored))
        assertEquals(1.5, rules.minimum(armored, 8.2))
        assertEquals(4.0, rules.minimum(armored, 8.19))
    }

    @Test
    fun forceDamagePrecedesArmorUnlessLethalAndArmorBoundaryIsStrict() {
        assertEquals(4.0, rules.minimum(armored.copy(minArmor = 5.0), 2.0))
        assertEquals(1.5, rules.minimum(armored.copy(minArmor = 4.99), 2.0))
        val forced = rules.copy(force = true, forceDamage = 3.0, lethalMultiplier = 0.0)
        val low = armored.copy(health = 4.0, minArmor = 1.0)
        assertEquals(3.0, forced.minimum(low, 2.0))
        assertEquals(1.5, forced.minimum(low, 4.5))
    }

    @Test
    fun safetyAndOverrideFollowReferenceBranches() {
        assertTrue(rules.unsafe(19.5, 20.0, false))
        assertFalse(rules.unsafe(12.0, 20.0, false))
        assertFalse(rules.unsafe(100.0, 20.0, true))
        assertFalse(rules.accepts(10.0, 19.5, true, armored))
        // 原源码的 Override 还会绕过伤害比例；并非只在致死时放宽最大自伤。
        assertTrue(rules.copy(safetyOverride = true).accepts(2.0, 19.5, true, armored))
        val forced = rules.copy(safetyOverride = true, force = true)
        val low = armored.copy(health = 4.0, minArmor = 1.0, maxArmor = 1.0)
        assertFalse(forced.accepts(4.49, 19.5, true, low))
        assertTrue(forced.accepts(4.5, 19.5, true, low))
        assertTrue(forced.accepts(2.0, 19.5, true, low.copy(maxArmor = 10.0)))
    }

    @Test
    fun lethalTickTotemAndSurroundKeepTheirIndependentThresholds() {
        assertFalse(rules.copy(lethalDamageTick = true, attackElapsed = 500).lethal(1.5, armored))
        assertTrue(rules.copy(lethalDamageTick = true, attackElapsed = 501).lethal(1.5, armored))
        assertTrue(rules.copy(lethalTotem = true, lethalMultiplier = 0.0).lethal(1.6, armored.copy(health = 2.0, recentTotem = true)))
        assertFalse(rules.accepts(1.0, 2.0, false, armored))
        assertTrue(rules.accepts(1.0, 2.0, false, armored, antiSurround = true))
        assertFalse(rules.accepts(1.0, 20.0, true, armored, antiSurround = true))
    }
}
