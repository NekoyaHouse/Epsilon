package com.github.epsilon.events.api

interface IEvent : IPosting {
    fun post() = post(this)
}
