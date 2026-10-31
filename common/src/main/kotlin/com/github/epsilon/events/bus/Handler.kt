package com.github.epsilon.events.bus

/**
 * 显式事件处理器：固定 owner、事件类型、优先级和回调。
 *
 * Handler 由 [EventBus] 统一管理，不使用注解扫描或反射方法绑定。
 */
class Handler<T : Any>(
    val owner: Any,
    val eventClass: Class<T>,
    val priority: Int,
    private val function: (T) -> Unit,
) {

    fun call(event: Any) {
        function(eventClass.cast(event))
    }

}
