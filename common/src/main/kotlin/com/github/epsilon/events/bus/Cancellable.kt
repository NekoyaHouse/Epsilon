package com.github.epsilon.events.bus

open class Cancellable {

    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }

}
