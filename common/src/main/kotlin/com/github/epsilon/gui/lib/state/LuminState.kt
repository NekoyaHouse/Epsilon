package com.github.epsilon.gui.lib.state

import com.github.epsilon.kernel.state.MutableState as KernelMutableState
import com.github.epsilon.kernel.state.State
import com.github.epsilon.kernel.state.mutableStateOf as kernelMutableStateOf
import kotlin.reflect.KProperty

/** 兼容旧导入；通用状态实现位于不依赖 GUI 的 kernel.state。 */
typealias LuminState<T> = State<T>
typealias MutableState<T> = KernelMutableState<T>
fun <T> mutableStateOf(initial: T): MutableState<T> = kernelMutableStateOf(initial)
operator fun <T> LuminState<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }
