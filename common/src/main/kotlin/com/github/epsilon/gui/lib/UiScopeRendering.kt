package com.github.epsilon.gui.lib

import com.github.epsilon.graphics.schedulers.render2d.Render2DProperties
import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.gui.lib.render.UiPhaseValue
import com.github.epsilon.gui.lib.render.UiRenderNode
import java.awt.Color

/** 读取只使变换阶段失效；变换围绕当前作用域原点应用。 */
fun UiScope.Transform(value: () -> Render2DTransform, block: UiScope.() -> Unit) {
    property({ Render2DProperties(transform = value()) }, null, block)
}

/** 逐图元乘色，不表示将重叠子树离屏合成后统一设置透明度。 */
fun UiScope.Paint(value: () -> Color, block: UiScope.() -> Unit) {
    property({ val c = value(); Render2DProperties(red = c.red / 255f, green = c.green / 255f, blue = c.blue / 255f, alpha = c.alpha / 255f) }, null, block)
}

/** 裁剪读取不使组合或几何失效；矩形相对当前作用域。 */
fun UiScope.Clip(value: () -> UiRect, block: UiScope.() -> Unit) {
    property({ Render2DProperties.DEFAULT }, value, block)
}

private fun UiScope.property(properties: () -> Render2DProperties, clip: (() -> UiRect)?, block: UiScope.() -> Unit) {
    val capture = tree.openCapture()
    val children = try { block(); capture.flush().nodes } catch (failure: Throwable) {
        UiRenderNode.release(capture.flush().nodes); throw failure
    } finally { capture.close() }
    val phase = UiPhaseValue({ UiTree.PropertyValue(properties(), clip?.invoke()) })
    tree.appendNodes(listOf(UiTree.PropertyNode(origin, phase, children)))
}

/** 仅观察绘制几何依赖；此块用于图元，不在其中声明组合、输入或帧副作用。 */
fun UiScope.CacheDraw(block: UiScope.() -> Unit) {
    val phase = UiPhaseValue({
        val output = UiTree.Scope()
        try {
            output.pushAbsolute(origin) { block(UiScope(output, metrics, layer, origin)) }
            output.snapshot().nodes.also { validateGeometry(it) }
        } catch (failure: Throwable) { UiRenderNode.release(output.snapshot().nodes); throw failure }
    }, UiRenderNode::release)
    tree.appendNodes(listOf(UiTree.GeometryNode(phase)))
}

private fun validateGeometry(nodes: List<UiTree.UiNode>) {
    for (node in nodes) when (node) {
        is UiTree.CompositionNode, is UiTree.InteractionNode, is UiTree.ViewportNode -> error("CacheDraw 只允许图元和绘制容器")
        is UiTree.LayerNode -> validateGeometry(node.children)
        is UiTree.ScissorNode -> validateGeometry(node.children)
        is UiTree.PropertyNode -> validateGeometry(node.children)
        else -> Unit
    }
}
