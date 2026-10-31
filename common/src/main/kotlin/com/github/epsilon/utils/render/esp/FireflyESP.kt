package com.github.epsilon.utils.render.esp

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.utils.safety.*
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

object FireflyESP {

    enum class ColorMode {
        Solid,
        Blend,
        Rainbow
    }

    private val FIREFLY_TEX: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/firefly.png")

    private val TARGET_ICON_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_target_icon")
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    private val TARGET_ICON_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_target_icon")
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build()

    /**
     * 在目标周围渲染萤火轨迹 ESP。
     *
     * @param stack             渲染姿态栈
     * @param target            目标实体
     * @param espLength         轨迹长度
     * @param factor            轨迹采样因子
     * @param shaking           轨迹抖动幅度
     * @param amplitude         轨迹振幅
     * @param color             主颜色
     * @param colorMode         颜色模式
     * @param secondColor       第二种颜色
     * @param colorMix          双色混合比例
     * @param colorSpeed        颜色动画速度
     * @param rainbowSpeed      彩虹颜色变化速度
     * @param rainbowSaturation 彩虹颜色饱和度
     * @param rainbowBrightness 彩虹颜色亮度
     */
    fun render(
        stack: PoseStack,
        target: LivingEntity,
        espLength: Int,
        factor: Int,
        shaking: Double,
        amplitude: Double,
        color: Color,
        colorMode: ColorMode,
        secondColor: Color,
        colorMix: Double,
        colorSpeed: Double,
        rainbowSpeed: Double,
        rainbowSaturation: Double,
        rainbowBrightness: Double,
    ) {
        runSafe {
            val canSee = player.hasLineOfSight(target)

            val camera: Camera = Constants.client.gameRenderer.mainCamera()
            val tickDelta = Constants.client.getDeltaTracker().getGameTimeDeltaPartialTick(true)

            val tPosX = Mth.lerp(tickDelta.toDouble(), target.xOld, target.x) - camera.position().x
            val tPosY = Mth.lerp(tickDelta.toDouble(), target.yOld, target.y) - camera.position().y
            val tPosZ = Mth.lerp(tickDelta.toDouble(), target.zOld, target.z) - camera.position().z
            val iAge = (target.tickCount - 1).toFloat() + tickDelta

            val pipeline = if (canSee) TARGET_ICON_PIPELINE else TARGET_ICON_NO_DEPTH_PIPELINE
            val renderer = LuminImmediateRenderer.beginPosTexColorQuads(pipeline, FIREFLY_TEX)

            for (j in 0 until 3) {
                for (i in 0..espLength) {
                    val radians = Math.toRadians(
                        (((i.toFloat() / 1.5f + iAge) * factor + (j * 120)) % (factor * 360)).toDouble()
                    )
                    val sinQuad = Math.sin(Math.toRadians((iAge * 2.5f + i * (j + 1)).toDouble()) * amplitude) / shaking

                    val offset = i.toFloat() / espLength.toFloat()

                    stack.pushPose()
                    stack.translate(
                        tPosX + Math.cos(radians) * target.bbWidth,
                        tPosY + target.bbHeight * 0.5 + sinQuad,
                        tPosZ + Math.sin(radians) * target.bbWidth
                    )
                    stack.rotateDegrees(Axis.YP, -camera.yRot())
                    stack.rotateDegrees(Axis.XP, camera.xRot())

                    val matrix: Matrix4f = stack.last().pose()

                    val scale = Math.max(0.24f * (offset), 0.2f)

                    val renderColor = resolveColor(
                        iAge,
                        i,
                        j,
                        espLength,
                        colorMode,
                        color,
                        secondColor,
                        colorMix.toFloat(),
                        colorSpeed.toFloat(),
                        rainbowSpeed.toFloat(),
                        rainbowSaturation.toFloat(),
                        rainbowBrightness.toFloat()
                    ).getRGB()

                    renderer.vertex(matrix, -scale, scale, 0f, 0f, 1f, renderColor)
                    renderer.vertex(matrix, scale, scale, 0f, 1f, 1f, renderColor)
                    renderer.vertex(matrix, scale, -scale, 0f, 1f, 0f, renderColor)
                    renderer.vertex(matrix, -scale, -scale, 0f, 0f, 0f, renderColor)

                    stack.popPose()
                }
            }

            renderer.end()
        }
    }

    private fun resolveColor(
        age: Float,
        index: Int,
        ringIndex: Int,
        espLength: Int,
        mode: ColorMode,
        primaryColor: Color,
        secondaryColor: Color,
        mixAmount: Float,
        blendSpeed: Float,
        rainbowSpeed: Float,
        rainbowSaturation: Float,
        rainbowBrightness: Float,
    ): Color {
        val clampedMix = Mth.clamp(mixAmount, 0.0f, 1.0f)
        val progress = if (espLength <= 0) 0.0f else index.toFloat() / espLength.toFloat()

        return when (mode) {
            ColorMode.Blend -> {
                val wave = (Mth.sin(((age * blendSpeed * 0.25f) + (progress * 6.2831855f) + ringIndex).toDouble()) + 1.0f) * 0.5f
                val blendProgress = Mth.clamp(wave * clampedMix, 0.0f, 1.0f)
                ColorUtils.interpolateColor(primaryColor, secondaryColor, blendProgress)
            }

            ColorMode.Rainbow -> {
                val hue = Mth.frac((age * 0.01f * rainbowSpeed) + progress + ringIndex * 0.17f)
                val rainbow = Color.getHSBColor(hue, Mth.clamp(rainbowSaturation, 0.0f, 1.0f), Mth.clamp(rainbowBrightness, 0.0f, 1.0f))
                Color(rainbow.red, rainbow.green, rainbow.blue, primaryColor.alpha)
            }

            ColorMode.Solid -> primaryColor
        }
    }

}
