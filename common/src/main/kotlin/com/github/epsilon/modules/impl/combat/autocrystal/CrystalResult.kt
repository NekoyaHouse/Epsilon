package com.github.epsilon.modules.impl.combat.autocrystal

import java.util.concurrent.atomic.AtomicReference

/** 计算线程立即发布不可变结果；失效令牌与结果一起原子更新，防止清理后旧任务回写。 */
internal class CrystalResult<T> {
    private data class State<T>(val token: Any, val value: T?)

    private val state = AtomicReference(State<T>(Any(), null))

    val value: T? get() = state.get().value
    fun token(): Any = state.get().token

    fun publish(token: Any, value: T?): Boolean {
        while (true) {
            val previous = state.get()
            if (previous.token !== token) return false
            if (state.compareAndSet(previous, State(token, value))) return true
        }
    }

    fun replace(value: T? = null) {
        state.set(State(Any(), value))
    }
}
