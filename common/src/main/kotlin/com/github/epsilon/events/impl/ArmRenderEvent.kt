package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.InteractionHand

data class ArmRenderEvent(val hand: InteractionHand, val poseStack: PoseStack) : IEvent<ArmRenderEvent>,
    IPosting by Companion {
    companion object : EventBus()
}
