package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class SkeletonRenderer {

    public static void render(AbstractClientPlayer player, Color color, float lineWidth) {
        var dispatcher = mc.getEntityRenderDispatcher();
        float partialTick = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        if (!(dispatcher.getRenderer(player).createRenderState(player, partialTick) instanceof AvatarRenderState state))
            return;
        AvatarRenderer<?> avatarRenderer = dispatcher.getRenderer(state);

        Vec3 origin = new Vec3(state.x, state.y, state.z).add(avatarRenderer.getRenderOffset(state));
        PoseStack stack = new PoseStack();
        prepareModelPose(avatarRenderer, state, stack);

        PlayerModel model = avatarRenderer.getModel();
        model.setupAnim(state);
        model.root().translateAndRotate(stack);

        Vec3 neck = point(stack, model.head, origin, 0.0f, 0.0f, 0.0f);
        Vec3 head = point(stack, model.head, origin, 0.0f, -8.0f, 0.0f);
        Vec3 leftShoulder = point(stack, model.leftArm, origin, 0.0f, 0.0f, 0.0f);
        Vec3 rightShoulder = point(stack, model.rightArm, origin, 0.0f, 0.0f, 0.0f);
        Vec3 leftHip = point(stack, model.leftLeg, origin, 0.0f, 0.0f, 0.0f);
        Vec3 rightHip = point(stack, model.rightLeg, origin, 0.0f, 0.0f, 0.0f);
        Vec3 shoulders = leftShoulder.lerp(rightShoulder, 0.5);
        Vec3 hips = leftHip.lerp(rightHip, 0.5);

        line(neck, head, color, lineWidth);
        line(neck, shoulders, color, lineWidth);
        line(leftShoulder, rightShoulder, color, lineWidth);
        line(shoulders, hips, color, lineWidth);
        line(leftHip, rightHip, color, lineWidth);
        line(leftShoulder, point(stack, model.leftArm, origin, 0.0f, 10.0f, 0.0f), color, lineWidth);
        line(rightShoulder, point(stack, model.rightArm, origin, 0.0f, 10.0f, 0.0f), color, lineWidth);
        line(leftHip, point(stack, model.leftLeg, origin, 0.0f, 12.0f, 0.0f), color, lineWidth);
        line(rightHip, point(stack, model.rightLeg, origin, 0.0f, 12.0f, 0.0f), color, lineWidth);
    }

    private static void prepareModelPose(AvatarRenderer<?> renderer, AvatarRenderState state, PoseStack stack) {
        if (state.hasPose(Pose.SLEEPING)) {
            Direction direction = state.bedOrientation;
            if (direction != null) {
                float offset = state.eyeHeight - 0.1f;
                stack.translate(-direction.getStepX() * offset, 0.0f, -direction.getStepZ() * offset);
            }
        }
        stack.scale(state.scale, state.scale, state.scale);
        renderer.setupRotations(state, stack, state.bodyRot, state.scale);
        stack.scale(-1.0f, -1.0f, 1.0f);
        renderer.scale(state, stack);
        stack.translate(0.0f, -1.501f, 0.0f);
    }

    private static Vec3 point(PoseStack stack, ModelPart part, Vec3 origin, float x, float y, float z) {
        stack.pushPose();
        try {
            part.translateAndRotate(stack);
            Vector3f point = stack.last().pose().transformPosition(x / 16.0f, y / 16.0f, z / 16.0f, new Vector3f());
            return origin.add(point.x, point.y, point.z);
        } finally {
            stack.popPose();
        }
    }

    private static void line(Vec3 from, Vec3 to, Color color, float lineWidth) {
        Render3DScheduler.INSTANCE.addLine(from, to, color, lineWidth);
    }

}
