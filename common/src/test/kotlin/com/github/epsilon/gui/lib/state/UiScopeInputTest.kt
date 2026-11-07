package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.kernel.state.StateNotifications
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import kotlin.test.*

class UiScopeInputTest {
    private val metrics = object : UiTextMetrics {
        override fun textWidth(text: String, scale: Float) = text.length * scale
        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader) = textWidth(text, scale)
        override fun textHeight(scale: Float) = scale
        override fun textHeight(scale: Float, fontLoader: TtfFontLoader) = scale
    }
    private val root = LuminSceneRoot()
    private val view = LuminView()
    private val bounds = UiRect(10f, 20f, 100f, 100f)
    private val left = InputConstants.MOUSE_BUTTON_LEFT
    private val right = InputConstants.MOUSE_BUTTON_RIGHT
    private fun pointer(x: Double = 15.0, y: Double = 25.0, button: Int = left) =
        MouseButtonEvent(x, y, MouseButtonInfo(button, 0))

    private fun frame(rect: UiRect = bounds, block: UiScope.() -> Unit) {
        root.beginFrame()
        try {
            view.render(15, 25) {
                val entry = root.nextEntry(rect, "root")
                entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, rect), block)
                entry.emit(UiTree.Scope())
            }
            root.endFrame()
        } catch (failure: Throwable) {
            root.abortFrame()
            throw failure
        }
    }

    @AfterTest fun close() { root.close(); view.close(); StateNotifications.drain() }

    @Test fun cachedTreeKeepsSubscriptionsAndLocalCoordinates() {
        var builds = 0
        var clicks = 0
        val draw: UiScope.() -> Unit = {
            builds++
            Positioned(2f, 3f, 20f, 20f) {
                Interaction("button") {
                    onClick {
                        assertEquals(3.0, it.localX)
                        assertEquals(2.0, it.localY)
                        assertTrue(it.doubleClick)
                        clicks++
                        true
                    }
                }
            }
        }
        repeat(3) { frame(block = draw); assertTrue(view.input.mouseClicked(pointer(), true)) }
        assertEquals(1, builds)
        assertEquals(3, clicks)
        assertFalse(view.input.mouseClicked(pointer(90.0, 90.0), false))
    }

    @Test fun layersClipsAndPaintOrderDetermineHit() {
        val hits = mutableListOf<String>()
        frame {
            Above {
                Clip(0f, 0f, 10f, 10f) {
                    Interaction("upper") { onClick { hits.add("upper"); true } }
                }
            }
            Interaction("lower") { onClick { hits.add("lower"); true } }
            Clip(500f, 500f, 5f, 5f) {
                Clip(0f, 0f, 10f, 10f) {
                    Interaction("empty-clip") { onClick { fail("空裁剪区域不能收到输入") } }
                }
            }
        }
        view.input.mouseClicked(pointer(), false)
        view.input.mouseClicked(pointer(30.0, 40.0), false)
        assertEquals(listOf("upper", "lower"), hits)
        frame(bounds.translate(1f, 0f)) {
            Interaction("first") { onClick { hits.add("first"); true } }
            Interaction("last") { onClick { hits.add("last"); true } }
        }
        view.input.mouseClicked(pointer(), false)
        assertEquals("last", hits.last())
    }

    @Test fun dragCapturesOutsideAndIgnoresOtherButtons() {
        val events = mutableListOf<String>()
        frame {
            Interaction("slider") {
                onDrag(
                    onStart = { events.add("start"); true },
                    onEnd = { events.add("end:${it.x}") },
                    onCancel = { events.add("cancel") },
                ) { events.add("drag:${it.deltaX}:${it.localX}") }
            }
        }
        assertTrue(view.input.mouseClicked(pointer(), false))
        assertFalse(view.input.mouseClicked(pointer(button = right), false))
        assertFalse(view.input.mouseReleased(pointer(button = right)))
        assertTrue(view.input.mouseDragged(pointer(500.0), 485.0, 0.0))
        assertTrue(view.input.mouseReleased(pointer(500.0)))
        assertFalse(view.input.mouseReleased(pointer()))
        assertEquals(listOf("start", "drag:485.0:490.0", "end:500.0"), events)
    }

    @Test fun focusRoutesTextToOnlyOneNodeAndDisposesOnRemoval() {
        val focus = mutableListOf<String>()
        val typed = mutableListOf<String>()
        var visible by mutableStateOf(true)
        val draw: UiScope.() -> Unit = {
            if (visible) {
                for (id in 0..1) Positioned(id * 30f, 0f, 20f, 20f) {
                    Interaction(id) {
                        onClick { true }
                        onFocusChanged { focus.add("$id:$it") }
                        onTextInput { typed.add("$id:${it.codepointAsString()}"); true }
                        onKeyPressed { typed.add("key:$id"); true }
                    }
                }
            }
        }
        frame(block = draw)
        assertFalse(view.input.charTyped(CharacterEvent('字'.code)))
        view.input.mouseClicked(pointer(), false)
        view.input.charTyped(CharacterEvent('字'.code))
        frame(block = draw)
        assertEquals(listOf("0:true"), focus)
        view.input.mouseClicked(pointer(45.0), false)
        view.input.charTyped(CharacterEvent(0x1F600))
        assertTrue(view.input.keyPressed(KeyEvent(InputConstants.KEY_LEFT, 0, 0)))
        visible = false
        frame(block = draw)
        assertFalse(view.input.charTyped(CharacterEvent('x'.code)))
        assertEquals(listOf("0:true", "0:false", "1:true", "1:false"), focus)
        assertEquals(listOf("0:字", "1:😀", "key:1"), typed)
    }

    @Test fun geometryAndCallbacksUpdateWhileDragIdentitySurvives() {
        var offset by mutableStateOf(0f)
        var parameter by mutableStateOf("old")
        val events = mutableListOf<String>()
        var parents = 0
        val draw: UiScope.() -> Unit = {
            parents++
            LuminUIItem("child") {
                val current = parameter
                Positioned(offset, 0f, 20f, 20f) {
                    Interaction("slider") {
                        onDrag(onCancel = { events.add("cancel:$current") }) { events.add("$current:${it.localX}") }
                    }
                }
            }
        }
        frame(block = draw)
        view.input.mouseClicked(pointer(), false)
        offset = 30f
        parameter = "new"
        frame(block = draw)
        view.input.mouseDragged(pointer(50.0), 35.0, 0.0)
        view.close()
        assertEquals(listOf("new:10.0", "cancel:new"), events)
        assertEquals(1, parents)
        assertFalse(view.input.mouseReleased(pointer()))
    }

    @Test fun disabledOrMissingNodeCancelsOnce() {
        var enabled by mutableStateOf(true)
        var cancels = 0
        val draw: UiScope.() -> Unit = {
            Interaction("drag", enabled = enabled) {
                onDrag(onCancel = { cancels++ }) {}
            }
        }
        frame(block = draw)
        view.input.mouseClicked(pointer(), false)
        enabled = false
        frame(block = draw)
        assertEquals(1, cancels)
        assertFalse(view.input.mouseDragged(pointer(), 1.0, 1.0))
        enabled = true
        frame(block = draw)
        view.input.mouseClicked(pointer(), false)
        frame {}
        root.close()
        assertEquals(2, cancels)
    }

    @Test fun failedBuildDoesNotPublishNewCallbacks() {
        var value by mutableStateOf("old")
        var fail by mutableStateOf(false)
        val calls = mutableListOf<String>()
        val draw: UiScope.() -> Unit = {
            val snapshot = value
            Interaction("button") { onClick { calls.add(snapshot); true } }
            if (fail) error("失败组合")
        }
        frame(block = draw)
        value = "new"
        fail = true
        assertFailsWith<IllegalStateException> { frame(block = draw) }
        view.input.mouseClicked(pointer(), false)
        fail = false
        frame(block = draw)
        view.input.mouseClicked(pointer(), false)
        assertEquals(listOf("old", "new"), calls)
    }

    @Test fun subscriptionsRequireHostAndRejectDuplicateKeys() {
        val scope = UiTree.Scope().asUiScope(metrics)
        assertFailsWith<IllegalStateException> { scope.Interaction("outside") {} }
        assertFailsWith<IllegalStateException> {
            frame { Interaction("duplicate") {}; Interaction("duplicate") {} }
        }
    }

    @Test fun viewportClipsScrolledInputAndCaptureKeepsReceivingOutside() {
        val buffer = com.github.epsilon.gui.lib.render.UiContentBuffer(com.github.epsilon.gui.theme.EpsilonUiTheme)
        var updates = 0
        try {
            frame {
                Viewport(buffer, UiRect(0f, 0f, 20f, 20f), 10f, 100f, 150f) {
                    Positioned(0f, 10f, 20f, 40f) {
                        Interaction("scrolled") { onDrag { updates++; assertEquals(50.0, it.localY) } }
                    }
                }
            }
            assertFalse(view.input.mouseClicked(pointer(y = 45.0), false))
            assertTrue(view.input.mouseClicked(pointer(), false))
            assertTrue(view.input.mouseDragged(pointer(y = 70.0), 0.0, 45.0))
            assertTrue(view.input.mouseReleased(pointer(y = 70.0)))
            assertEquals(1, updates)
        } finally { buffer.close() }
    }

    @Test fun rootLayersAndMultipleButtonsShareOneView() {
        val hits = mutableListOf<String>()
        root.beginFrame()
        try {
            view.render(15, 25) {
                for (layer in listOf(400, 100)) {
                    val entry = root.nextEntry(bounds, layer)
                    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), {
                        Interaction("button") {
                            onClick { hits.add("left:$layer"); true }
                            onClick(right) { hits.add("right:$layer"); true }
                        }
                    })
                    entry.emit(UiTree.Scope(), layer)
                }
            }
        } finally { root.endFrame() }
        assertTrue(view.input.mouseClicked(pointer(), false))
        assertTrue(view.input.mouseClicked(pointer(button = right), false))
        assertEquals(listOf("left:400", "right:400"), hits)
    }

    @Test fun changingCapabilitiesReleasesOldSubscriptionsAndOutsideClickBlurs() {
        var dragEnabled by mutableStateOf(true)
        var focusEnabled by mutableStateOf(true)
        var cancels = 0
        val focus = mutableListOf<Boolean>()
        val draw: UiScope.() -> Unit = {
            Interaction("node") {
                onClick { true }
                if (dragEnabled) onDrag(onCancel = { cancels++ }) {}
                if (focusEnabled) {
                    onTextInput { true }
                    onFocusChanged { focus.add(it) }
                }
            }
        }
        frame(block = draw)
        view.input.mouseClicked(pointer(), false)
        dragEnabled = false
        focusEnabled = false
        frame(block = draw)
        assertEquals(1, cancels)
        assertEquals(listOf(true, false), focus)
        focusEnabled = true
        frame(block = draw)
        view.input.mouseClicked(pointer(), false)
        view.input.mouseClicked(pointer(900.0), false)
        assertEquals(listOf(true, false, true, false), focus)
    }

    private fun sharedFrame(vararg content: Pair<LuminView, UiScope.() -> Unit>) {
        root.beginFrame()
        try {
            for ((index, item) in content.withIndex()) item.first.render(15, 25) {
                val entry = root.nextEntry(bounds, index)
                entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), item.second)
                entry.emit(UiTree.Scope(), index * 100)
            }
            root.endFrame()
        } catch (failure: Throwable) { root.abortFrame(); throw failure }
    }

    @Test fun sceneOwnsOneFocusAcrossViewsAndClosingOneViewKeepsTheOther() {
        val other = LuminView()
        val events = mutableListOf<String>()
        try {
            sharedFrame(view to {
                Interaction("first") {
                    onClick { true }
                    onFocusChanged { events.add("first:$it") }
                    onTextInput { events.add("first:text"); true }
                }
            }, other to {
                Interaction("second", UiRect(30f, 0f, 20f, 20f)) {
                    onClick { true }
                    onFocusChanged { events.add("second:$it") }
                    onTextInput { events.add("second:text"); true }
                }
            })
            assertSame(root.input, view.input)
            assertSame(root.input, other.input)
            root.input.mouseClicked(pointer(), false)
            root.input.mouseClicked(pointer(45.0), false)
            view.close()
            assertTrue(root.input.charTyped(CharacterEvent('x'.code)))
            assertEquals(listOf("first:true", "first:false", "second:true", "second:text"), events)
        } finally { other.close() }
        assertEquals("second:false", events.last())
    }

    @Test fun modalOwnerNeverFallsThroughToUnderlyingViewOrKeyboardFocus() {
        val popup = LuminView()
        var backgroundEvents = 0
        try {
            sharedFrame(view to {
                Interaction("background") {
                    onClick { backgroundEvents++; true }
                    onTextInput { backgroundEvents++; true }
                    onScroll { _, _, _, _ -> backgroundEvents++; true }
                }
            }, popup to {
                Interaction("popup") { onClick { false } }
            })
            root.input.mouseClicked(pointer(), false)
            root.input.within(popup) {
                assertFalse(charTyped(CharacterEvent('x'.code)))
                assertFalse(mouseScrolled(15.0, 25.0, 0.0, 1.0))
                assertFalse(mouseClicked(pointer(), false))
            }
            assertEquals(1, backgroundEvents)
            assertTrue(root.input.mouseClicked(pointer(), false))
            assertEquals(2, backgroundEvents)
        } finally { popup.close() }
    }

    @Test fun autoFocusRequestsOnlyOnActivationAndExplicitFocusUsesStableKey() {
        var auto by mutableStateOf(true)
        val focus = mutableListOf<String>()
        val draw: UiScope.() -> Unit = {
            for (id in 0..1) Interaction(id) {
                autoFocus = id == 1 && auto
                onTextInput { true }
                onFocusChanged { focus.add("$id:$it") }
            }
        }
        frame(block = draw)
        assertEquals(listOf("1:true"), focus)
        assertTrue(root.input.requestFocus(0))
        frame(block = draw)
        assertEquals("0:true", focus.last())
        auto = false
        frame(block = draw)
        auto = true
        frame(block = draw)
        assertEquals(listOf("1:true", "1:false", "0:true", "0:false", "1:true"), focus)
        assertFalse(root.input.requestFocus("missing"))
    }

    @Test fun focusedPreviewCapturesMouseBindingOutsideItsRegion() {
        var boundButton = -1
        var buttonClicks = 0
        var listening = false
        frame {
            Interaction("bind", UiRect(0f, 0f, 20f, 20f)) {
                onClick { listening = true; true }
                onPreviewClick {
                    if (listening) { boundButton = it.source.button(); listening = false; true } else false
                }
            }
            Interaction("button", UiRect(30f, 0f, 20f, 20f)) { onClick { buttonClicks++; true } }
        }
        root.input.mouseClicked(pointer(), false)
        assertTrue(root.input.mouseClicked(pointer(45.0, button = right), false))
        assertEquals(right, boundButton)
        assertEquals(0, buttonClicks)
        root.input.mouseClicked(pointer(45.0), false)
        assertEquals(1, buttonClicks)
    }

    @Test fun scrollRespectsLayerClipAndFalseFallsThrough() {
        val events = mutableListOf<String>()
        frame {
            Interaction("base") { onScroll { _, _, _, _ -> events.add("base"); true } }
            Above {
                Clip(0f, 0f, 20f, 20f) {
                    Interaction("top") {
                        onScroll { _, _, sx, sy -> events.add("top:$sx:$sy"); false }
                    }
                }
            }
        }
        assertTrue(root.input.mouseScrolled(15.0, 25.0, 2.0, -3.0))
        assertTrue(root.input.mouseScrolled(50.0, 60.0, 0.0, 1.0))
        assertEquals(listOf("top:2.0:-3.0", "base", "base"), events)
    }

    @Test fun inputRegionSupportsAuxiliaryButtonsAndCancelsBeforeBlur() {
        val events = mutableListOf<String>()
        frame {
            InputRegion("editor", onPress = { event, _ -> events.add("press:${event.button()}"); true },
                onDrag = { _, dx, dy -> events.add("drag:$dx:$dy"); true },
                onRelease = { events.add("commit"); true }, onKey = { true },
                onCancel = { events.add("cancel") }, onBlur = { events.add("blur") })
        }
        val auxiliary = InputConstants.MOUSE_BUTTON_8
        assertTrue(root.input.mouseClicked(pointer(button = auxiliary), false))
        assertTrue(root.input.mouseDragged(pointer(button = auxiliary), 4.0, 5.0))
        view.close()
        assertEquals(listOf("press:$auxiliary", "drag:4.0:5.0", "cancel", "blur"), events)
        assertFalse(root.input.mouseReleased(pointer(button = auxiliary)))
    }

    @Test fun immediateHostWithoutExplicitViewUpdatesCallbacksAndKeepsIdentity() {
        var parameter = 0
        val calls = mutableListOf<Int>()
        repeat(3) {
            parameter = it
            root.beginFrame()
            val entry = root.nextEntry(bounds, "immediate")
            entry.alwaysDirty = true
            entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), {
                val snapshot = parameter
                Interaction("button") { onClick { calls.add(snapshot); true } }
            })
            entry.emit(UiTree.Scope())
            root.endFrame()
            assertTrue(root.input.mouseClicked(pointer(), false))
        }
        assertEquals(listOf(0, 1, 2), calls)
    }

    @Test fun modalOpenedDuringPressCannotCaptureOrRefocusTheUnderlyingControl() {
        var cancels = 0
        val focus = mutableListOf<Boolean>()
        frame {
            InputRegion("opens-popup", onPress = { _, _ ->
                root.input.cancelPointer()
                root.input.clearFocus()
                true
            }, onDrag = { _, _, _ -> fail("弹窗打开后不能继续拖动原控件") },
                onCancel = { cancels++ }, onText = { fail("弹窗打开后不能给原控件焦点") },
                onBlur = { focus.add(false) })
        }
        assertTrue(root.input.mouseClicked(pointer(), false))
        assertEquals(1, cancels)
        assertFalse(root.input.mouseReleased(pointer()))
        assertFalse(root.input.charTyped(CharacterEvent('x'.code)))
        assertTrue(focus.isEmpty())
    }

    @Test fun movingCompositionToAnotherViewRebindsItsSubscriptions() {
        val other = LuminView()
        val events = mutableListOf<String>()
        val draw: UiScope.() -> Unit = {
            Interaction("editor") {
                onDrag(onCancel = { events.add("cancel") }) {}
                onTextInput { events.add("text"); true }
                onFocusChanged { events.add("focus:$it") }
            }
        }
        try {
            sharedFrame(view to draw)
            assertTrue(root.input.mouseClicked(pointer(), false))
            sharedFrame(other to draw)
            assertEquals(listOf("focus:true", "cancel", "focus:false"), events)
            root.input.within(view) { assertFalse(mouseClicked(pointer(), false)) }
            root.input.within(other) {
                assertTrue(mouseClicked(pointer(), false))
                assertTrue(charTyped(CharacterEvent('x'.code)))
            }
            view.close()
            assertTrue(root.input.charTyped(CharacterEvent('y'.code)))
            assertEquals(listOf("text", "text"), events.takeLast(2))
        } finally { other.close() }
    }

    @Test fun viewportInputUsesTheBuffersPaintOrderAndOuterLayer() {
        val buffer = com.github.epsilon.gui.lib.render.UiContentBuffer(com.github.epsilon.gui.theme.EpsilonUiTheme)
        val events = mutableListOf<String>()
        try {
            frame {
                Viewport(buffer, UiRect(0f, 0f, 60f, 20f), 0f, 0f, 20f) {
                    Above {
                        Interaction("early") { onClick { events.add("early"); true } }
                    }
                    Clip(0f, 0f, 60f, 20f) {
                        Interaction("late") { onClick { events.add("late"); true } }
                    }
                }
                Above {
                    Interaction("overlay", UiRect(30f, 0f, 20f, 20f)) {
                        onClick { events.add("overlay"); true }
                    }
                }
            }
            assertTrue(root.input.mouseClicked(pointer(), false))
            assertTrue(root.input.mouseClicked(pointer(45.0), false))
            assertEquals(listOf("late", "overlay"), events)
        } finally { buffer.close() }
    }

    @Test fun removingFocusAndDragCapabilitiesCancelsBeforeBlur() {
        var active by mutableStateOf(true)
        val events = mutableListOf<String>()
        val draw: UiScope.() -> Unit = {
            Interaction("editor") {
                onClick { true }
                if (active) {
                    onDrag(onCancel = { events.add("cancel") }) {}
                    onTextInput { true }
                    onFocusChanged { if (!it) events.add("blur") }
                }
            }
        }
        frame(block = draw)
        root.input.mouseClicked(pointer(), false)
        active = false
        frame(block = draw)
        assertEquals(listOf("cancel", "blur"), events)
    }
}
