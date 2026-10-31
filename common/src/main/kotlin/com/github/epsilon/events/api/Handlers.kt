package com.github.epsilon.events.api

import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.bus.listeners.ConsumerListener
import com.github.epsilon.events.bus.listeners.IListener
import com.github.epsilon.utils.safety.*

/** 默认监听优先级，与 [EventPriority.MEDIUM] 一致。 */
const val DEFAULT_HANDLER_PRIORITY: Int = EventPriority.MEDIUM

/**
 * 为 [owner] 注册 [E] 事件的 lambda 监听器。
 *
 * [alwaysListening] 为 false（默认）时，监听器绑定到 [owner]，是否生效由 [EventBus.subscribe] /
 * [EventBus.unsubscribe] 决定（模块生命周期即启用时订阅、禁用时注销）；为 true 时立即订阅并一直监听，
 * 不随 owner 的订阅状态变化，适用于 Manager 等常驻监听器。
 *
 * ```kotlin
 * // 模块作用域内有现成的 Module.nonNullListener / Module.handler 封装该用法
 * EventBus.nonNullHandler(this) { ... }
 *
 * // 常驻监听（不会被 unsubscribe(owner) 注销）
 * EventBus.nonNullHandler(this, alwaysListening = true) {
 *     player.jumpFromGround()
 * }
 * ```
 */
inline fun <reified E : Any> EventBus.handler(
    owner: Any,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    noinline function: (E) -> Unit,
): IListener = handler(owner, E::class.java, priority, alwaysListening, function)

/** [handler] 的显式 class 版本，供无法使用 reified 类型参数的场景调用。 */
fun <E : Any> EventBus.handler(
    owner: Any,
    eventClass: Class<E>,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    function: (E) -> Unit,
): IListener {
    val listener = ConsumerListener(eventClass, priority, function)
    if (alwaysListening) {
        subscribe(listener)
    } else {
        attach(owner, listener)
    }
    return listener
}

/**
 * [handler] 的上下文版本：世界、玩家、交互管理器或连接任一缺失时直接跳过，不会进入 lambda，
 * 因此处理器内不需要再做 null 检查。
 *
 * [NonNullContext] 提供 `player`/`level`/`interaction`/`connection` 等上下文属性，
 * 其它包使用时一律 `import com.github.epsilon.utils.safety.*`，不得写成 `nn.player` 这类显式访问。
 */
inline fun <reified E : Any> EventBus.nonNullHandler(
    owner: Any,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    noinline function: context(NonNullContext) (E) -> Unit,
): IListener = nonNullHandler(owner, E::class.java, priority, alwaysListening, function)

/** [nonNullHandler] 的显式 class 版本，供无法使用 reified 类型参数的场景调用。 */
fun <E : Any> EventBus.nonNullHandler(
    owner: Any,
    eventClass: Class<E>,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    alwaysListening: Boolean = false,
    function: context(NonNullContext) (E) -> Unit,
): IListener = handler(owner, eventClass, priority, alwaysListening) { event -> runSafe { function(event) } }
