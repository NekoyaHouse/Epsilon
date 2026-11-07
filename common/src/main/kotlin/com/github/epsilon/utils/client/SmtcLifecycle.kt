package com.github.epsilon.utils.client

import com.github.epsilon.native.smtc.SmtcService

/**
 * SMTC 生命周期的 Java 门面。
 *
 * `native` 是 Java 关键字，Java 源文件无法引用 `com.github.epsilon.native.smtc`，
 * 因此由本门面转发 [SmtcService] 的启停调用；Kotlin 代码仍直接使用 `SmtcService`。
 */
object SmtcLifecycle {

    @JvmStatic
    fun start() {
        SmtcService.start()
    }

    @JvmStatic
    fun stop() {
        SmtcService.stop()
    }

}
