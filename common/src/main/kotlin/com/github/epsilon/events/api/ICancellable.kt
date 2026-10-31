package com.github.epsilon.events.api

interface ICancellable {
    var isCancelled: Boolean

    fun cancel() {
        isCancelled = true
    }
}

open class Cancellable : ICancellable {
    override var isCancelled = false
        set(value) {
            field = field || value
        }
}
