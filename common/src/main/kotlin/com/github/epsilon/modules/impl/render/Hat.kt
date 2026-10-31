package com.github.epsilon.modules.impl.render

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.safety.runSafe
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import org.joml.Matrix4f
import java.awt.Color
import kotlin.math.PI

object Hat : Module("Hat", Category.RENDER) {

    init {
        handler<Render3DEvent> { onRender3D(it) }
    }

    private enum class Mode {
        Astolfo,
        Sexy,
        Fade
    }

    private val mode by enumSetting("Mode", Mode.Sexy)
    private val points by intSetting("Points", 30, 3, 180, 1)
    private val size by doubleSetting("Size", 0.5, 0.1, 3.0, 0.1)
    private val offset by doubleSetting("Offset", 2000.0, 0.0, 5000.0, 100.0)
    private val color by colorSetting("Color", Color(255, 255, 255), dependency = { mode == Mode.Fade })
    private val secondColor by colorSetting("Second Color", Color(0, 0, 0), dependency = { mode == Mode.Fade })
    private val onlyThirdPerson by boolSetting("Only Third Person", true)

    private val positions: Array<DoubleArray> = Array(181) { DoubleArray(2) }
    private var lastPoints = 0
    private var lastSize = 0.0

    private val HAT_CONE_PIPELINE: RenderPipeline =
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/hat_cone"))
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
            .build()

    private val HAT_OUTLINE_PIPELINE: RenderPipeline =
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/hat_outline"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build()

    private fun computeChineseHatPoints(points: Int, radius: Double) {
        for (i in 0..points) {
            val circleX = radius * StrictMath.cos(i * PI * 2 / points)
            val circleZ = radius * StrictMath.sin(i * PI * 2 / points)
            this.positions[i][0] = circleX
            this.positions[i][1] = circleZ
        }
    }

    private fun onRender3D(event: Render3DEvent) {
        if (this.lastSize != this.size || this.lastPoints != this.points) {
            this.lastSize = this.size
            this.lastPoints = this.points
            this.computeChineseHatPoints(this.lastPoints, this.lastSize)
        }

        runSafe {
            drawHat(event.poseStack, player)
        }
    }

    fun drawHat(stack: PoseStack, player: Player) {
        if (player === mc.player && mc.options.cameraType.isFirstPerson && onlyThirdPerson) {
            return
        }

        val pointCount = this.points
        val radius = this.size

        val colors = arrayOfNulls<Color>(181)
        val colorMode = getColorMode()

        for (i in colors.indices) {
            colors[i] = this.fadeBetween(
                colorMode, this.offset,
                i.toDouble() * (this.offset / this.points)
            )
        }

        val camera = mc.gameRenderer.mainCamera().position()
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val x = Mth.lerp(tickDelta.toDouble(), player.xOld, player.x) - camera.x
        val y = Mth.lerp(tickDelta.toDouble(), player.yOld, player.y) - camera.y
        val z = Mth.lerp(tickDelta.toDouble(), player.zOld, player.z) - camera.z

        stack.pushPose()

        stack.translate(x, y + 1.9, z)

        if (player.isCrouching) {
            stack.translate(0.0, -0.2, 0.0)
        }

        val yaw = Mth.lerp(tickDelta, player.yHeadRotO, player.yHeadRot)
        stack.rotateDegrees(Axis.YN, yaw)

        val pitch = Mth.lerp(tickDelta, player.xRotO, player.xRot)
        stack.rotateDegrees(Axis.XP, pitch / 3.0f)
        stack.translate(0.0, 0.0, pitch / 270.0)

        val matrix: Matrix4f = stack.last().pose()
        val entry: PoseStack.Pose = stack.last()

        val lineWidth = 2.0f
        val outlineBuffer = LuminImmediateRenderer.beginLines(HAT_OUTLINE_PIPELINE)

        for (i in 0 until pointCount) {
            val next = (i + 1) % pointCount

            val p1 = this.positions[i]
            val p2 = this.positions[next]

            val dx = (p2[0] - p1[0]).toFloat()
            val dz = (p2[1] - p1[1]).toFloat()
            val len = Mth.sqrt(dx * dx + dz * dz)
            val nx = if (len == 0.0f) 1.0f else dx / len
            val nz = if (len == 0.0f) 0.0f else dz / len

            val c1 = colors[i]!!
            val c2 = colors[next]!!

            outlineBuffer.vertex(
                matrix,
                entry,
                p1[0].toFloat(),
                0.0f,
                p1[1].toFloat(),
                withAlpha(c1, 255).rgb,
                nx,
                0.0f,
                nz,
                lineWidth
            )
            outlineBuffer.vertex(
                matrix,
                entry,
                p2[0].toFloat(),
                0.0f,
                p2[1].toFloat(),
                withAlpha(c2, 255).rgb,
                nx,
                0.0f,
                nz,
                lineWidth
            )
        }
        outlineBuffer.end()

        val coneBuffer = LuminImmediateRenderer.beginPosColorTriangleFan(HAT_CONE_PIPELINE)

        coneBuffer.vertex(matrix, 0.0f, (radius / 2).toFloat(), 0.0f, Color(255, 255, 255, 128).rgb)

        for (i in 0..pointCount) {
            val pos = this.positions[i % pointCount]
            val clr = colors[i % colors.size]!!
            coneBuffer.vertex(matrix, pos[0].toFloat(), 0.0f, pos[1].toFloat(), withAlpha(clr, 128).rgb)
        }
        coneBuffer.end()

        stack.popPose()
    }

