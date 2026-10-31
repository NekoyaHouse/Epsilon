package com.github.epsilon.utils.render.esp

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.utils.render.ColorUtils
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.Camera
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import java.awt.Color
import kotlin.math.PI
import kotlin.math.sin

object CaptureMarkESP {

    private val CAPTUREMARK_TEX: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/target.png")

    private val TARGET_ICON_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_target_icon")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    /**
     * 为目标渲染旋转波形捕获标记。
     *
     * @param poseStack 渲染姿态栈
     * @param target    目标实体
     * @param espSize   特效尺寸
     * @param rotSpeed  旋转速度
     * @param waveSpeed 波形动画速度
     * @param color1    第一种颜色
     * @param color2    第二种颜色
     */
    fun render(
        poseStack: PoseStack,
        target: LivingEntity,
        espSize: Double,
        rotSpeed: Double,
        waveSpeed: Double,
        color1: Color,
        color2: Color
    ) {
        val timeSeconds = System.nanoTime() * 1.0E-9
        val rotation = (-((timeSeconds * rotSpeed * 60.0) % 360.0)).toFloat()

        val cameraPos = mc.entityRenderDispatcher.camera!!.position()
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val ex = Mth.lerp(tickDelta.toDouble(), target.xOld, target.x) - cameraPos.x
        val ey = Mth.lerp(tickDelta.toDouble(), target.yOld, target.y) - cameraPos.y
        val ez = Mth.lerp(tickDelta.toDouble(), target.zOld, target.z) - cameraPos.z

        val size = espSize.toFloat() * 0.5f

        poseStack.pushPose()
        poseStack.translate(ex, ey + target.bbHeight * 0.5, ez)

        val camera: Camera = mc.gameRenderer.mainCamera()
        poseStack.rotateDegrees(Axis.YP, -camera.yRot())
        poseStack.rotateDegrees(Axis.XP, camera.xRot())
        poseStack.rotateDegrees(Axis.ZP, rotation)

        val renderer = LuminImmediateRenderer.beginPosTexColorQuads(TARGET_ICON_PIPELINE, CAPTUREMARK_TEX)

        val matrix: Matrix4f = poseStack.last().pose()

        val c1 = getColorForProgress(0f, waveSpeed, color1, color2, timeSeconds)
        val c2 = getColorForProgress(0.25f, waveSpeed, color1, color2, timeSeconds)
        val c3 = getColorForProgress(0.5f, waveSpeed, color1, color2, timeSeconds)
        val c4 = getColorForProgress(0.75f, waveSpeed, color1, color2, timeSeconds)

        renderer.vertex(matrix, -size, -size, 0f, 0f, 0f, c1.rgb)
        renderer.vertex(matrix, -size, size, 0f, 0f, 1f, c2.rgb)
        renderer.vertex(matrix, size, size, 0f, 1f, 1f, c3.rgb)
        renderer.vertex(matrix, size, -size, 0f, 1f, 0f, c4.rgb)
        renderer.end()

        poseStack.popPose()
    }

    private fun getColorForProgress(
        progress: Float,
        waveSpeed: Double,
        color1: Color,
        color2: Color,
        timeSeconds: Double
    ): Color {
        var wave = sin((progress * PI * 2.0) + (timeSeconds * waveSpeed)).toFloat()
        wave = (wave + 1f) / 2f
        return ColorUtils.interpolateColor(color1, color2, wave)
    }

}
