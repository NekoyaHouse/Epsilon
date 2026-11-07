package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.scene.LayerOffset

/**
 * 逐行节点缓存。
 *
 * 用于「嵌在 viewport / scissor 捕获里、又希望逐行复用」的列表内容：每行独立捕获一次节点，
 * 只有 [revision] 变化时才重新捕获，其余帧只把缓存节点指针拷回当前作用域。
 *
 * 与 [LuminComposition] 的分工：composition 用于场景级（外层）缓存；本类用于已经处在
 * `UiTree.Scope.capture` 之内的嵌套内容，避免两套捕获互相干扰。
 */
class LuminRowCache {

    private class Entry(val revision: Long, val nodes: List<UiTree.UiNode>)

    private val entries: HashMap<Any, Entry> = HashMap()

    /**
     * 绘制一行；命中缓存时直接重放节点。
     *
     * @param revision 该行的失效版本，变化即重新捕获（通常是设置值 + 动画进度的组合）
     */
    fun draw(
        key: Any,
        revision: Long,
        scope: UiTree.Scope,
        metrics: UiTextMetrics,
        block: UiScope.() -> Unit,
    ) {
        val cached = entries[key]
        if (cached != null && cached.revision == revision) {
            scope.appendNodes(cached.nodes)
            return
        }

        val session = scope.openCapture()
        val nodes: List<UiTree.UiNode>
        try {
            val origin: UiRect = scope.bound()
            block(UiScope(scope, metrics, LayerOffset.Same, origin))
            nodes = session.flush().nodes
        } finally {
            session.close()
        }
        entries[key] = Entry(revision, nodes)
        scope.appendNodes(nodes)
    }

    /** 丢弃不再可见的行，避免列表切换后残留。 */
    fun retain(activeKeys: Set<Any>) {
        entries.keys.retainAll(activeKeys)
    }

    fun clear() {
        entries.clear()
    }

    /** 当前缓存的行数，便于调试。 */
    val size: Int get() = entries.size

}
