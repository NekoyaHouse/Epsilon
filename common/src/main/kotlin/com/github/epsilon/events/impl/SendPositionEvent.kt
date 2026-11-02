package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.world.phys.Vec3

class SendPositionEvent(
    var x: Double,
    var y: Double,
    var z: Double,
    var yaw: Float,
    var pitch: Float,
    var isOnGround: Boolean,
    val lastSentPosition: Vec3,
    val lastSentYaw: Float,
    val lastSentPitch: Float,
    val positionReminder: Int,
) : Cancellable(), IEvent<SendPositionEvent>, IPosting by Companion {
    var forcePosition = false
    var forceRotation = false

    companion object : EventBus()
}
