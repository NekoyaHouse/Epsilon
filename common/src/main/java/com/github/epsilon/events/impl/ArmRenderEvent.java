package com.github.epsilon.events.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;

public record ArmRenderEvent(InteractionHand hand, PoseStack poseStack) {
}
