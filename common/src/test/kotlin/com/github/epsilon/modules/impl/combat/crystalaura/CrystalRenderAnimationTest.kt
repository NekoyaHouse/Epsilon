package com.github.epsilon.modules.impl.combat.crystalaura

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CrystalRenderAnimationTest {
    private val first = Vec3(0.5, 64.5, 0.5)
    private val second = Vec3(10.5, 64.5, 0.5)

    @Test
    fun repeatedTargetCompletesFadeAndKeepsBoxCentered() {
        val animation = CrystalRenderAnimation()
        assertNull(animation.sample(first, 0, 400, 200))
        val half = assertNotNull(animation.sample(first, 100, 400, 200))
        assertEquals(0.875, half.scale, 1e-9)
        assertEquals(first, half.center)
        assertEquals(first.x, half.box.center.x, 1e-9)
        assertEquals(half.scale, half.box.maxX - half.box.minX, 1e-9)
        assertEquals(1.0, assertNotNull(animation.sample(first, 200, 400, 200)).scale)
    }

    @Test
    fun repeatedDestinationDoesNotRestartMovement() {
        val animation = CrystalRenderAnimation()
        animation.sample(first, 0, 400, 0)
        animation.sample(second, 100, 400, 0)
        assertEquals(9.875, assertNotNull(animation.sample(second, 300, 400, 0)).center.x, 1e-9)
        assertEquals(second, assertNotNull(animation.sample(second, 500, 400, 0)).center)
    }

    @Test
    fun retargetsFromInterpolatedPositionWithoutNeedingAPreviousFrame() {
        val animation = CrystalRenderAnimation()
        animation.sample(first, 0, 400, 0)
        animation.sample(second, 100, 400, 0)
        val third = Vec3(20.5, 65.5, 0.5)
        val redirected = assertNotNull(animation.sample(third, 300, 400, 0))
        assertEquals(9.875, redirected.center.x, 1e-9)
        assertEquals(first.y, redirected.center.y, 1e-9)
        assertEquals(third, assertNotNull(animation.sample(third, 700, 400, 0)).center)
    }

    @Test
    fun disappearingTargetShrinksAndFadesUntilFullyHidden() {
        val animation = CrystalRenderAnimation()
        animation.sample(first, 0, 400, 200)
        animation.sample(first, 200, 400, 200)
        assertEquals(1.0, assertNotNull(animation.sample(null, 300, 400, 200)).scale)
        val fading = assertNotNull(animation.sample(null, 400, 400, 200))
        assertEquals(0.875, fading.scale, 1e-9)
        assertEquals(first, fading.center)
        assertNull(animation.sample(null, 500, 400, 200))
        assertNull(animation.sample(null, 1000, 400, 200))
    }

    @Test
    fun reversingFadePreservesCurrentOpacityAndPosition() {
        val animation = CrystalRenderAnimation()
        animation.sample(first, 0, 400, 200)
        animation.sample(first, 200, 400, 200)
        animation.sample(null, 300, 400, 200)
        val before = assertNotNull(animation.sample(null, 400, 400, 200))
        val after = assertNotNull(animation.sample(second, 400, 400, 200))
        assertEquals(before, after)
        assertEquals(1.0, assertNotNull(animation.sample(second, 600, 400, 200)).scale)
    }

    @Test
    fun fullyHiddenTargetReappearsAtNewPosition() {
        val animation = CrystalRenderAnimation()
        animation.sample(first, 0, 400, 200)
        animation.sample(first, 200, 400, 200)
        animation.sample(null, 300, 400, 200)
        assertNull(animation.sample(second, 600, 400, 200))
        assertEquals(second, assertNotNull(animation.sample(second, 700, 400, 200)).center)
    }

    @Test
    fun zeroLengthsApplyImmediately() {
        val animation = CrystalRenderAnimation()
        assertEquals(1.0, assertNotNull(animation.sample(first, 0, 0, 0)).scale)
        assertEquals(second, assertNotNull(animation.sample(second, 0, 0, 0)).center)
        assertNull(animation.sample(null, 0, 0, 0))
    }

    @Test
    fun clearRemovesPositionAndFadeState() {
        val animation = CrystalRenderAnimation()
        animation.sample(first, 0, 400, 0)
        animation.sample(second, 100, 400, 0)
        animation.clear()
        assertNull(animation.sample(null, 200, 400, 200))
        assertNull(animation.sample(second, 300, 400, 200))
        assertEquals(second, assertNotNull(animation.sample(second, 400, 400, 200)).center)
    }
}
