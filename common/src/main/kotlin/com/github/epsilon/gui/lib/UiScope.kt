package com.github.epsilon.gui.lib

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.state.LuminRuntime
import com.github.epsilon.utils.render.animation.Animation
import kotlin.math.max

/**
 * Compose 风格的 UI 作用域。
 *
 * 作用域就是扩展函数的接收者：它携带文本度量、累积 layer 和当前绘制矩形，
 * 因此图元签名里不再需要 `layer` / `scope` / `textRenderer` 参数。
 *
 * 坐标一律是**相对当前作用域原点**的；绝对定位的旧入口（`pushAbsolute`）不再提供。
 * 不变量：[origin] 必须与底层 [UiTree.Scope] 当前的 bound 一致，这样坐标解析与
 * [size] / 布局计算使用的才是同一套坐标系。派生作用域只能通过 [Positioned] / [Box]
 * / [ColumnScope.Item]，它们会同时推进底层 bound 与 [origin]。
 */
open class UiScope internal constructor(
    internal val tree: UiTree.Scope,
    /** 文本度量；由宿主提供，控件只依赖该接口。 */
    val metrics: UiTextMetrics,
    /** 相对根作用域累积后的 layer。 */
    val layer: LayerOffset,
    /** 当前绘制矩形；图元坐标相对它解析。 */
    val origin: UiRect,
) {

    /** 当前可用尺寸，等价于 Compose 的 `size`。 */
    val size: UiRect get() = UiRect(0.0f, 0.0f, origin.width, origin.height)

    /** 当前可用宽度。 */
    val width: Float get() = origin.width

    /** 当前可用高度。 */
    val height: Float get() = origin.height

    /**
     * 派生一个子作用域。
     *
     * 只改变作用域携带的矩形与 layer，不改变节点收集范围；调用方必须自己把底层 bound
     * 推到同一个矩形上（见 [Positioned] / [Box] / [ColumnScope.Item]）。
     */
    internal fun derive(origin: UiRect, layer: LayerOffset = this.layer): UiScope =
        UiScope(tree, metrics, layer, origin)

    /** 把局部矩形解析成绝对坐标。 */
    internal fun absolute(x: Float, y: Float, width: Float, height: Float): UiRect =
        UiRect(origin.x + x, origin.y + y, width, height)

}

/**
 * 列作用域，等价于 Compose 的 `ColumnScope`。
 *
 * 只有 [Column] 的内容才能拿到槽位 API，避免任意作用域里出现半套布局。
 */
class ColumnScope internal constructor(
    tree: UiTree.Scope,
    metrics: UiTextMetrics,
    layer: LayerOffset,
    origin: UiRect,
    private val gap: Float,
) : UiScope(tree, metrics, layer, origin) {

    private var cursor = 0.0f

    private var used = 0.0f

    /** 已占用的高度，含间距。 */
    val contentHeight: Float get() = used

    /** 固定高度槽位。 */
    fun Item(height: Float, block: UiScope.() -> Unit) {
        val rect = absolute(0.0f, cursor, width, height)
        tree.pushAbsolute(rect) { block(derive(rect)) }
        cursor += height + gap
        used = cursor
    }

    /** 吃掉剩余高度，等价于 Compose 的 `Modifier.weight(1f)`。 */
    fun Fill(block: UiScope.() -> Unit) {
        Item(max(0.0f, height - cursor), block)
    }

    /** 跳过一段高度。 */
    fun Spacer(height: Float) {
        cursor += height + gap
        used = cursor
    }

}

/**
 * 唯一的 layer 原语。
 *
 * 子树被捕获成一个 [UiTree.LayerNode]，偏移相对父作用域；[LayerOffset.Same] 直接内联，
 * 不产生空节点，保持与旧 `layer == 0` 快路径一致的开销。
 */
fun UiScope.Layer(offset: LayerOffset, block: UiScope.() -> Unit) {
    if (offset == LayerOffset.Same) {
        block()
        return
    }
    tree.layer(offset) { block(derive(origin, layer + offset)) }
}

/** 相对父层 +1。 */
fun UiScope.Above(block: UiScope.() -> Unit) {
    Layer(LayerOffset.Edge, block)
}

/** 相对父层 -1。 */
fun UiScope.Below(block: UiScope.() -> Unit) {
    Layer(-LayerOffset.Edge, block)
}

