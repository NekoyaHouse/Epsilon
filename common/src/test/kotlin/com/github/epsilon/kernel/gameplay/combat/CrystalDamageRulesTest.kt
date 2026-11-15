package com.github.epsilon.kernel.gameplay.combat

import kotlin.test.Test
import kotlin.test.assertEquals

class CrystalDamageRulesTest {
    @Test
    fun anchorUsesFiveBlockExplosionStrength() {
        assertEquals(71.0f, CrystalDamageRules.rawDamage(0.0, 1.0, 5.0))
        assertEquals(27.25f, CrystalDamageRules.rawDamage(5.0, 1.0, 5.0))
        assertEquals(11.9375f, CrystalDamageRules.rawDamage(5.0, 0.5, 5.0))
    }

    @Test
    fun anchorStopsDamagingBeyondTenBlocks() {
        assertEquals(1.0f, CrystalDamageRules.rawDamage(10.0, 1.0, 5.0))
        assertEquals(0.0f, CrystalDamageRules.rawDamage(10.01, 1.0, 5.0))
    }

    @Test
    fun defaultCrystalDamageKeepsSixBlockStrength() {
        assertEquals(85.0f, CrystalDamageRules.rawDamage(0.0, 1.0))
        assertEquals(32.5f, CrystalDamageRules.rawDamage(6.0, 1.0))
        assertEquals(1.0f, CrystalDamageRules.rawDamage(12.0, 1.0))
        assertEquals(0.0f, CrystalDamageRules.rawDamage(12.01, 1.0))
    }
}
