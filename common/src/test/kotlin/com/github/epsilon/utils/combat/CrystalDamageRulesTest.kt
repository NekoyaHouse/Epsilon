package com.github.epsilon.utils.combat

import kotlin.test.Test
import kotlin.test.assertEquals

class CrystalDamageRulesTest {
    @Test
    fun explosionKeepsFractionalDamageAndHonorsRadius() {
        assertEquals(85.0f, CrystalDamageRules.rawDamage(0.0, 1.0))
        assertEquals(32.5f, CrystalDamageRules.rawDamage(6.0, 1.0))
        assertEquals(1.0f, CrystalDamageRules.rawDamage(12.0, 1.0))
        assertEquals(0.0f, CrystalDamageRules.rawDamage(12.001, 1.0))
        // 原版零暴露度仍保留基础的 1 点伤害，不能当作绝对安全。
        assertEquals(1.0f, CrystalDamageRules.rawDamage(6.0, 0.0))
        assertEquals(14.125f, CrystalDamageRules.rawDamage(6.0, 0.5))
    }

    @Test
    fun playerDifficultyScalingMatchesVanilla() {
        assertEquals(0.0f, CrystalDamageRules.scaleForDifficulty(20.0f, 0))
        assertEquals(11.0f, CrystalDamageRules.scaleForDifficulty(20.0f, 1))
        assertEquals(1.0f, CrystalDamageRules.scaleForDifficulty(1.0f, 1))
        assertEquals(20.0f, CrystalDamageRules.scaleForDifficulty(20.0f, 2))
        assertEquals(30.0f, CrystalDamageRules.scaleForDifficulty(20.0f, 3))
    }

    @Test
    fun armorResistanceAndProtectionApplyInOrder() {
        assertEquals(20.0f, CrystalDamageRules.reduceDamage(20.0f, 0.0f, 0.0f, -1, 0))
        assertEquals(8.0f, CrystalDamageRules.reduceDamage(20.0f, 20.0f, 8.0f, -1, 0), 0.00001f)
        assertEquals(1.28f, CrystalDamageRules.reduceDamage(20.0f, 20.0f, 8.0f, 0, 20), 0.00001f)
        assertEquals(0.0f, CrystalDamageRules.reduceDamage(85.0f, 20.0f, 12.0f, 4, 0))
        assertEquals(CrystalDamageRules.reduceDamage(20.0f, 0.0f, 0.0f, -1, 20),
            CrystalDamageRules.reduceDamage(20.0f, 0.0f, 0.0f, -1, 100))
    }

}
