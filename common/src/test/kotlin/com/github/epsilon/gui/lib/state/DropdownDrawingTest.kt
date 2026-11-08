package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.graphics.schedulers.render2d.Render2DProperties
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.component.ModuleButton
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiRenderNode
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.modules.Module
import java.awt.Color
import kotlin.math.roundToInt
import kotlin.test.*

class DropdownDrawingTest {
    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }
    private data class Draw(val layer: Int, val node: UiTree.UiNode, val color: Color?)

    private fun draw(buttons: List<ModuleButton>): List<Draw> {
        val tree = UiTree.Scope()
        for ((index, button) in buttons.withIndex()) {
            val bounds = UiRect(0f, index * DropdownTheme.MODULE_HEIGHT, DropdownTheme.PANEL_WIDTH, DropdownTheme.MODULE_HEIGHT)
            button.setPosition(bounds.x, bounds.y, bounds.width)
            tree.pushAbsolute(bounds) {
                with(button) { UiScope(tree, metrics, LayerOffset.Same, bounds).draw(-1, -1) }
            }
        }
        fun flatten(nodes: List<UiTree.UiNode>, layer: Int, paint: Render2DProperties = Render2DProperties.DEFAULT): List<Draw> = nodes.flatMap { node ->
            when (node) {
                is UiTree.LayerNode -> flatten(node.children, layer + node.layer, paint)
                is UiTree.PropertyNode -> flatten(node.children, layer, paint.then(node.value.get().properties))
                else -> {
                    val color = when (node) {
                        is UiTree.RectNode -> node.color
                        is UiTree.RoundRectNode -> node.color
                        is UiTree.OutlineNode -> node.color
                        is UiTree.TextNode -> node.color
                        else -> null
                    }
                    listOf(Draw(layer, node, color?.let {
                        Color(it.red / 255f * paint.red, it.green / 255f * paint.green,
                            it.blue / 255f * paint.blue, it.alpha / 255f * paint.alpha)
                    }))
                }
            }
        }
        val nodes = tree.snapshot().nodes
        return try { flatten(nodes, 0).sortedBy { it.layer } }
        finally { UiRenderNode.release(nodes) }
    }

    @Test fun keybindTextStaysLightAboveTheRowSurfaceThroughoutTheToggleAnimation() {
        val button = ModuleButton(Module("module", null))
        val field = button.javaClass.getDeclaredField("toggleAnim").apply { isAccessible = true }
        val toggle = field.get(button) as LuminAnimatable
        try {
            for (progress in listOf(0f, 0.5f, 1f)) {
                toggle.snapTo(progress)
                val label = draw(listOf(button)).map { it.node }.filterIsInstance<UiTree.TextNode>().single { it.text == "NONE" }
                assertEquals(DropdownTheme.keybindText(true), label.color)
            }
        } finally { toggle.close() }
    }

    @Test fun rowsDimButtonsBeforeDrawingNamesAndLabels() {
        val draws = draw(listOf(ModuleButton(Module("first", null)), ModuleButton(Module("second", null))))
        val backgrounds = draws.filter { it.node is UiTree.RectNode }
        val names = draws.filter { (it.node as? UiTree.TextNode)?.text in listOf("first", "second") }
        val buttons = draws.filter { it.node is UiTree.RoundRectNode || it.node is UiTree.OutlineNode }
        val labels = draws.filter { (it.node as? UiTree.TextNode)?.text == "NONE" }
        assertEquals(4, backgrounds.size)
        assertEquals(2, names.size)
        assertEquals(4, buttons.size)
        assertEquals(2, labels.size)
        assertTrue(buttons.last().layer < backgrounds.first().layer)
        assertTrue(backgrounds.last().layer < names.first().layer)
        assertTrue(backgrounds.last().layer < labels.first().layer)
        assertEquals(names.first().layer, labels.first().layer)
        // 下一面板的 background pass 起点比 content 高 10，行内分层不能越过该边界。
        assertTrue(draws.all { it.layer > -10 && it.layer < 10 })
    }

    @Test fun buttonFillAndOutlineKeepTheLegacyAlphaComposition() {
        val button = ModuleButton(Module("module", null))
        val toggle = button.javaClass.getDeclaredField("toggleAnim").apply { isAccessible = true }.get(button) as LuminAnimatable
        fun over(source: Color, destination: List<Double>): List<Double> {
            val alpha = source.alpha / 255.0
            return listOf(source.red, source.green, source.blue).zip(destination) { s, d -> s * alpha + d * (1 - alpha) }
        }
        try {
            for (progress in listOf(0f, 0.5f, 1f)) {
                toggle.snapTo(progress)
                val draws = draw(listOf(button))
                val fill = draws.single { it.node is UiTree.RoundRectNode }
                val outline = draws.single { it.node is UiTree.OutlineNode }
                val row = draws.first { it.node is UiTree.RectNode }
                val backdrop = MD3Theme.SURFACE_CONTAINER.let { listOf(it.red.toDouble(), it.green.toDouble(), it.blue.toDouble()) }
                for (edge in listOf(false, true)) {
                    // 旧版同层顺序为圆角填充、描边、矩形；采样不含字形的按钮内部和直边。
                    val legacy = over(checkNotNull(row.color),
                        if (edge) over(checkNotNull(outline.color), over(checkNotNull(fill.color), backdrop))
                        else over(checkNotNull(fill.color), backdrop))
                    val actual = draws.filter { it === fill || it === row || edge && it === outline }
                        .fold(backdrop) { color, draw -> over(checkNotNull(draw.color), color) }
                    legacy.zip(actual).forEach { (expected, result) -> assertEquals(expected, result, 0.00001) }
                    // 默认紫色主题的旧图按钮内部像素；避免仅核对顺序而漏掉 alpha 或配色变化。
                    if (!edge && progress == 0f) assertEquals(listOf(35, 32, 40), actual.map { it.roundToInt() })
                    if (!edge && progress == 1f) assertEquals(listOf(89, 65, 148), actual.map { it.roundToInt() })
                }
            }
        } finally { toggle.close() }
    }
}
