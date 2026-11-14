package com.github.epsilon.modules.impl.combat.crystalaura

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import java.awt.Color

internal class CrystalTechnoRenderer {
    private val pulses = ArrayList<Int>()
    private val texture = ResourceLocationUtils.getIdentifier("textures/crystalaura/techno.png")
    private val pipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/crystal_techno"))
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    fun onPlace() {
        pulses.add(8)
    }

    fun tick() {
        pulses.replaceAll { it - 1 }
        pulses.removeAll { it < -1 }
    }

    fun clear() {
        pulses.clear()
    }

    fun render(
        stack: PoseStack,
        frame: CrystalRenderAnimation.Frame,
        ticks: Int,
        partialTick: Float,
        sideColor: Color,
        lineColor: Color,
        fill: Boolean,
        outline: Boolean,
    ) {
        val box = frame.box
        val capColor = faded(lineColor, frame.scale * 0.4)
        if (fill) {
            val camera = mc.gameRenderer.mainCamera().position()
            val minX = (box.minX - camera.x).toFloat()
            val minY = (box.minY - camera.y).toFloat()
            val minZ = (box.minZ - camera.z).toFloat()
            val maxX = (box.maxX - camera.x).toFloat()
            val maxY = (box.maxY - camera.y).toFloat()
            val maxZ = (box.maxZ - camera.z).toFloat()
            val offset = (ticks - 1.0f + partialTick) % 80.0f / 80.0f
            val bottomColor = faded(sideColor, frame.scale * 0.1)
            val topColor = faded(lineColor, frame.scale)
            val matrix = stack.last().pose()
            val renderer = LuminImmediateRenderer.beginPosTexColorQuads(pipeline, texture)

            renderer.vertex(matrix, minX, minY, minZ, offset, 1.0f, bottomColor)
            renderer.vertex(matrix, minX, maxY, minZ, offset, 0.0f, topColor)
            renderer.vertex(matrix, maxX, maxY, minZ, offset + 0.32f, 0.0f, topColor)
            renderer.vertex(matrix, maxX, minY, minZ, offset + 0.32f, 1.0f, bottomColor)

            renderer.vertex(matrix, maxX, minY, minZ, offset + 0.32f, 1.0f, bottomColor)
            renderer.vertex(matrix, maxX, maxY, minZ, offset + 0.32f, 0.0f, topColor)
            renderer.vertex(matrix, maxX, maxY, maxZ, offset + 0.64f, 0.0f, topColor)
            renderer.vertex(matrix, maxX, minY, maxZ, offset + 0.64f, 1.0f, bottomColor)

            renderer.vertex(matrix, maxX, minY, maxZ, offset + 0.64f, 1.0f, bottomColor)
            renderer.vertex(matrix, minX, minY, maxZ, offset + 0.96f, 1.0f, bottomColor)
            renderer.vertex(matrix, minX, maxY, maxZ, offset + 0.96f, 0.0f, topColor)
            renderer.vertex(matrix, maxX, maxY, maxZ, offset + 0.64f, 0.0f, topColor)

            renderer.vertex(matrix, minX, minY, maxZ, offset + 0.96f, 1.0f, bottomColor)
            renderer.vertex(matrix, minX, minY, minZ, offset + 1.28f, 1.0f, bottomColor)
            renderer.vertex(matrix, minX, maxY, minZ, offset + 1.28f, 0.0f, topColor)
            renderer.vertex(matrix, minX, maxY, maxZ, offset + 0.96f, 0.0f, topColor)
            renderer.end()

            Render3DScheduler.addFilledSide(box, capColor, Direction.UP)
        }
        if (outline) {
            Render3DScheduler.addSideOutline(box, capColor, 2.0f, Direction.UP)
            for (remaining in pulses) {
                val progress = 0.5 + ((remaining + 1.0f - partialTick) / 8.0f).coerceIn(0.0f, 0.5f)
                val half = (progress - 0.5) * frame.scale
                if (half <= 0.0) continue
                val y = box.maxY - (1.0 - progress) * frame.scale
                val pulseBox = AABB(
                    frame.center.x - half, y, frame.center.z - half,
                    frame.center.x + half, y, frame.center.z + half
                )
                Render3DScheduler.addSideOutline(
                    pulseBox, faded(lineColor, frame.scale * progress), 2.0f, Direction.UP
                )
            }
        }
    }

    private fun faded(color: Color, alpha: Double): Int =
        Color(color.red, color.green, color.blue, (color.alpha * alpha).toInt().coerceIn(0, 255)).rgb
}
