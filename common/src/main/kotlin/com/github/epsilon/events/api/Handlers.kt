package com.github.epsilon.events.api

import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.bus.listeners.ConsumerListener
import com.github.epsilon.events.bus.listeners.IListener
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.runSafe

/** 默认监听优先级，与 [EventPriority.MEDIUM] 一致。 */
const val DEFAULT_HANDLER_PRIORITY: Int = EventPriority.MEDIUM

/**
 * 以 lambda 形式订阅 [E] 事件，返回可用于 [EventBus.unsubscribe] 的监听器。
 *
 * 与 `@EventHandler` 注解扫描相比，这里的 listener 在 [subscribe] 时立即生效，
 * 因此 Kotlin 侧通常在模块启用时订阅、禁用时注销：
 *
 * ```kotlin
 * private var tickHandler: IListener? = null
 *
 * override fun onEnable() {
 *     tickHandler = EventBus.INSTANCE.handler<ClientTickEvent.Pre> { ... }
 * }
 *
 * override fun onDisable() {
 *     tickHandler?.let(EventBus.INSTANCE::unsubscribe)
 *     tickHandler = null
 * }
 * ```
 */
inline fun <reified E : Any> EventBus.handler(
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    noinline function: (E) -> Unit,
): IListener = handler(E::class.java, priority, function)

/**
 * [handler] 的显式 class 版本，供无法使用 reified 类型参数的场景调用。
 */
fun <E : Any> EventBus.handler(
    eventClass: Class<E>,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    function: (E) -> Unit,
): IListener = ConsumerListener(eventClass, priority, function).also { subscribe(it) }

/**
 * 以 lambda 形式订阅 [E] 事件，并把 [NonNullContext] 作为上下文参数注入 lambda。
 *
 * 世界、玩家、交互管理器或连接任一缺失时直接跳过，不会进入 lambda；因此处理器内不需要再做 null 检查：
 *
 * ```kotlin
 * EventBus.INSTANCE.nonNullHandler<PlayerTickEvent.Post> {
 *     player.jumpFromGround()
 * }
 * ```
 *
 * [NonNullContext] 提供 `player`/`world`/`interaction`/`netHandler` 等上下文属性，
 * 在其它包中需要导入对应属性（或 `com.github.epsilon.utils.*`），也可以改用 `contextOf<NonNullContext>()`。
 */
inline fun <reified E : Any> EventBus.nonNullHandler(
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    noinline function: context(NonNullContext) (E) -> Unit,
): IListener = nonNullHandler(E::class.java, priority, function)

/**
 * [nonNullHandler] 的显式 class 版本，供无法使用 reified 类型参数的场景调用。
 */
fun <E : Any> EventBus.nonNullHandler(
    eventClass: Class<E>,
    priority: Int = DEFAULT_HANDLER_PRIORITY,
    function: context(NonNullContext) (E) -> Unit,
): IListener = handler(eventClass, priority) { event -> runSafe { function(event) } }
