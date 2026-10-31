package com.github.epsilon.events.api

interface IPosting {

    val eventBus: EventBus

    fun post(event: Any)

}
