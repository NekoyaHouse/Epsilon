package com.github.epsilon.gui.lib.state

import kotlin.reflect.KProperty

/**
 * recompose 可订阅状态的基类。
 *
 * 实现方负责：读取时登记依赖（[LuminRuntime.recordRead]），写入时让订阅者失效。
 * 内置实现有 [MutableState]（普通状态）与 Setting 适配器（写回设置项）。
 */
abstract class LuminState<T> internal constructor() {

    abstract var value: T

    internal abstract fun subscribe(composition: LuminComposition)

    internal abstract fun unsubscribe(composition: LuminComposition)

    internal abstract fun invalidateSubscribers()

    /** 不登记依赖地读取。 */
    abstract fun readUntracked(): T

}

/**
 * 普通可订阅状态，等价于 Compose 的 `mutableStateOf`。
 *
 * - 写入用 `==` 判等，值没变不触发任何重建；
 * - 读取只在 recompose 期间登记依赖，立即模式下是纯读取。
 */
class MutableState<T> internal constructor(initial: T) : LuminState<T>() {

    private var current: T = initial

    private val subscribers: MutableList<LuminComposition> = ArrayList(2)

    override var value: T
        get() {
            LuminRuntime.recordRead(this)
            return current
        }
        set(newValue) {
            if (current != newValue) {
                current = newValue
                LuminRuntime.invalidate(this)
            }
        }

    override fun readUntracked(): T = current

    internal override fun subscribe(composition: LuminComposition) {
        if (!subscribers.contains(composition)) {
            subscribers.add(composition)
        }
    }

    internal override fun unsubscribe(composition: LuminComposition) {
        subscribers.remove(composition)
    }

    internal override fun invalidateSubscribers() {
        for (index in subscribers.indices.reversed()) {
            subscribers[index].markDirty()
        }
    }

}

/** 创建一个用 `==` 判等的状态。 */
fun <T> mutableStateOf(initial: T): MutableState<T> = MutableState(initial)

/** 属性委托：`var hover by mutableStateOf(0f)`。 */
operator fun <T> LuminState<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T> LuminState<T>.setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
    value = newValue
}