/** 在当前作用域内缩出一块子作用域。 */
fun UiScope.Box(insets: UiTree.Insets, block: UiScope.() -> Unit) {
    val target = insets.apply(origin)
    tree.pushAbsolute(target) { block(derive(target)) }
}

/**
 * 在相对当前作用域的位置上开辟固定矩形子树。
 *
 * 这是唯一的定位入口：坐标相对 [UiScope.origin]，不允许传屏幕绝对坐标。
 */
fun UiScope.Positioned(x: Float, y: Float, width: Float, height: Float, block: UiScope.() -> Unit) {
    val target = absolute(x, y, width, height)
    tree.pushAbsolute(target) { block(derive(target)) }
}

/** 裁剪子树，坐标相对当前作用域。 */
fun UiScope.Clip(x: Float, y: Float, width: Float, height: Float, block: UiScope.() -> Unit) {
    tree.scissor(UiRect(x, y, width, height)) { block(this) }
}

/** 按条件裁剪，避免调用方各自写 if/else。 */
fun UiScope.ClipIf(required: Boolean, x: Float, y: Float, width: Float, height: Float, block: UiScope.() -> Unit) {
    if (required) {
        Clip(x, y, width, height, block)
    } else {
        block()
    }
}

/** 垂直布局。 */
fun UiScope.Column(gap: Float, block: ColumnScope.() -> Unit) {
    block(ColumnScope(tree, metrics, layer, origin, gap))
}

/**
 * 垂直槽位栈，等价于旧的 `UiTree.Scope.Stack`，但坐标是相对当前作用域的。
 *
 * 适合「逐行往下摆、但每行高度由外部决定」的列表；需要吃剩余高度时用 [Column] + `Fill {}`。
 */
class StackScope internal constructor(
    private val parent: UiScope,
    private val bounds: UiRect,
) {

    private var cursor = bounds.y

    /** 栈的矩形（相对父作用域）。 */
    fun bounds(): UiRect = bounds

    /** 当前游标（相对父作用域）。 */
    fun cursor(): Float = cursor

    /** 取一个固定高度槽位。 */
    fun Item(height: Float, block: UiScope.() -> Unit) {
        val itemHeight = max(0.0f, height)
        parent.Positioned(bounds.x, cursor, bounds.width, itemHeight, block)
        cursor += itemHeight
    }

    /** 推进游标。 */
    fun Gap(gap: Float) {
        cursor += max(0.0f, gap)
    }

}

/** 在当前作用域内开一个垂直槽位栈，坐标相对当前作用域。 */
fun UiScope.Stack(x: Float, y: Float, width: Float, height: Float): StackScope =
    StackScope(this, UiRect(x, y, width, height))

/** 推进动画并返回当前进度（返回值的工具函数按 Compose 规范保持小写）。 */
fun UiScope.animate(animation: Animation, target: Boolean): Float {
    LuminRuntime.requireImmediateAnimation("animate")
    return tree.animate(animation, target)
}

/** 推进动画并返回当前进度。 */
fun UiScope.animate(animation: Animation, target: Float): Float {
    LuminRuntime.requireImmediateAnimation("animate")
    return tree.animate(animation, target)
}

/**
 * 从既有的 [UiTree.Scope] 派生 Compose 风格作用域。
 *
 * 迁移期桥接用：作用域的 origin 直接取底层当前 bound，保证两套坐标系一致。
 */
fun UiTree.Scope.asUiScope(metrics: UiTextMetrics, layer: LayerOffset = LayerOffset.Same): UiScope =
    UiScope(this, metrics, layer, bound())

/** 以 [TextRenderer] 为度量的桥接重载。 */
fun UiTree.Scope.asUiScope(renderer: TextRenderer, layer: LayerOffset = LayerOffset.Same): UiScope =
    asUiScope(renderer.asUiTextMetrics(), layer)

/** 把 [TextRenderer] 适配成库内最小度量接口。 */
fun TextRenderer.asUiTextMetrics(): UiTextMetrics = TextRendererMetrics(this)

private class TextRendererMetrics(private val renderer: TextRenderer) : UiTextMetrics {

    override fun textWidth(text: String, scale: Float): Float = renderer.getWidth(text, scale)

    override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float =
        renderer.getWidth(text, scale, fontLoader)

    override fun textHeight(scale: Float): Float = renderer.getHeight(scale)

    override fun textHeight(scale: Float, fontLoader: TtfFontLoader): Float =
        renderer.getHeight(scale, fontLoader)

}