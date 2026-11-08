package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.component.SettingSectionRenderer
import com.github.epsilon.gui.dropdown.widget.SettingWidget
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.kernel.state.StateNotifications
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.impl.BoolSetting
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import java.awt.Color
import kotlin.test.*

class SettingGroupDrawingTest {
    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }
    private var time = 0L
    private val clock = LuminFrameClock { time }
    private val root = LuminSceneRoot(clock)
    private val bounds = UiRect(17f, 23f, 200f, 500f)

    @AfterTest fun close() { root.close(); StateNotifications.drain() }

    private fun setting(name: String, group: SettingGroup? = null) =
        BoolSetting(name, false, null, null).apply { groupRef = group }

    private fun renderer(settings: List<Setting<*>>) = SettingSectionRenderer.create(
        "batch", settings
    ) { setting ->
        object : SettingWidget<BoolSetting>(setting as BoolSetting) {
            override fun getHeight() = 16f
            override fun UiScope.draw(mouseX: Int, mouseY: Int) {
                Rect(0f, 0f, width, height, Color.MAGENTA)
                Text(setting.name, 2f, 3f, 0.5f, Color.WHITE)
            }
        }
    }

    private fun draw(renderer: SettingSectionRenderer): List<UiTree.UiNode> {
        root.beginFrame()
        return try {
            val entry = root.nextEntry(bounds, "groups")
            entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), block = {
                renderer.draw(this, -1, -1, 0f, 0f, width)
            })
            entry.resolve()
        } finally { root.endFrame() }
    }

    private fun visible(nodes: List<UiTree.UiNode>) = nodes.filter { it !is UiTree.InteractionNode }

    private class Buffer : GpuBuffer {
        override fun size() = 65536L
        override fun usage() = GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_INDEX
        override fun isClosed() = false
        override fun close() = Unit
        override fun map(offset: Long, length: Long, read: Boolean, write: Boolean): GpuBufferSlice.MappedView = error("无需 GPU")
    }
    private class View : GpuTextureView {
        override fun texture(): GpuTexture = error("无需 GPU")
        override fun baseMipLevel() = 0
        override fun mipLevels() = 1
        override fun getWidth(mipLevel: Int) = 1024
        override fun getHeight(mipLevel: Int) = 1024
        override fun close() = Unit
        override fun isClosed() = false
    }

    @Test fun eightCollapsedSiblingHeadersProduceTwoCompatibleRunsFromActualNodes() {
        val renderer = renderer(List(8) { setting("s-$it", SettingGroup("group-$it")) })
        val nodes = visible(draw(renderer))
        val texts = nodes.filterIsInstance<UiTree.TextNode>()
        assertEquals((0 until 8).flatMap { listOf("group-$it", "1") }, texts.map { it.text })
        val firstText = nodes.indexOfFirst { it is UiTree.TextNode }
        assertEquals(32, firstText) // 每组保留填充、描边、计数底板与箭头。
        assertTrue(nodes.drop(firstText).all { it is UiTree.TextNode })
        val page = Buffer()
        val shape = Render2DDrawPlan.Binding(LuminRenderPipelines.UI_SHAPE, page, page, IndexType.SHORT, null, emptyMap(), emptyMap())
        val text = shape.copy(pipeline = LuminRenderPipelines.TTF_FONT_AA, vertices = Buffer(),
            textures = mapOf("Sampler0" to Render2DDrawPlan.Texture(View(), null)))
        val packets = nodes.mapIndexed { index, node ->
            assertTrue(node is UiTree.RoundRectNode || node is UiTree.OutlineNode || node is UiTree.TriangleNode || node is UiTree.TextNode)
            Render2DDrawPlan.Packet(if (node is UiTree.TextNode) text else shape, Render2DDrawPlan.Range(6, 0, index * 4, index))
        }
        val plan = Render2DDrawPlan.build(packets)
        assertEquals(listOf(32, 16), plan.runs.map { it.ranges.size })
        // 复原旧组头的形状/文字交错顺序，验证真实节点重排带来的兼容批次数变化。
        val legacy = (0 until 8).flatMap { index ->
            val shapeStart = index * 4
            val textStart = firstText + index * 2
            listOf(packets[shapeStart], packets[shapeStart + 1], packets[textStart],
                packets[shapeStart + 2], packets[textStart + 1], packets[shapeStart + 3])
        }
        assertEquals(33, Render2DDrawPlan.build(legacy).runs.size)
        val stride = DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP
        texts.filter { it.text != "1" }.forEachIndexed { index, title ->
            assertEquals(bounds.x + DropdownTheme.SETTING_INDENT + DropdownTheme.SETTING_PADDING_X, title.x)
            assertEquals(bounds.y + index * stride + (DropdownTheme.GROUP_HEADER_HEIGHT - title.scale) * 0.5f, title.y, 0.0001f)
        }
    }

    @Test fun ordinaryWidgetRemainsBetweenSiblingGroupBatches() {
        val renderer = renderer(listOf(
            setting("a", SettingGroup("first")), setting("inline"), setting("b", SettingGroup("last"))
        ))
        val nodes = visible(draw(renderer))
        val widget = nodes.indexOfFirst { it is UiTree.RectNode && it.color == Color.MAGENTA }
        val firstTitle = nodes.indexOfFirst { it is UiTree.TextNode && it.text == "first" }
        val lastCard = nodes.indexOfLast { it is UiTree.OutlineNode }
        assertTrue(firstTitle < widget)
        assertTrue(widget < lastCard)
        assertEquals(listOf("first", "1", "inline", "last", "1"), nodes.filterIsInstance<UiTree.TextNode>().map { it.text })
    }

    @Test fun nestedHeadersBatchInsideTheAnimatedParentClipAndKeepAncestorOrder() {
        val parent = SettingGroup("parent")
        val renderer = renderer(listOf(
            setting("a", parent.child("child-a")), setting("b", parent.child("child-b")),
            setting("c", SettingGroup("sibling"))
        ))
        draw(renderer)
        parent.isCollapsed = false
        draw(renderer)
        time += 90_000_000L
        val nodes = visible(draw(renderer))
        val clip = nodes.filterIsInstance<UiTree.ScissorNode>().single()
        val childNodes = visible(clip.children)
        val firstText = childNodes.indexOfFirst { it is UiTree.TextNode }
        assertEquals(8, firstText)
        assertTrue(childNodes.drop(firstText).all { it is UiTree.TextNode })
        assertEquals(listOf("child-a", "1", "child-b", "1"), childNodes.filterIsInstance<UiTree.TextNode>().map { it.text })
        assertTrue(nodes.indexOfFirst { it is UiTree.TextNode && it.text == "parent" } < nodes.indexOf(clip))
        assertTrue(nodes.indexOf(clip) < nodes.indexOfLast { it is UiTree.OutlineNode })
        assertEquals(listOf("parent", "2", "sibling", "1"), nodes.filterIsInstance<UiTree.TextNode>().map { it.text })
        time += 200_000_000L
        assertTrue(draw(renderer).none { it is UiTree.ScissorNode })
    }
}
