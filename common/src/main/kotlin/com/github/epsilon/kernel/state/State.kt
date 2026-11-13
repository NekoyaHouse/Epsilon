package com.github.epsilon.kernel.state

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KProperty

interface State<out T> {
    val value: T
}

interface MutableState<T> : State<T> {
    override var value: T
}

/** 通知只在观察宿主线程排空；写入线程不访问 UI 的订阅图。 */
object StateNotifications {
    private val pending = ConcurrentLinkedQueue<() -> Unit>()

    internal fun enqueue(action: () -> Unit) {
        pending.add(action)
    }

    fun drain() {
        while (true) (pending.poll() ?: return).invoke()
    }
}

internal class StateSource(
    private val onObserved: () -> Unit = {},
    private val onUnobserved: () -> Unit = {},
) {
    var version = 0L
        private set
    private val listeners = LinkedHashSet<() -> Unit>()
    private var queued = false

    @Synchronized
    fun read() {
        StateTracking.record(this, version)
    }

    @Synchronized
    fun version(): Long = version

    @Synchronized
    fun isObserved(): Boolean = listeners.isNotEmpty()

    @Synchronized
    fun subscribe(listener: () -> Unit) {
        val first = listeners.isEmpty()
        if (listeners.add(listener) && first) onObserved()
    }

    @Synchronized
    fun unsubscribe(listener: () -> Unit) {
        if (listeners.remove(listener) && listeners.isEmpty()) onUnobserved()
    }

    @Synchronized
    fun changed() {
        version++
        if (queued) return
        queued = true
        StateNotifications.enqueue {
            val callbacks = synchronized(this) {
                queued = false
                listeners.toList()
            }
            callbacks.forEach { it() }
        }
    }
}

/** 每次观察先收集，再提交依赖；异常不会破坏上一轮订阅。 */
class StateObservation(private val onChanged: () -> Unit) : AutoCloseable {
    private var dependencies = emptyMap<StateSource, Long>()
    private var subscriptions = emptySet<StateSource>()
    private val listener: () -> Unit = {
        if (dependencies.any { (source, version) -> source.version() != version }) onChanged()
    }

    internal fun isCurrent(): Boolean = dependencies.all { (source, version) -> source.version() == version }

    fun <T> observeReads(block: () -> T): T = observeReads(true, block)

    internal fun <T> observeReads(subscribe: Boolean, block: () -> T): T {
        val reads = IdentityHashMap<StateSource, Long>()
        val result = StateTracking.collect(reads, block)
        // 成功收集后不再修改 reads，订阅可直接复用其 key 视图，无需再复制一份哈希集合。
        val nextSubscriptions = if (subscribe) reads.keys else emptySet()
        for (source in subscriptions) if (source !in nextSubscriptions) source.unsubscribe(listener)
        for (source in nextSubscriptions) if (source !in subscriptions) source.subscribe(listener)
        subscriptions = nextSubscriptions
        dependencies = reads
        if (reads.any { (source, version) -> source.version() != version }) onChanged()
        return result
    }

    internal fun activate() {
        for (source in dependencies.keys) if (source !in subscriptions) source.subscribe(listener)
        subscriptions = dependencies.keys
        if (!isCurrent()) onChanged()
    }

    internal fun suspendSubscriptions() {
        subscriptions.forEach { it.unsubscribe(listener) }
        subscriptions = emptySet()
    }

    override fun close() {
        suspendSubscriptions()
        dependencies = emptyMap()
    }
}

object StateTracking {
    private val reads = ThreadLocal<MutableMap<StateSource, Long>?>()
    private val building = ThreadLocal.withInitial { false }

    internal fun record(source: StateSource, version: Long) {
        reads.get()?.putIfAbsent(source, version)
    }

    internal fun <T> collect(target: MutableMap<StateSource, Long>, block: () -> T): T {
        val previous = reads.get()
        reads.set(target)
        return try {
            block()
        } finally {
            reads.set(previous)
        }
    }

    fun <T> untracked(block: () -> T): T {
        val previous = reads.get()
        reads.set(null)
        return try {
            block()
        } finally {
            reads.set(previous)
        }
    }

    fun <T> buildingUi(block: () -> T): T {
        val previous = building.get()
        building.set(true)
        return try {
            block()
        } finally {
            building.set(previous)
        }
    }

    val isBuildingUi: Boolean get() = building.get()

    internal fun checkWrite() {
        check(!building.get()) { "UI 构建期间不能写入业务状态，请在输入回调或提交阶段修改" }
    }
}

private class StateCell<T>(initial: T) : MutableState<T> {
    private val source = StateSource()
    private var current = initial

    override var value: T
        @Synchronized get() {
            source.read()
            return current
        }
        @Synchronized set(value) {
            if (current == value) return
            StateTracking.checkWrite()
            current = value
            source.changed()
        }
}

private class DerivedState<T>(private val calculation: () -> T) : State<T>, AutoCloseable {
    private val source = StateSource(
        onObserved = { observation.activate() },
        onUnobserved = { observation.suspendSubscriptions() },
    )
    private var initialized = false
    private var current: Any? = null
    private val observation = StateObservation { update() }

    private fun update() {
        val next = observation.observeReads(source.isObserved(), calculation)
        if (!initialized || current != next) {
            current = next
            initialized = true
            source.changed()
        }
    }

    override val value: T
        get() {
            if (!initialized || !observation.isCurrent()) update()
            source.read()
            @Suppress("UNCHECKED_CAST")
            return current as T
        }

    override fun close() = observation.close()
}

fun <T> mutableStateOf(initial: T): MutableState<T> = StateCell(initial)

fun <T> derivedStateOf(calculation: () -> T): State<T> = DerivedState(calculation)

fun <T> State<T>.readUntracked(): T = StateTracking.untracked { value }

operator fun <T> State<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}
