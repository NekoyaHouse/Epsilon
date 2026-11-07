package com.github.epsilon.gui.lib

import com.github.epsilon.gui.lib.input.UiInputSubscription
import com.github.epsilon.gui.lib.state.LuminRuntime
import com.github.epsilon.gui.lib.state.LuminView
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent

/** 指针位置同时提供屏幕坐标与相对交互区域的坐标；拖动增量沿用宿主事件。 */
data class UiPointerEvent(
    val source: MouseButtonEvent,
    val bounds: UiRect,
    val doubleClick: Boolean = false,
    val deltaX: Double = 0.0,
    val deltaY: Double = 0.0,
    internal val mappedLocal: Pair<Double, Double>? = null,
    internal val declarationSource: MouseButtonEvent? = null,
) {
    val x: Double get() = source.x()
    val y: Double get() = source.y()
    val localX: Double get() = mappedLocal?.first ?: (x - bounds.x)
    val localY: Double get() = mappedLocal?.second ?: (y - bounds.y)
}

/** 回调只在宿主输入阶段执行。返回 true 的点击消费事件，false 继续向下命中。 */
class UiInteraction internal constructor() {
    var focusable: Boolean = false
    var autoFocus: Boolean = false
    internal val clicks = LinkedHashMap<Int, (UiPointerEvent) -> Boolean>()
    internal val drags = LinkedHashMap<Int, UiDragSubscription>()
    internal var focus: ((Boolean) -> Unit)? = null
    internal var key: ((KeyEvent) -> Boolean)? = null
    internal var text: ((CharacterEvent) -> Boolean)? = null
    internal var preedit: ((PreeditEvent?) -> Boolean)? = null
    internal var scroll: ((Double, Double, Double, Double) -> Boolean)? = null
    internal var previewClick: ((UiPointerEvent) -> Boolean)? = null

    /** 聚焦控件优先读取按下事件，供快捷键录制捕获区域外鼠标键。 */
    fun onPreviewClick(handler: (UiPointerEvent) -> Boolean) { focusable = true; previewClick = handler }

    fun onClick(button: Int = InputConstants.MOUSE_BUTTON_LEFT, handler: (UiPointerEvent) -> Boolean) {
        check(clicks.putIfAbsent(button, handler) == null) { "同一交互节点重复订阅按键: $button" }
    }

    /** 按下时开始捕获；区域外仍收到拖动与同一按键的释放，节点消失时收到取消。 */
    fun onDrag(
        button: Int = InputConstants.MOUSE_BUTTON_LEFT,
        onStart: (UiPointerEvent) -> Boolean = { true },
        onEnd: (UiPointerEvent) -> Unit = {},
        onCancel: () -> Unit = {},
        handler: (UiPointerEvent) -> Unit,
    ) {
        check(drags.putIfAbsent(button, UiDragSubscription(button, onStart, handler, onEnd, onCancel)) == null)
    }

    fun onFocusChanged(handler: (Boolean) -> Unit) { focus = handler }
    fun onKeyPressed(handler: (KeyEvent) -> Boolean) { focusable = true; key = handler }
    fun onTextInput(handler: (CharacterEvent) -> Boolean) { focusable = true; text = handler }
    fun onPreedit(handler: (PreeditEvent?) -> Boolean) { focusable = true; preedit = handler }
    fun onScroll(handler: (Double, Double, Double, Double) -> Boolean) { scroll = handler }
}

/** 给持有编辑模型的控件声明输入订阅，区域相对当前作用域。 */
fun UiScope.InputRegion(
    key: Any,
    bounds: UiRect = size,
    onPress: ((MouseButtonEvent, Boolean) -> Boolean)? = null,
    onPreviewPress: ((MouseButtonEvent, Boolean) -> Boolean)? = null,
    onDrag: ((MouseButtonEvent, Double, Double) -> Boolean)? = null,
    onRelease: ((MouseButtonEvent) -> Boolean)? = null,
    onCancel: () -> Unit = {},
    onKey: ((KeyEvent) -> Boolean)? = null,
    onText: ((CharacterEvent) -> Boolean)? = null,
    onPreedit: ((PreeditEvent?) -> Boolean)? = null,
    onBlur: () -> Unit = {},
    onScroll: ((Double, Double, Double, Double) -> Boolean)? = null,
    autoFocus: Boolean = false,
    screenCoordinates: Boolean = true,
) {
    fun UiPointerEvent.event() = if (screenCoordinates) source else declarationSource ?: source
    Interaction(key, bounds) {
        this.autoFocus = autoFocus
        onPreviewPress?.let { handler -> onPreviewClick { handler(it.event(), it.doubleClick) } }
        if (onPress != null) for (button in InputConstants.MOUSE_BUTTON_LEFT..InputConstants.MOUSE_BUTTON_8) {
            if (onDrag != null || onRelease != null) {
                onDrag(button, onStart = { onPress(it.event(), it.doubleClick) },
                    onEnd = { onRelease?.invoke(it.event()) }, onCancel = onCancel) {
                    onDrag?.invoke(it.event(), it.deltaX, it.deltaY)
                }
            } else onClick(button) { onPress(it.event(), it.doubleClick) }
        }
        onKey?.let { onKeyPressed(it) }
        onText?.let { onTextInput(it) }
        onPreedit?.let { onPreedit(it) }
        onScroll?.let { onScroll(it) }
        onFocusChanged { if (!it) onBlur() }
    }
}

internal data class UiDragSubscription(
    val button: Int,
    val start: (UiPointerEvent) -> Boolean,
    val update: (UiPointerEvent) -> Unit,
    val end: (UiPointerEvent) -> Unit,
    val cancel: () -> Unit,
)

/**
 * 声明当前组合的输入订阅，区域相对当前作用域。key 只表示身份，不能包含几何或鼠标。
 * 订阅随绘制树重放，成功提交后更新回调，移出组合自动释放焦点与指针捕获。
 */
fun UiScope.Interaction(
    key: Any,
    bounds: UiRect = size,
    enabled: Boolean = true,
    block: UiInteraction.() -> Unit,
) {
    val composition = checkNotNull(LuminRuntime.currentComposition) { "Interaction 必须在 LuminUI 组合中声明" }
    val view = checkNotNull(composition.view) { "Interaction 需要 LuminView 宿主" }
    if (!enabled) return
    val router = view.input
    val subscription = composition.remember(InputKey(key, view)) { UiInputSubscription(router, key, view) }
    val handlers = UiInteraction().apply(block)
    tree.appendNodes(listOf(UiTree.InteractionNode(
        subscription, absolute(bounds.x, bounds.y, bounds.width, bounds.height), handlers,
    )))
}

private data class InputKey(val key: Any, val owner: LuminView)
