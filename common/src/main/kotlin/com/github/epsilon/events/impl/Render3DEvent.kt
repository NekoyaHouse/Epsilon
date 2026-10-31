package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import com.mojang.blaze3d.vertex.PoseStack

class Render3DEvent(val poseStack: PoseStack) : IEvent<Render3DEvent>, IPosting by Companion {
    companion object : EventBus()
}
