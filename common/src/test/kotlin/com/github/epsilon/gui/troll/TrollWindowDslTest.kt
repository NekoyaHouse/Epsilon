package com.github.epsilon.gui.troll

import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.state.LuminSceneRoot
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrollWindowDslTest {
    @Test
    fun windowPositionsLocalContentAndPreservesScreenPointerCoordinates() {
        val window = ProbeWindow()
        LuminSceneRoot().use { root ->
            render(root, window)
            assertEquals(UiRect(100f, 60f, 180f, 120f), window.contentOrigin)
            assertEquals(115 to 70, window.screenMouse)
            assertTrue(root.input.mouseClicked(mouse(115.0, 70.0), false))
            assertEquals(115.0 to 70.0, window.pressedAt)
            assertTrue(root.input.mouseDragged(mouse(330.0, 240.0), 215.0, 170.0))
            assertEquals(330.0 to 240.0, window.draggedAt)
            assertTrue(root.input.mouseReleased(mouse(330.0, 240.0)))
            assertTrue(window.released)
        }
    }

    @Test
    fun openingTranslationAndRevealClipShareTheInputTransform() {
        val window = ProbeWindow()
        LuminSceneRoot().use { root ->
            render(root, window, offsetY = 40f, revealHeight = 30f)
            assertFalse(root.input.mouseClicked(mouse(115.0, 70.0), false))
            assertFalse(root.input.mouseClicked(mouse(115.0, 140.0), false))
            assertTrue(root.input.mouseClicked(mouse(115.0, 110.0), false))
            assertEquals(115.0 to 70.0, window.pressedAt)
        }
    }

    @Test
    fun movingAndResizingWindowUpdatesContentAndHitBounds() {
        val window = ProbeWindow()
        LuminSceneRoot().use { root ->
            render(root, window)
            window.bounds = UiRect(300f, 200f, 240f, 160f)
            render(root, window)
            assertEquals(window.bounds, window.contentOrigin)
            assertFalse(root.input.mouseClicked(mouse(115.0, 70.0), false))
            assertTrue(root.input.mouseClicked(mouse(530.0, 350.0), false))
            assertEquals(530.0 to 350.0, window.pressedAt)
        }
    }

    @Test
    fun hoverUpdatesOnTheFirstFrameAfterWindowWasBlocked() {
        val window = ProbeWindow()
        window.bounds = UiRect(300f, 200f, 180f, 120f)
        LuminSceneRoot().use { root ->
            repeat(2) {
                render(root, window, mouseX = Int.MIN_VALUE, mouseY = Int.MIN_VALUE)
                assertFalse(window.hovered)
                render(root, window, mouseX = 315, mouseY = 210)
                assertTrue(window.hovered)
                render(root, window, mouseX = 500, mouseY = 350)
                assertFalse(window.hovered)
            }
        }
    }

    @Test
    fun nestedRowHoverTracksPointerAndRetainsIdleContentAtFractionalPositions() {
        val window = ProbeWindow()
        window.bounds = UiRect(300.5f, 200.5f, 180f, 120f)
        LuminSceneRoot().use { root ->
            render(root, window, mouseX = Int.MIN_VALUE, mouseY = Int.MIN_VALUE)
            assertFalse(window.rowHovered)
            render(root, window, mouseX = 320, mouseY = 245)
            assertTrue(window.rowHovered)
            val builds = window.rowBuilds
            render(root, window, mouseX = 325, mouseY = 245)
            assertTrue(window.rowHovered)
            assertEquals(builds, window.rowBuilds)
            render(root, window, mouseX = 410, mouseY = 245)
            assertFalse(window.rowHovered)
            render(root, window, mouseX = 310, mouseY = 245)
            assertTrue(window.rowHovered)
        }
    }

    @Test
    fun hoverUsesOpeningTranslationAfterBlockedWindowResumes() {
        val window = ProbeWindow()
        window.bounds = UiRect(300f, 200f, 180f, 120f)
        LuminSceneRoot().use { root ->
            render(root, window, offsetY = 40f, mouseX = Int.MIN_VALUE, mouseY = Int.MIN_VALUE)
            render(root, window, offsetY = 40f, mouseX = 315, mouseY = 330)
            assertTrue(window.hovered)
            render(root, window, offsetY = 40f, mouseX = 315, mouseY = 220)
            assertFalse(window.hovered)
        }
    }

    private fun render(
        root: LuminSceneRoot, window: ProbeWindow, offsetY: Float = 0f, revealHeight: Float = window.bounds.height,
        mouseX: Int = 115, mouseY: Int = 70,
    ) {
        root.beginFrame()
        try {
            root.defaultView.render(mouseX, mouseY) {
                val screen = UiRect(0f, 0f, 800f, 600f)
                val entry = root.nextEntry(screen, window)
                entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, screen), block = {
                    Transform({ Render2DTransform.translation(0f, offsetY) }) {
                        Clip(window.bounds.x, window.bounds.y, window.bounds.width, revealHeight) {
                            LuminUIGroup("content") { window.drawInScope(this, mouseX, mouseY) }
                        }
                    }
                })
                entry.emit(UiTree.Scope())
            }
            root.endFrame()
        } finally {
            root.abortFrame()
        }
    }

    private fun mouse(x: Double, y: Double) = MouseButtonEvent(x, y, MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0))

    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }

    private class ProbeWindow : TrollWindow() {
        var contentOrigin: UiRect? = null
        var screenMouse: Pair<Int, Int>? = null
        var pressedAt: Pair<Double, Double>? = null
        var draggedAt: Pair<Double, Double>? = null
        var released = false
        var hovered = false
        var rowHovered = false
        var rowBuilds = 0

        init {
            bounds = UiRect(100f, 60f, 180f, 120f)
        }

        override fun layout(metrics: UiTextMetrics, screenWidth: Float, screenHeight: Float) = Unit

        override fun UiScope.draw(mouseX: Int, mouseY: Int) {
            contentOrigin = origin
            screenMouse = mouseX to mouseY
            hovered = isHovered(mouseX, mouseY)
            Fill(Color.WHITE)
            LuminUIItem("row") {
                Positioned(8.5f, 35.5f, 80f, 24f) {
                    rowHovered = isHovered(mouseX, mouseY)
                    rowBuilds++
                }
            }
        }

        override fun mouseClicked(event: MouseButtonEvent): Boolean {
            pressedAt = event.x() to event.y()
            return true
        }

        override fun mouseDragged(event: MouseButtonEvent): Boolean {
            draggedAt = event.x() to event.y()
            return true
        }

        override fun mouseReleased(event: MouseButtonEvent) {
            released = true
        }
    }
}
