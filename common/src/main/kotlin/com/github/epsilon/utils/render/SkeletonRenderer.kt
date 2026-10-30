package com.github.epsilon.utils.render

import com.github.epsilon.Constants
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.utils.runSafe
import com.github.epsilon.utils.world
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.Direction
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.awt.Color

object SkeletonRenderer {

    fun render(player: AbstractClientPlayer, color: Color, lineWidth: Float) {
        val dispatcher = Constants.mc.getEntityRenderDispatcher()
        val partialTick = runSafe {
            if (world.tickRateManager().isFrozen) 1.0f
            else Constants.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
        } ?: return

        val state = dispatcher.getRenderer(player).createRenderState(player, partialTick) as? AvatarRenderState ?: return
        val avatarRenderer = dispatcher.getRenderer(state)

        val origin = Vec3(state.x, state.y, state.z).add(avatarRenderer.getRenderOffset(state))
        val stack = PoseStack()
        prepareModelPose(avatarRenderer, state, stack)

        val model: PlayerModel = avatarRenderer.getModel()
        model.setupAnim(state)
        model.root().translateAndRotate(stack)

        val neck = point(stack, model.head, origin, 0.0f, 0.0f, 0.0f)
        val head = point(stack, model.head, origin, 0.0f, -8.0f, 0.0f)
        val leftShoulder = point(stack, model.leftArm, origin, 0.0f, 0.0f, 0.0f)
        val rightShoulder = point(stack, model.rightArm, origin, 0.0f, 0.0f, 0.0f)
        val leftHip = point(stack, model.leftLeg, origin, 0.0f, 0.0f, 0.0f)
        val rightHip = point(stack, model.rightLeg, origin, 0.0f, 0.0f, 0.0f)
        val shoulders = leftShoulder.lerp(rightShoulder, 0.5)
        val hips = leftHip.lerp(rightHip, 0.5)

        line(neck, head, color, lineWidth)
        line(neck, shoulders, color, lineWidth)
        line(leftShoulder, rightShoulder, color, lineWidth)
        line(shoulders, hips, color, lineWidth)
        line(leftHip, rightHip, color, lineWidth)
        line(leftShoulder, point(stack, model.leftArm, origin, 0.0f, 10.0f, 0.0f), color, lineWidth)
        line(rightShoulder, point(stack, model.rightArm, origin, 0.0f, 10.0f, 0.0f), color, lineWidth)
        line(leftHip, point(stack, model.leftLeg, origin, 0.0f, 12.0f, 0.0f), color, lineWidth)
        line(rightHip, point(stack, model.rightLeg, origin, 0.0f, 12.0f, 0.0f), color, lineWidth)
    }

    private fun prepareModelPose(renderer: AvatarRenderer<*>, state: AvatarRenderState, stack: PoseStack) {
        if (state.hasPose(Pose.SLEEPING)) {
            val direction: Direction? = state.bedOrientation
            if (direction != null) {
                val offset = state.eyeHeight - 0.1f
                stack.translate(-direction.stepX * offset, 0.0f, -direction.stepZ * offset)
            }
        }
        stack.scale(state.scale, state.scale, state.scale)
        renderer.setupRotations(state, stack, state.bodyRot, state.scale)
        stack.scale(-1.0f, -1.0f, 1.0f)
        renderer.scale(state, stack)
        stack.translate(0.0f, -1.501f, 0.0f)
    }

    private fun point(stack: PoseStack, part: ModelPart, origin: Vec3, x: Float, y: Float, z: Float): Vec3 {
        stack.pushPose()
        try {
            part.translateAndRotate(stack)
            val point = stack.last().pose().transformPosition(x / 16.0f, y / 16.0f, z / 16.0f, Vector3f())
            return origin.add(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
        } finally {
            stack.popPose()
        }
    }

    private fun line(from: Vec3, to: Vec3, color: Color, lineWidth: Float) {
        Render3DScheduler.addLine(from, to, color, lineWidth)
    }

}