    private fun withAlpha(source: Color, alpha: Int): Color =
        Color(source.red, source.green, source.blue, Mth.clamp(alpha, 0, 255))

    private fun getColorMode(): Array<Color> = when (this.mode) {
        Mode.Astolfo -> arrayOf(
            Color(252, 106, 140), Color(252, 106, 213),
            Color(218, 106, 252), Color(145, 106, 252),
            Color(106, 140, 252), Color(106, 213, 252),
            Color(106, 213, 252), Color(106, 140, 252),
            Color(145, 106, 252), Color(218, 106, 252),
            Color(252, 106, 213), Color(252, 106, 140)
        )

        Mode.Sexy -> arrayOf(
            Color(255, 150, 255), Color(255, 132, 199),
            Color(211, 101, 187), Color(160, 80, 158),
            Color(120, 63, 160), Color(123, 65, 168),
            Color(104, 52, 152), Color(142, 74, 175),
            Color(160, 83, 179), Color(255, 110, 189),
            Color(255, 150, 255)
        )

        Mode.Fade -> arrayOf(
            this.color,
            this.secondColor,
            this.color
        )
    }

    fun fadeBetween(table: Array<Color>, speed: Double, offset: Double): Color =
        this.fadeBetween(table, (System.currentTimeMillis() + offset) % speed / speed)

    fun fadeBetween(table: Array<Color>, progress: Double): Color {
        val i = table.size
        if (progress == 1.0) {
            return table[0]
        }
        if (progress == 0.0) {
            return table[i - 1]
        }
        val max = maxOf(0.0, (1.0 - progress) * (i - 1))
        val min = max.toInt()
        return this.fadeBetween(table[min], table[min + 1], max - min)
    }

    fun fadeBetween(start: Color, end: Color, progress: Double): Color {
        var clamped = progress
        if (clamped > 1.0) {
            clamped = 1.0 - clamped % 1.0
        }
        return this.gradient(start, end, clamped)
    }

    fun gradient(start: Color, end: Color, progress: Double): Color {
        val invert = 1.0 - progress
        return Color(
            (start.red * invert + end.red * progress).toInt(),
            (start.green * invert + end.green * progress).toInt(),
            (start.blue * invert + end.blue * progress).toInt(),
            (start.alpha * invert + end.alpha * progress).toInt()
        )
    }

}
