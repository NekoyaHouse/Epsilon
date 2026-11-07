package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.component.ModuleButton
import com.github.epsilon.gui.dropdown.component.SettingSectionRenderer
import com.github.epsilon.gui.dropdown.widget.BoolWidget
import com.github.epsilon.kernel.state.MutableState
import com.github.epsilon.kernel.state.StateNotifications
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.settings.impl.BoolSetting
import kotlin.test.*

class DropdownMeasurementTest {
    private var time = 0L
    private val clock = LuminFrameClock { time }
    private val root = LuminSceneRoot(clock)

    @AfterTest fun close() { root.close(); StateNotifications.drain() }

    private fun measure(block: () -> Float): Float {
        root.beginFrame()
        return try { block() } finally { root.endFrame() }
    }

    @Test fun foldedModuleSkipsSettingsAndResumesMeasurementThroughoutAnimation() {
        var visibilityReads = 0
        val visible = mutableStateOf(true)
        val module = Module("measurement", null)
        repeat(100) { module.boolSetting("setting-$it", false, { visibilityReads++; visible.value }) }
        val button = ModuleButton(module)
        var frameId = 0
        fun height() = measure { button.getHeightForFrame(++frameId) }
        repeat(10) { assertEquals(DropdownTheme.MODULE_HEIGHT, height()) }
        assertEquals(0, visibilityReads)

        // 绕过输入音效与 Screen 初始化，只驱动真实控件的可观察展开状态。
        val field = button.javaClass.getDeclaredField("expanded\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val expanded = field.get(button) as MutableState<Boolean>
        expanded.value = true
        height()
        time += 100_000_000L
        assertTrue(height() > DropdownTheme.MODULE_HEIGHT)
        assertEquals(100, visibilityReads)
        visible.value = false
        val hiddenHeight = height()
        visible.value = true
        assertTrue(height() > hiddenHeight)

        expanded.value = false
        height()
        time += 1_000_000_000L
        visibilityReads = 0
        repeat(10) { assertEquals(DropdownTheme.MODULE_HEIGHT, height()) }
        assertEquals(0, visibilityReads)
        assertEquals(0, clock.taskCount)
    }

    @Test fun collapsedGroupSkipsDescendantsAndRechecksVisibilityWhenOpened() {
        var visibilityReads = 0
        val visible = mutableStateOf(true)
        val group = SettingGroup("group")
        val settings = List(100) { BoolSetting("setting-$it", false, { visibilityReads++; visible.value }, null).group(group) }
        val renderer = SettingSectionRenderer.create("measurement", settings) { BoolWidget(it as BoolSetting) }
        fun height() = measure { renderer.height() }
        val collapsedHeight = height()
        repeat(10) { assertEquals(collapsedHeight, height()) }
        assertEquals(0, visibilityReads)
        group.isCollapsed = false
        height()
        time += 100_000_000L
        val expandedHeight = height()
        assertTrue(expandedHeight > collapsedHeight)
        assertEquals(100, visibilityReads)
        visible.value = false
        assertTrue(height() < expandedHeight)
        group.isCollapsed = true
        height()
        time += 1_000_000_000L
        visibilityReads = 0
        repeat(10) { assertEquals(collapsedHeight, height()) }
        assertEquals(0, visibilityReads)
        assertEquals(0, clock.taskCount)
    }
}
