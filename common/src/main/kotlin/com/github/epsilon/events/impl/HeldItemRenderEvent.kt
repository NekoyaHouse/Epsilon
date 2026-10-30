package com.github.epsilon.events.impl

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.InteractionHand

data class HeldItemRenderEvent(val hand: InteractionHand, val poseStack: PoseStack)
