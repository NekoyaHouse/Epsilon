package com.github.epsilon.events.impl;

import com.mojang.blaze3d.vertex.PoseStack;

public class Render3DEvent {

    private final PoseStack poseStack;

    public Render3DEvent(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

}
