package com.github.epsilon.graphics.schedulers.render2d

import java.util.concurrent.atomic.AtomicLong

/** 重载线程只递增版本，GPU 缓存在下次渲染线程访问时替换。 */
object Render2DResources {
    private val revision = AtomicLong()
    val generation: Long get() = revision.get()
    fun invalidate() {
        revision.incrementAndGet()
    }
}
