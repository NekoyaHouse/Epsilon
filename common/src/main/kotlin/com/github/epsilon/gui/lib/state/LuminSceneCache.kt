package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.scene.LayerOffset

/**
 * 场景持有的逐项节点缓存。
 *
 * 由各绘制入口（`LuminUIScene`、`UiContentBuffer.LuminUI`）创建并通过 [UiScope] 暴露给块内代码，
 * 列表项用 `LuminUIItem(key, revision) { }` 自动分割缓存；调用方不需要自己持有缓存对象。
 */
class LuminSceneCache {

    private class Entry(val revision: Long, val nodes: List<UiTree.UiNode>)

    private val entries: HashMap<Any, Entry> = HashMap()

    /**
     * 绘制一个可缓存项；命中时只做节点指针拷贝。
     *
     * @param revision 该项的失效版本；不变即重放缓存
     */
    fun draw(
        key: Any,
        revision: Long,
        scope: UiTree.Scope,
        block: UiScope.() -> Unit,
        metrics: com.github.epsilon.gui.lib.UiTextMetrics,
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

    /** 丢弃不再可见的项。 */
    fun retain(activeKeys: Set<Any>) {
        entries.keys.retainAll(activeKeys)
    }

    fun clear() {
        entries.clear()
    }

    val size: Int get() = entries.size

}
