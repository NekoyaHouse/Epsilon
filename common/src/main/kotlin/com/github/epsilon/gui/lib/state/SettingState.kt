package com.github.epsilon.gui.lib.state

import com.github.epsilon.settings.Setting

/** 直接访问真实 getter，保留子类的有效值约束，不维护第二份缓存。 */
fun <V> Setting<V>.asState(): MutableState<V> = object : MutableState<V> {
    override var value: V
        get() = this@asState.value
        set(value) {
            this@asState.value = value
        }
}
