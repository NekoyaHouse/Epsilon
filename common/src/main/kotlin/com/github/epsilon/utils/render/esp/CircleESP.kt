package com.github.epsilon.utils.render.esp

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.utils.safety.*
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import java.awt.Color

object CircleESP {

    private val TRIANGLE_STRIP_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/triangle_strip"))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
        .build()

    private val TRIANGLE_STRIP_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/triangle_strip"))
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
        .build()

    private val CIRCLE_LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/circle_lines"))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    private val CIRCLE_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/circle_lines"))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build()

    /**
     * 在目标周围渲染圆形 ESP。
     *
     * @param poseStack   渲染姿态栈
     * @param target      目标实体
     * @param sideColor   填充面颜色
     * @param lineColor   轮廓线颜色
     * @param alphaFactor 透明度系数
     */
    fun render(poseStack: PoseStack, target: LivingEntity, sideColor: Color, lineColor: Color, alphaFactor: Float) {
        runSafe {
            val canSee = player.hasLineOfSight(target)

            val ticks = (System.currentTimeMillis() % 1000000).toFloat() * 0.004f
            val alpha = 0.35f + 0.65f * ((Mth.sin((ticks * 1.8f).toDouble()) + 1.0f) * 0.5f) * alphaFactor

            val tickDelta = Constants.client.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val cameraPos = Constants.client.getEntityRenderDispatcher().camera!!.position()

            val x = Mth.lerp(tickDelta.toDouble(), target.xo, target.x) - cameraPos.x
            val y = Mth.lerp(tickDelta.toDouble(), target.yo, target.y) - cameraPos.y + Math.sin(ticks.toDouble()) + 1
            val z = Mth.lerp(tickDelta.toDouble(), target.zo, target.z) - cameraPos.z

            poseStack.pushPose()
            poseStack.translate(x, y, z)

            val matrix: Matrix4f = poseStack.last().pose()

            val triPipeline = if (canSee) TRIANGLE_STRIP_PIPELINE else TRIANGLE_STRIP_NO_DEPTH_PIPELINE
            val triBuilder = LuminImmediateRenderer.beginPosColorTriangleStrip(triPipeline)

            val radius = (
                    (target.boundingBox.maxX - target.boundingBox.minX) +
                            (target.boundingBox.maxZ - target.boundingBox.minZ)
                    ) * 0.5
            val radiusFloat = radius.toFloat()

            var i = 0.0f
            while (i <= Math.PI * 2) {
                val vecX = (radiusFloat * Math.cos(i.toDouble())).toFloat()
                val vecZ = (radiusFloat * Math.sin(i.toDouble())).toFloat()

                triBuilder.vertex(
                    matrix, vecX, (-Math.sin((ticks + 1.0f).toDouble()) / 2.7f).toFloat(), vecZ,
                    Color(sideColor.alpha / 255.0f, sideColor.green / 255.0f, sideColor.blue / 255.0f, 0.0f).getRGB()
                )
                triBuilder.vertex(
                    matrix, vecX, 0.0f, vecZ,
                    Color(sideColor.alpha / 255.0f, sideColor.green / 255.0f, sideColor.blue / 255.0f, 0.52f * alpha).getRGB()
                )

                i += (Math.PI.toFloat() * 2) / 64.0f
            }

            triBuilder.end()

            val linePipeline = if (canSee) CIRCLE_LINES_PIPELINE else CIRCLE_LINES_NO_DEPTH_PIPELINE
            val lineBuilder = LuminImmediateRenderer.beginLines(linePipeline)
            val entry = poseStack.last()

            var j = 0
            while (j <= 180) {
                val radAngle = (j * Math.PI * 2 / 90).toFloat()
                val nextAngle = ((j + 1) * Math.PI * 2 / 90).toFloat()

                val nextPoint = getPoint(nextAngle, radiusFloat)
                val linePoint = getPoint(radAngle, radiusFloat)
                val normal = getNormal(radAngle)

                lineBuilder.vertex(
                    matrix, entry, linePoint[0], 0.0f, linePoint[1],
                    Color(lineColor.red, lineColor.green, lineColor.blue, Math.round(lineColor.alpha * alpha)).getRGB(),
                    normal[0], 0.0f, normal[1], 2.0f
                )
                lineBuilder.vertex(
                    matrix, entry, nextPoint[0], 0.0f, nextPoint[1],
                    Color(lineColor.red, lineColor.green, lineColor.blue, Math.round(lineColor.alpha * alpha)).getRGB(),
                    normal[0], 0.0f, normal[1], 2.0f
                )

                j++
            }

            lineBuilder.end()

            poseStack.popPose()
        }
    }

    private fun getPoint(radAngle: Float, radius: Float): FloatArray {
        return floatArrayOf((-Math.sin(radAngle.toDouble()) * radius).toFloat(), (Math.cos(radAngle.toDouble()) * radius).toFloat())
    }

    private fun getNormal(radAngle: Float): FloatArray {
        return floatArrayOf((-Math.cos(radAngle.toDouble())).toFloat(), (-Math.sin(radAngle.toDouble())).toFloat())
    }

}
