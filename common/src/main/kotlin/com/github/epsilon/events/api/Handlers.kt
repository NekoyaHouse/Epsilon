package com.github.epsilon.events.api

import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.bus.listeners.ConsumerListener
import com.github.epsilon.events.bus.listeners.IListener
import com.github.epsilon.utils.safety.*

/** 默认监听优先级，与 [EventPriority.MEDIUM] 一致。 */
const val DEFAULT_HANDLER_PRIORITY: Int = EventPriority.MEDIUM

/**
 * 为当前对象注册 [E] 事件的 lambda 监听器。
 *
 * 隐式 receiver 就是监听器的 owner：默认（[alwaysListening] = false）绑定到该对象，
 * 是否生效由 [EventBus.subscribe] / [EventBus.unsubscribe] 决定（Module 即启用时订阅、禁用时注销）；
 * [alwaysListening] = true 时立即订阅并一直监听，不随 owner 的订阅状态变化。
 *
 * ```kotlin
 * init {
 *     handler<ClientTickEvent.Pre> {
 *         onClientTick()
 *     }
 * }
 * ```
 */
inline fun <reified E : Any> Any.handler(
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    noinline function: (E) -> Unit,
): IListener = handler(this, E::class.java, priority, alwaysListening, function)

/** [handler] 的显式 owner/class 版本，供无法使用 reified 类型参数的场景调用。 */
fun <E : Any> handler(
    owner: Any,
    eventClass: Class<E>,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    function: (E) -> Unit,
): IListener {
    val listener = ConsumerListener(eventClass, priority, function)
    if (alwaysListening) {
        EventBus.subscribe(listener)
    } else {
        EventBus.attach(owner, listener)
    }
    return listener
}

/**
 * [handler] 的上下文版本：世界、玩家、交互管理器或连接任一缺失时直接跳过，不会进入 lambda，
 * 因此处理器内不需要再做 null 检查。用法与 [handler] 一致，隐式 receiver 即 owner。
 *
 * [NonNullContext] 提供 `player`/`level`/`interaction`/`connection` 等上下文属性，
 * 其它包使用时一律 `import com.github.epsilon.utils.safety.*`，不得写成 `nn.player` 这类显式访问。
 *
 * ```kotlin
 * init {
 *     nonNullHandler<PlayerTickEvent.Post> {
 *         player.jumpFromGround()
 *     }
 * }
 * ```
 */
inline fun <reified E : Any> Any.nonNullHandler(
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    noinline function: context(NonNullContext) (E) -> Unit,
): IListener = nonNullHandler(this, E::class.java, priority, alwaysListening, function)

/** [nonNullHandler] 的显式 owner/class 版本，供无法使用 reified 类型参数的场景调用。 */
fun <E : Any> nonNullHandler(
    owner: Any,
    eventClass: Class<E>,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    function: context(NonNullContext) (E) -> Unit,
): IListener = handler(owner, eventClass, priority, alwaysListening) { event -> runSafe { function(event) } }
