package com.github.epsilon.events.api

interface IEvent<out E : IEvent<E>> : IPosting {

    @Suppress("UNCHECKED_CAST")
    fun post(): E {
        post(this)
        return this as E
    }

}
