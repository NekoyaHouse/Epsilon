package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.kernel.state.StateNotifications
import com.github.epsilon.utils.render.animation.Easing
import java.awt.Color
import kotlin.test.*

class LuminRuntimeTest {
    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }
    private var time = 0L
    private val clock = LuminFrameClock { time }
    private val root = LuminSceneRoot(clock)
    private val bounds = UiRect(10f, 20f, 100f, 100f)
    private val view = LuminView()

    @AfterTest fun close() { root.close(); view.close(); StateNotifications.drain() }

    private fun frame(rect: UiRect = bounds, x: Int = 0, y: Int = 0, block: UiScope.() -> Unit): List<UiTree.UiNode> {
        root.beginFrame()
        return try {
            view.render(x, y) {
                val entry = root.nextEntry(rect, "root")
                entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, rect), block)
                entry.resolve()
            }
        } finally { root.endFrame() }
    }

    @Test fun childRebuildsWithCachedParentAndRestoredLayerClipOrigin() {
        val state = mutableStateOf(1f)
        var parents = 0
        var children = 0
        val draw: UiScope.() -> Unit = {
            parents++
            Layer(LayerOffset(3)) {
                Clip(0f, 0f, 50f, 50f) {
                    Positioned(4f, 5f, 10f, 10f) {
                        LuminUIItem("child") { children++; Rect(0f, state.value, 2f, 2f, Color.WHITE) }
                    }
                }
            }
        }
        frame(block = draw)
        frame(block = draw)
        assertEquals(1, parents)
        assertEquals(1, children)
        state.value = 7f
        val nodes = frame(block = draw)
        assertEquals(1, parents)
        assertEquals(2, children)
        val layer = nodes.single() as UiTree.LayerNode
        assertEquals(3, layer.layer)
        val clip = layer.children.single() as UiTree.ScissorNode
        assertEquals(UiRect(10f, 20f, 50f, 50f), clip.clip)
        val rect = clip.children.single() as UiTree.RectNode
        assertEquals(14f, rect.x)
        assertEquals(32f, rect.y)
    }

    @Test fun keyedRememberSurvivesReorderGeometryAndClosesRemovedItems() {
        val items = mutableStateOf(listOf("a", "b"))
        val identities = mutableMapOf<String, Any>()
        var closed = 0
        val draw: UiScope.() -> Unit = {
            LuminUIItems(items.value, key = { it }) { item ->
                val identity = remember("resource") { AutoCloseable { closed++ } }
                identities.putIfAbsent(item, identity)?.let { assertSame(it, identity) }
            }
        }
        frame(block = draw)
        items.value = listOf("b", "c", "a")
        frame(block = draw)
        frame(bounds.translate(3f, 5f), block = draw)
        assertEquals(0, closed)
        items.value = listOf("c")
        frame(block = draw)
        assertEquals(2, closed)
        root.close()
        assertEquals(3, closed)
    }

    @Test fun parentParameterRebindsClosureAndDuplicateKeysFail() {
        val param = mutableStateOf(1f)
        var drawn = 0f
        val draw: UiScope.() -> Unit = {
            val snapshot = param.value
            LuminUIItem("child") { drawn = snapshot }
        }
        frame(block = draw)
        param.value = 2f
        frame(block = draw)
        assertEquals(2f, drawn)
        assertFailsWith<IllegalStateException> {
            frame(bounds.translate(1f, 0f)) { LuminUIItem("x") {}; LuminUIItem("x") {} }
        }
    }

    @Test fun immediateChildExecutesOncePerFrameEvenWhenParentRebuilds() {
        val state = mutableStateOf(0)
        var count = 0
        val draw: UiScope.() -> Unit = {
            state.value
            LuminUIGroupImmediate("immediate") { count++ }
        }
        frame(block = draw)
        frame(block = draw)
        state.value++
        frame(block = draw)
        assertEquals(3, count)
    }

    @Test fun hoverChangesOnlyWhenCrossingBoundsAndTracksMovedGeometry() {
        var builds = 0
        val draw: UiScope.() -> Unit = {
            builds++
            origin.contains(LuminRuntime.currentView!!.mouseX.toDouble(), LuminRuntime.currentView!!.mouseY.toDouble())
        }
        frame(x = 12, y = 22, block = draw)
        frame(x = 13, y = 23, block = draw)
        assertEquals(1, builds)
        frame(x = 200, y = 200, block = draw)
        assertEquals(2, builds)
        frame(UiRect(190f, 190f, 100f, 100f), x = 200, y = 200, block = draw)
        assertEquals(3, builds)
    }

    @Test fun nestedHoverKeepsCapturedPointerOriginWhenParentRebuildsIndependently() {
        var rootBuilds = 0
        var parentBuilds = 0
        var childBuilds = 0
        fun tick(px: Int, py: Int) = frame(x = px, y = py) {
            rootBuilds++
            LuminUIItem("parent") {
                parentBuilds++
                val hovered = origin.contains(px.toDouble(), py.toDouble())
                Text("parent:$hovered", 0f, 0f, 1f, Color.WHITE)
                LuminUIItem("child") {
                    childBuilds++
                    val localHovered = UiRect(0f, 0f, width, height)
                        .contains(px - origin.x.toDouble(), py - origin.y.toDouble())
                    Text("child:$localHovered", 0f, 1f, 1f, Color.WHITE)
                }
            }
        }.filterIsInstance<UiTree.TextNode>().map { it.text }

        assertEquals(listOf("parent:true", "child:true"), tick(12, 22))
        assertEquals(listOf("parent:false", "child:false"), tick(200, 200))
        assertEquals(1, rootBuilds)
        val settled = parentBuilds to childBuilds
        repeat(3) { assertEquals(listOf("parent:false", "child:false"), tick(201 + it, 201 + it)) }
        assertEquals(settled, parentBuilds to childBuilds)
        assertEquals(listOf("parent:true", "child:true"), tick(12, 22))
        assertEquals(1, rootBuilds)
        assertEquals(listOf("parent:false", "child:false"), tick(-1, -1))
        assertEquals(listOf("parent:true", "child:true"), tick(12, 22))
    }

    @Test fun dropdownGroupHoverMovesToCurrentHeaderWithCachedParent() {
        val theme = com.github.epsilon.gui.dropdown.DropdownTheme
        val settings = List(2) { index ->
            com.github.epsilon.settings.impl.BoolSetting("setting-$index", false, null, null).apply {
                groupRef = com.github.epsilon.settings.SettingGroup("group-$index")
            }
        }
        val renderer = com.github.epsilon.gui.dropdown.component.SettingSectionRenderer.create("hover", settings) {
            com.github.epsilon.gui.dropdown.widget.BoolWidget(it as com.github.epsilon.settings.impl.BoolSetting)
        }
        var rootBuilds = 0
        fun tick(py: Int) = frame(x = 30, y = py) {
            rootBuilds++
            LuminUIItem("settings") { renderer.draw(this, 30, py, 0f, 0f, width) }
        }.filterIsInstance<UiTree.RoundRectNode>().filter {
            it.height == theme.GROUP_HEADER_HEIGHT && it.color == theme.groupHeaderHover(1f)
        }.map { it.y }

        val lowerY = bounds.y + theme.GROUP_HEADER_HEIGHT + theme.SETTING_GAP
        tick(lowerY.toInt() + 5)
        time = 200_000_000L
        assertEquals(listOf(lowerY), tick(lowerY.toInt() + 5))
        tick(bounds.y.toInt() + 5)
        time += 200_000_000L
        assertEquals(listOf(bounds.y), tick(bounds.y.toInt() + 5))
        tick(200)
        time += 200_000_000L
        assertEquals(emptyList(), tick(200))
        assertEquals(1, rootBuilds)
        assertEquals(0, clock.taskCount)
    }

    @Test fun animationsRetargetFinishAndReleaseClockTasks() {
        val target = mutableStateOf(0f)
        lateinit var animation: LuminAnimatable
        var builds = 0
        val draw: UiScope.() -> Unit = {
            builds++
            animation = rememberAnimatable("animation", 0f, Easing.LINEAR, 100)
            animation.target = target.value
            animation.value
        }
        frame(block = draw)
        assertEquals(0, clock.taskCount)
        target.value = 1f
        frame(block = draw)
        assertEquals(1, clock.taskCount)
        time = 50_000_000L
        frame(block = draw)
        assertEquals(0.5f, animation.value, 0.0001f)
        target.value = 0f
        frame(block = draw)
        time += 100_000_000L
        frame(block = draw)
        assertEquals(0f, animation.value)
        assertEquals(0, clock.taskCount)
        val finished = builds
        repeat(4) { time += 16_000_000L; frame(block = draw) }
        assertEquals(finished, builds)
        animation.duration = 0
        target.value = 2f
        frame(block = draw)
        assertEquals(2f, animation.value)
        assertEquals(0, clock.taskCount)
        animation.duration = 100
        target.value = 3f
        frame(block = draw)
        assertEquals(1, clock.taskCount)
        root.close()
        assertEquals(0, clock.taskCount)
    }

    @Test fun blinkCancelsWhenFocusBranchDisappears() {
        val focused = mutableStateOf(true)
        var builds = 0
        val draw: UiScope.() -> Unit = { builds++; if (focused.value) luminBlink("caret", 500) }
        frame(block = draw)
        time = 100_000_000L
        frame(block = draw)
        assertEquals(1, builds)
        time = 500_000_000L
        frame(block = draw)
        assertEquals(2, builds)
        focused.value = false
        frame(block = draw)
        assertEquals(0, clock.taskCount)
    }
    @Test fun viewportChildPreservesItsNestingAndScrolledOrigin() {
        val buffer = com.github.epsilon.gui.lib.render.UiContentBuffer(com.github.epsilon.gui.theme.EpsilonUiTheme)
        try {
            val offset = mutableStateOf(1f)
            var parents = 0
            val draw: UiScope.() -> Unit = {
                parents++
                Viewport(buffer, UiRect(3f, 4f, 40f, 50f), 7f, 100f, 150f) {
                    LuminUIItem("viewport-item") { Rect(2f, offset.value, 3f, 4f, Color.WHITE) }
                }
            }
            frame(block = draw)
            offset.value = 8f
            val node = frame(block = draw).single() as UiTree.ViewportNode
            assertEquals(1, parents)
            assertSame(buffer, node.buffer)
            assertEquals(UiRect(13f, 24f, 40f, 50f), node.viewport)
            val rect = node.children.single() as UiTree.RectNode
            assertEquals(15f, rect.x)
            assertEquals(25f, rect.y)
        } finally { buffer.close() }
    }

    @Test fun failedBuildDisposesOnlyNewResourcesAndKeepsInvalidation() {
        val fail = mutableStateOf(false)
        var disposed = 0
        var builds = 0
        val draw: UiScope.() -> Unit = {
            builds++
            remember("stable") { AutoCloseable { disposed++ } }
            if (fail.value) {
                remember("abandoned") { AutoCloseable { disposed++ } }
                error("build failure")
            }
            Rect(0f, 0f, 1f, 1f, Color.WHITE)
        }
        frame(block = draw)
        fail.value = true
        assertFailsWith<IllegalStateException> { frame(block = draw) }
        assertEquals(1, disposed)
        fail.value = false
        assertEquals(1, frame(block = draw).size)
        assertEquals(3, builds)
        root.close()
        assertEquals(2, disposed)
    }

    @Test fun repeatedOpenCloseAndConditionalRootsReleaseSubscriptions() {
        val state = mutableStateOf(0)
        var builds = 0
        repeat(8) {
            frame { builds++; state.value; luminFrameTime("clock") }
            assertEquals(1, clock.taskCount)
            root.beginFrame()
            root.endFrame()
            assertEquals(0, clock.taskCount)
            state.value++
            StateNotifications.drain()
        }
        assertEquals(8, builds)
    }

    @Test fun pointerInputRunsOutsideBuildAndIsRemovedWithItsOwner() {
        val value = mutableStateOf(0)
        var callbacks = 0
        val draw: UiScope.() -> Unit = {
            luminPointerInput("drag") { x, _ -> callbacks++; value.value = x }
            value.value
        }
        frame(x = 1, block = draw)
        frame(x = 2, block = draw)
        assertEquals(2, value.value)
        root.close()
        val stopped = callbacks
        view.render(3, 0) {}
        assertEquals(stopped, callbacks)
    }

    @Test fun snapStopsActiveTaskAndTwoGroupsShareOneClockAdvance() {
        val animation = LuminAnimatable(0f, Easing.LINEAR, 100)
        val draw: UiScope.() -> Unit = {
            LuminUIItem("a") { animation.target = 1f; animation.value }
            LuminUIItem("b") { animation.target = 1f; animation.value }
        }
        frame(block = draw)
        assertEquals(1, clock.taskCount)
        time = 50_000_000L
        frame(block = draw)
        assertEquals(0.5f, animation.value, 0.0001f)
        animation.snapTo(0.7f)
        assertEquals(0.7f, animation.value)
        assertEquals(0, clock.taskCount)
    }

    @Test fun cachedViewportReadsTheCurrentPointerAndGeometryKeepsRemember() {
        val buffer = com.github.epsilon.gui.lib.render.UiContentBuffer(com.github.epsilon.gui.theme.EpsilonUiTheme)
        try {
            var builds = 0
            var remembered: Any? = null
            val draw: UiScope.() -> Unit = {
                builds++
                val value = remember("identity") { Any() }
                if (remembered == null) remembered = value else assertSame(remembered, value)
                val x = LuminRuntime.currentView!!.mouseX
                val y = LuminRuntime.currentView!!.mouseY
                Viewport(buffer, size, 0f, 10f, 200f, x, y) {}
            }
            frame(x = 20, y = 30, block = draw)
            val node = frame(x = 21, y = 31, block = draw).single() as UiTree.ViewportNode
            assertEquals(1, builds)
            assertEquals(21 to 31, node.pointer!!.invoke())
            frame(bounds.translate(1f, 2f), x = 21, y = 31, block = draw)
            assertEquals(2, builds)
        } finally { buffer.close() }
    }

    @Test fun failedBuildDoesNotChangeAnExistingAnimationTargetOrTiming() {
        val fail = mutableStateOf(false)
        lateinit var animation: LuminAnimatable
        val draw: UiScope.() -> Unit = {
            animation = rememberAnimatable("animation", 0f, Easing.LINEAR, 100)
            animation.value
            if (fail.value) {
                animation.animateTo(2f)
                animation.reset()
                animation.snapTo(3f)
                error("abandon animation declarations")
            }
            animation.animateTo(1f)
        }
        frame(block = draw)
        time = 50_000_000L
        fail.value = true
        assertFailsWith<IllegalStateException> { frame(block = draw) }
        assertEquals(1f, animation.target)
        assertEquals(0.5f, animation.value, 0.0001f)
        assertEquals(1, clock.taskCount)
        fail.value = false
        time = 75_000_000L
        frame(block = draw)
        assertEquals(0.75f, animation.value, 0.0001f)
        time = 100_000_000L
        frame(block = draw)
        assertEquals(1f, animation.value)
        assertEquals(0, clock.taskCount)
    }

    @Test fun changingDurationToZeroFinishesAnUnchangedTarget() {
        val duration = mutableStateOf(100L)
        var progress = 0f
        val draw: UiScope.() -> Unit = {
            progress = animateFloatAsState("animation", 1f, Easing.LINEAR, duration.value).value
        }
        val target = mutableStateOf(0f)
        val animated: UiScope.() -> Unit = {
            progress = animateFloatAsState("animation", target.value, Easing.LINEAR, duration.value).value
        }
        frame(block = animated)
        target.value = 1f
        frame(block = animated)
        time = 50_000_000L
        frame(block = animated)
        assertEquals(0.5f, progress, 0.0001f)
        duration.value = 0L
        frame(block = draw)
        assertEquals(0, clock.taskCount)
        frame(block = draw)
        assertEquals(1f, progress)
    }

    @Test fun separateWindowPreparationInvalidatesOnlyItsOwnerAtUnchangedBounds() {
        val data = mutableStateOf("before")
        val window = LuminView()
        var prepared = ""
        var drawn = ""
        var windowBuilds = 0
        var siblingBuilds = 0
        fun drawFrame() {
            root.beginFrame()
            try {
                window.prepare { prepared = data.value }
                window.render(0, 0) {
                    val entry = root.nextEntry(bounds, "window")
                    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), {
                        windowBuilds++
                        drawn = prepared
                    })
                    entry.resolve()
                }
                view.render(0, 0) {
                    val entry = root.nextEntry(bounds, "sibling")
                    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), { siblingBuilds++ })
                    entry.resolve()
                }
            } finally { root.endFrame() }
        }
        try {
            drawFrame()
            drawFrame()
            assertEquals(1, windowBuilds)
            data.value = "after"
            drawFrame()
            assertEquals("after", drawn)
            assertEquals(2, windowBuilds)
            assertEquals(1, siblingBuilds)
            root.beginFrame()
            root.endFrame()
            data.value = "closed"
            StateNotifications.drain()
            drawFrame()
            assertEquals("closed", drawn)
        } finally { window.close() }
    }

    @Test fun trollPhysicsScrollSettlesAndClosesWithItsComposition() {
        val scroll = com.github.epsilon.gui.troll.TrollScroll()
        var position = 0f
        fun tick() {
            root.beginFrame()
            try {
                position = scroll.update(200f)
                view.render(0, 0) {
                    val entry = root.nextEntry(bounds, "root")
                    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), {
                        LuminRuntime.currentComposition!!.retain("scroll") { scroll }
                        Unit
                    })
                    entry.resolve()
                }
            } finally { root.endFrame() }
        }
        tick()
        scroll.scroll(-2.0)
        assertEquals(1, clock.taskCount)
        repeat(500) { time += 16_000_000L; tick() }
        assertEquals(0, clock.taskCount)
        assertTrue(position in 0f..200f)
        scroll.scroll(20.0)
        repeat(500) { time += 16_000_000L; tick() }
        assertEquals(0, clock.taskCount)
        assertTrue(position in 0f..200f)
        scroll.scroll(-1.0)
        assertEquals(1, clock.taskCount)
        root.close()
        assertEquals(0, clock.taskCount)
    }

    @Test fun configDropdownInitialTextSyncRunsOnlyAfterSuccessfulComposition() {
        com.github.epsilon.assets.i18n.EpsilonTranslations.Gui.CONFIG_ACTION_SAVE_AS.getTranslatedName()
        val activeName = com.github.epsilon.managers.ConfigManager.activeConfigName
        val panel = com.github.epsilon.gui.dropdown.component.ConfigDropdownPanel(0)
        panel.isOpened = true
        val fail = mutableStateOf(false)
        var frameId = 0
        var builds = 0
        val draw: UiScope.() -> Unit = {
            builds++
            panel.beginRenderFrame(++frameId)
            with(panel) { drawContent(0, 0) }
            if (fail.value) error("discard config frame")
        }
        frame(block = draw)
        time = 300_000_000L
        fail.value = true
        assertEquals("discard config frame", assertFailsWith<IllegalStateException> { frame(block = draw) }.message)
        fail.value = false
        val first = frame(block = draw).filterIsInstance<UiTree.TextNode>().single { it.text == activeName }
        assertEquals(com.github.epsilon.gui.theme.MD3Theme.TEXT_MUTED, first.color)
        val committed = frame(block = draw).filterIsInstance<UiTree.TextNode>().single { it.text == activeName }
        assertEquals(com.github.epsilon.gui.theme.MD3Theme.TEXT_PRIMARY, committed.color)
        val settled = builds
        repeat(3) { time += 16_000_000L; frame(block = draw) }
        assertEquals(settled, builds)
    }

    @Test fun dropdownExpansionMovesFollowingRowsWithoutScrolling() {
        com.github.epsilon.modules.impl.ClientSetting.moduleSort.value
        val modules = (0..7).map { com.github.epsilon.modules.Module("module-$it", null) }
        modules.first().boolSetting("detail", false)
        val panel = com.github.epsilon.gui.dropdown.component.CategoryPanel("test", "test", "", 0, modules)
        panel.isOpened = true
        panel.startIntro()
        panel.updateMaxPanelHeight(90f)
        panel.setPosition(10f, 20f)
        val host = LuminView()
        var frameId = 0
        var builds = 0
        fun tick(): List<UiTree.UiNode> {
            root.beginFrame()
            return try {
                host.render(-1, -1) {
                    panel.beginRenderFrame(++frameId)
                    view.render(-1, -1) {
                        val rect = UiRect(panel.x, panel.contentClipY, panel.width, panel.contentClipHeight)
                        val entry = root.nextEntry(rect, "root")
                        entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, rect), {
                            builds++
                            Clip(0f, 0f, width, height) { with(panel) { drawContent(-1, -1) } }
                        })
                        entry.resolve()
                    }
                }
            } finally { root.endFrame() }
        }
        fun texts(nodes: List<UiTree.UiNode>): List<UiTree.TextNode> = nodes.flatMap { node ->
            when (node) {
                is UiTree.TextNode -> listOf(node)
                is UiTree.LayerNode -> texts(node.children)
                is UiTree.ScissorNode -> texts(node.children)
                else -> emptyList()
            }
        }
        try {
            val initialY = texts(tick()).single { it.text == modules[1].name }.y
            val buttonsField = panel.javaClass.getDeclaredField("moduleButtons").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val buttons = buttonsField.get(panel) as List<com.github.epsilon.gui.dropdown.component.ModuleButton>
            // 直接驱动控件状态，避免输入反馈初始化游戏 Screen 与资源系统。
            val expandedField = buttons.first().javaClass.getDeclaredField("expanded\$delegate").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val expanded = expandedField.get(buttons.first()) as com.github.epsilon.kernel.state.MutableState<Boolean>
            expanded.value = true
            tick()
            for (timestamp in listOf(110_000_000L, 165_000_000L, 220_000_000L)) {
                time = timestamp
                val rendered = texts(tick())
                val extra = buttons.first().getHeightForFrame(frameId) - com.github.epsilon.gui.dropdown.DropdownTheme.MODULE_HEIGHT
                assertTrue(extra > 0f)
                assertEquals(initialY + extra, rendered.single { it.text == modules[1].name }.y, 0.0001f)
                assertEquals(buttons.first().y + buttons.first().getHeightForFrame(frameId), buttons[1].y, 0.0001f)
                assertEquals(0f, panel.scrollOffset)
            }
            expanded.value = false
            tick()
            time += 220_000_000L
            assertEquals(initialY, texts(tick()).single { it.text == modules[1].name }.y, 0.0001f)
            tick()
            val settled = builds
            repeat(3) { time += 16_000_000L; tick() }
            assertEquals(settled, builds)
            assertEquals(0, clock.taskCount)
        } finally { host.close() }
    }

}
