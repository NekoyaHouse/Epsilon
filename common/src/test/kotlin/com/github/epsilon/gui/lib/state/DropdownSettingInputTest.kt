package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.dropdown.widget.AbstractSliderWidget
import com.github.epsilon.gui.dropdown.widget.DoubleSliderWidget
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.dropdown.widget.IntSliderWidget
import com.github.epsilon.gui.dropdown.widget.StringWidget
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.kernel.state.StateNotifications
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.StringSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import kotlin.test.*

class DropdownSettingInputTest {
    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }
    private val root = LuminSceneRoot()
    private val view = LuminView()
    private val bounds = UiRect(0f, 0f, 200f, 80f)
    private fun pointer(x: Double, y: Double = 10.0) =
        MouseButtonEvent(x, y, MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0))

    @AfterTest fun close() { root.close(); view.close(); StateNotifications.drain() }

    private fun frame(draw: UiScope.() -> Unit) {
        root.beginFrame()
        try {
            view.render(10, 10) {
                val entry = root.nextEntry(bounds, "settings")
                entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), draw)
                entry.emit(UiTree.Scope())
            }
            root.endFrame()
        } catch (failure: Throwable) { root.abortFrame(); throw failure }
    }

    // 直接驱动数值预览与提交，避开 Screen 音效和 GPU 初始化；失焦使用真实控件实现。
    private fun preview(widget: Any, ratio: Float) {
        widget.javaClass.getDeclaredMethod("updateValueFromRatio", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(widget, ratio)
    }
    private fun commit(widget: Any) {
        AbstractSliderWidget::class.java.getDeclaredMethod("commitPendingValue")
            .apply { isAccessible = true }.invoke(widget)
    }
    private fun buffer(widget: Any, value: String) {
        val field = AbstractSliderWidget::class.java.getDeclaredField("inputField").apply { isAccessible = true }
        (field.get(widget) as DropdownTextField).text = value
    }

    @Test fun releasedSliderKeepsItsValueWhenAnotherSettingStartsDragging() {
        val writesA = mutableListOf<Int>()
        val writesB = mutableListOf<Int>()
        val a = IntSetting("a", 10, 0..100, 1, null, writesA::add)
        val b = IntSetting("b", 20, 0..100, 1, null, writesB::add)
        val first = IntSliderWidget(a)
        val second = IntSliderWidget(b)
        buffer(first, "10")
        buffer(second, "20")
        val ends = mutableListOf<Int>()
        val draw: UiScope.() -> Unit = {
            for ((index, widget) in listOf(first, second).withIndex()) {
                InputRegion(widget, UiRect(0f, index * 30f, 100f, 20f),
                    onPress = { _, _ -> true },
                    onDrag = { event, _, _ -> preview(widget, (event.x() / 100).toFloat()); true },
                    onRelease = { commit(widget); ends.add(index); true },
                    onCancel = widget::cancelInput, onKey = { false }, onBlur = widget::blurInput)
            }
        }
        frame(draw)
        assertTrue(root.input.mouseClicked(pointer(10.0), false))
        assertTrue(root.input.mouseDragged(pointer(70.0), 60.0, 0.0))
        assertTrue(root.input.mouseReleased(pointer(70.0)))
        assertEquals(70, a.value)
        frame(draw)
        assertFalse(root.input.mouseDragged(pointer(90.0), 20.0, 0.0))
        assertTrue(root.input.mouseClicked(pointer(20.0, 40.0), false))
        assertTrue(root.input.mouseDragged(pointer(80.0, 40.0), 60.0, 0.0))
        assertTrue(root.input.mouseReleased(pointer(80.0, 40.0)))
        root.input.clearFocus()
        assertEquals(70, a.value)
        assertEquals(80, b.value)
        assertEquals(listOf(70), writesA)
        assertEquals(listOf(80), writesB)
        assertEquals(listOf(0, 1), ends)
    }

    @Test fun cancelledSliderDoesNotCommitAnOldEditorBufferOnBlur() {
        val writes = mutableListOf<Int>()
        val setting = IntSetting("a", 70, 0..100, 1, null, writes::add)
        val widget = IntSliderWidget(setting)
        buffer(widget, "10")
        preview(widget, 0.9f)
        widget.cancelInput()
        widget.blurInput()
        widget.blurInput()
        assertEquals(70, setting.value)
        assertTrue(writes.isEmpty())
    }

    @Test fun completedDoubleDragDoesNotCommitAnOldEditorBufferOnBlur() {
        val writes = mutableListOf<Double>()
        val setting = DoubleSetting("a", 0.1, 0.0..1.0, 0.1, null, writes::add)
        val widget = DoubleSliderWidget(setting)
        buffer(widget, "0.1")
        preview(widget, 0.7f)
        commit(widget)
        val committed = setting.value
        widget.blurInput()
        assertEquals(committed, setting.value)
        assertEquals(1, writes.size)
    }

    @Test fun unfocusedStringDoesNotOverwriteItsSettingOnBlur() {
        val writes = mutableListOf<String>()
        val setting = StringSetting("a", "current", null, writes::add)
        val widget = StringWidget(setting)
        widget.blurInput()
        assertEquals("current", setting.value)
        assertTrue(writes.isEmpty())
    }
}
