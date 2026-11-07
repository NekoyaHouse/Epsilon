package com.github.epsilon.gui.lib.state

/** 兼容旧导入；通用状态实现位于不依赖 GUI 的 kernel.state。 */
typealias LuminState<T> = com.github.epsilon.kernel.state.State<T>
typealias MutableState<T> = com.github.epsilon.kernel.state.MutableState<T>
fun <T> mutableStateOf(initial: T): MutableState<T> = com.github.epsilon.kernel.state.mutableStateOf(initial)
operator fun <T> LuminState<T>.getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) { this.value = value }
