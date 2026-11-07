package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.graphics.schedulers.render2d.Render2DProperties
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiPhaseValue
import com.github.epsilon.gui.lib.render.UiRenderNode
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.kernel.state.StateNotifications
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import java.awt.Color
import kotlin.test.*

class UiRenderingPhaseTest {
    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }
    private val root = LuminSceneRoot()
    private val view = LuminView()
    private val bounds = UiRect(10f, 20f, 100f, 100f)
    private lateinit var entry: LuminComposition
    private fun frame(block: UiScope.() -> Unit) {
        root.beginFrame()
        try {
            view.render(15, 25) {
                entry = root.nextEntry(bounds, "root")
                entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), block)
                entry.emit(UiTree.Scope())
            }
            root.endFrame()
        } catch (failure: Throwable) { root.abortFrame(); throw failure }
    }
    @AfterTest fun close() { root.close(); view.close(); StateNotifications.drain() }

    @Test fun paintAndTransformDoNotRecomposeOrReplaceGeometry() {
        val color = mutableStateOf(Color.WHITE)
        val position = mutableStateOf(0f)
        var builds = 0
        var clicks = 0
        val draw: UiScope.() -> Unit = {
            builds++
            Transform({ Render2DTransform.translation(position.value, 0f) }) {
                Paint({ color.value }) { Rect(0f, 0f, 10f, 10f, Color.WHITE) }
                Interaction("hit", UiRect(0f, 0f, 10f, 10f)) {
                    onClick { assertEquals(5.0, it.localX); clicks++; true }
                }
            }
        }
        frame(draw)
        val original = entry.renderNode.nodes
        val transform = original.single() as UiTree.PropertyNode
        val geometry = (transform.children.first() as UiTree.PropertyNode).children
        color.value = Color.RED
        position.value = 30f
        frame(draw)
        assertEquals(30f, transform.value.get().properties.transform.x)
        assertEquals(1, builds)
        assertSame(original, entry.renderNode.nodes)
        assertSame(geometry, (transform.children.first() as UiTree.PropertyNode).children)
        assertFalse(root.input.mouseClicked(MouseButtonEvent(15.0, 25.0, MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false))
        assertTrue(root.input.mouseClicked(MouseButtonEvent(45.0, 25.0, MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false))
        assertEquals(1, clicks)
    }

    @Test fun geometryReadsInvalidateOnlyTheirOwnPhase() {
        val width = mutableStateOf(5f)
        var builds = 0
        var geometryBuilds = 0
        val draw: UiScope.() -> Unit = {
            builds++
            CacheDraw { geometryBuilds++; Rect(0f, 0f, width.value, 10f, Color.WHITE) }
        }
        frame(draw)
        val node = entry.renderNode.nodes.single() as UiTree.GeometryNode
        val first = node.value.get()
        assertSame(first, node.value.get())
        width.value = 8f
        StateNotifications.drain()
        val next = node.value.get()
        assertEquals(8f, (next.single() as UiTree.RectNode).width)
        frame(draw)
        assertEquals(1, builds)
        assertEquals(2, geometryBuilds)
    }

    @Test fun failedPhaseRetainsPreviousValueAndCanRetry() {
        val failure = mutableStateOf(false)
        var disposed = 0
        val phase = UiPhaseValue({ check(!failure.value); Any() }, { disposed++ })
        try {
            val first = phase.get()
            failure.value = true; StateNotifications.drain()
            assertFailsWith<IllegalStateException> { phase.get() }
            assertEquals(0, disposed)
            failure.value = false; StateNotifications.drain()
            assertNotSame(first, phase.get())
            assertEquals(1, disposed)
        } finally { phase.close(); phase.close() }
        assertEquals(2, disposed)
    }

    @Test fun removedNodesAndFailedFramesReleasePhaseSubscriptions() {
        val source = mutableStateOf(Color.WHITE)
        var reads = 0
        val render = UiRenderNode()
        repeat(100) {
            val phase = UiPhaseValue({ reads++; UiTree.PropertyValue(
                Render2DProperties.DEFAULT, null).also { source.value } })
            val nodes = listOf(UiTree.PropertyNode(bounds, phase, emptyList()))
            render.commit(nodes, bounds)
            phase.get()
            render.commit(emptyList(), bounds)
            source.value = if (it % 2 == 0) Color.RED else Color.WHITE
            StateNotifications.drain()
        }
        render.close(); render.close()
        assertEquals(100, reads)
        val replacement = UiRenderNode()
        assertNotEquals(render.generation, replacement.generation)
        replacement.close()
    }
}
