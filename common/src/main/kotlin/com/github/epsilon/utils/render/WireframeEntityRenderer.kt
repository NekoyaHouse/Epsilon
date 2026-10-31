package com.github.epsilon.utils.render

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.modules.impl.render.AntiAlias
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.runSafe
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.model.Model
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.UvMapping
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.Color

object WireframeEntityRenderer {

    private val modelPoseStack = PoseStack()
    private val submitNodeStorage = WireframeSubmitNodeStorage()
    private val vertexConsumer = WireframeVertexConsumer()

    private val SIDES_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/wireframe_entity_sides"))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    private val LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/wireframe_entity_lines"))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    private var sideColor: Color = Color.WHITE
    private var lineColor: Color = Color.WHITE
    private var lineWidth: Float = 1.0f

    private var sidesBuilder: LuminImmediateRenderer.PosColorQuads? = null
    private var linesBuilder: LuminImmediateRenderer.Lines? = null

    private var drawMatrix: Matrix4f? = null
    private var linePose: PoseStack.Pose? = null

    private var offsetX: Double = 0.0
    private var offsetY: Double = 0.0
    private var offsetZ: Double = 0.0

    fun beginBatch(renderStack: PoseStack) {
        if (isBatching()) {
            throw IllegalStateException("Wireframe entity renderer is already batching")
        }
        beginDraw(renderStack)
    }

    fun endBatch() {
        if (isBatching()) AntiAlias.render(Runnable { endDraw() })
    }

    fun render(renderStack: PoseStack, entity: Entity, scale: Double, sideColor: Color, lineColor: Color, lineWidth: Float) {
        var startedBatch = false
        if (!isBatching()) {
            beginBatch(renderStack)
            startedBatch = true
        }

        val partialTick = runSafe {
            if (level.tickRateManager().isFrozen) 1.0f
            else Constants.client.deltaTracker.getGameTimeDeltaPartialTick(true)
        }
        if (partialTick != null) {
            renderEntity(entity, scale, sideColor, lineColor, lineWidth, partialTick)
        }

        if (startedBatch) {
            endBatch()
        }
    }

    private fun renderEntity(
        entity: Entity,
        scale: Double,
        sideColor: Color,
        lineColor: Color,
        lineWidth: Float,
        partialTick: Float,
    ) {
        this.sideColor = sideColor
        this.lineColor = lineColor
        this.lineWidth = lineWidth

        offsetX = Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x)
        offsetY = Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y)
        offsetZ = Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)

        @Suppress("UNCHECKED_CAST")
        val renderer = Constants.client.entityRenderDispatcher.getRenderer(entity) as EntityRenderer<Entity, EntityRenderState>
        val state: EntityRenderState = renderer.createRenderState(entity, partialTick)

        val renderOffset = renderer.getRenderOffset(state)
        offsetX += renderOffset.x
        offsetY += renderOffset.y
        offsetZ += renderOffset.z

        modelPoseStack.pushPose()
        modelPoseStack.scale(scale.toFloat(), scale.toFloat(), scale.toFloat())
        renderer.submit(
            state,
            modelPoseStack,
            submitNodeStorage,
            Constants.client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        )
        modelPoseStack.popPose()
        vertexConsumer.reset()
        submitNodeStorage.submitsPerOrder.clear()
    }

    private fun isBatching(): Boolean {
        return sidesBuilder != null && linesBuilder != null
    }

    private fun beginDraw(renderStack: PoseStack) {
        val pose = renderStack.last()
        drawMatrix = pose.pose()
        linePose = pose
        sidesBuilder = LuminImmediateRenderer.beginPosColorQuads(SIDES_PIPELINE)
        linesBuilder = LuminImmediateRenderer.beginLines(LINES_PIPELINE)
    }

    private fun endDraw() {
        val sides = sidesBuilder
        if (sides != null) {
            sides.end()
        }
        val lines = linesBuilder
        if (lines != null) {
            lines.end()
        }

        sidesBuilder = null
        linesBuilder = null
        drawMatrix = null
        linePose = null
    }

    private fun drawQuad(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
    ) {
        val cameraPos = Constants.client.entityRenderDispatcher.camera!!.position()
        val rx1 = (offsetX + x1 - cameraPos.x).toFloat()
        val ry1 = (offsetY + y1 - cameraPos.y).toFloat()
        val rz1 = (offsetZ + z1 - cameraPos.z).toFloat()
        val rx2 = (offsetX + x2 - cameraPos.x).toFloat()
        val ry2 = (offsetY + y2 - cameraPos.y).toFloat()
        val rz2 = (offsetZ + z2 - cameraPos.z).toFloat()
        val rx3 = (offsetX + x3 - cameraPos.x).toFloat()
        val ry3 = (offsetY + y3 - cameraPos.y).toFloat()
        val rz3 = (offsetZ + z3 - cameraPos.z).toFloat()
        val rx4 = (offsetX + x4 - cameraPos.x).toFloat()
        val ry4 = (offsetY + y4 - cameraPos.y).toFloat()
        val rz4 = (offsetZ + z4 - cameraPos.z).toFloat()

        val sides = sidesBuilder
        if (sides != null) {
            sides.vertex(drawMatrix!!, rx1, ry1, rz1, sideColor.rgb)
            sides.vertex(drawMatrix!!, rx2, ry2, rz2, sideColor.rgb)
            sides.vertex(drawMatrix!!, rx3, ry3, rz3, sideColor.rgb)
            sides.vertex(drawMatrix!!, rx4, ry4, rz4, sideColor.rgb)
        }

        if (linesBuilder != null) {
            drawLine(rx1, ry1, rz1, rx2, ry2, rz2)
            drawLine(rx2, ry2, rz2, rx3, ry3, rz3)
            drawLine(rx3, ry3, rz3, rx4, ry4, rz4)
            drawLine(rx4, ry4, rz4, rx1, ry1, rz1)
        }
    }

    private fun drawLine(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float) {
        val lines = linesBuilder ?: return
        val normal = getNormal(x1, y1, z1, x2, y2, z2)
        val matrix = drawMatrix!!
        val pose = linePose!!
        lines.vertex(matrix, pose, x1, y1, z1, lineColor.rgb, normal.x, normal.y, normal.z, lineWidth)
        lines.vertex(matrix, pose, x2, y2, z2, lineColor.rgb, normal.x, normal.y, normal.z, lineWidth)
    }

    private fun getNormal(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Vector3f {
        val xNormal = x2 - x1
        val yNormal = y2 - y1
        val zNormal = z2 - z1
        val normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal)
        if (normalSqrt <= 1.0E-5f) {
            return Vector3f(0.0f, 1.0f, 0.0f)
        }
        return Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt)
    }

    private class WireframeSubmitNodeStorage : SubmitNodeStorage() {

        override fun <S : Any> submitModel(
            model: Model<in S>,
            state: S,
            poseStack: PoseStack,
            renderType: RenderType,
            lightCoords: Int,
            overlayCoords: Int,
            tintedColor: Int,
            uvMapping: UvMapping?,
            outlineColor: Int,
        ) {
            model.setupAnim(state)
            model.renderToBuffer(poseStack, vertexConsumer, lightCoords, overlayCoords, tintedColor)
        }

        override fun submitCustomGeometry(
            poseStack: PoseStack,
            renderType: RenderType,
            customGeometryRenderer: SubmitNodeCollector.CustomGeometryRenderer,
        ) {
            customGeometryRenderer.render(poseStack.last(), vertexConsumer)
        }
    }

    private class WireframeVertexConsumer : VertexConsumer {

        private val xs = FloatArray(4)
        private val ys = FloatArray(4)
        private val zs = FloatArray(4)
        private var index = 0

        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
            xs[index] = x
            ys[index] = y
            zs[index] = z
            index++

            if (index == 4) {
                drawQuad(
                    xs[0], ys[0], zs[0],
                    xs[1], ys[1], zs[1],
                    xs[2], ys[2], zs[2],
                    xs[3], ys[3], zs[3]
                )
                index = 0
            }

            return this
        }

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
            return this
        }

        override fun setColor(color: Int): VertexConsumer {
            return this
        }

        override fun setUv(u: Float, v: Float): VertexConsumer {
            return this
        }

        override fun setUv1(u: Int, v: Int): VertexConsumer {
            return this
        }

        override fun setUv2(u: Int, v: Int): VertexConsumer {
            return this
        }

        override fun setUv3(u: Float, v: Float): VertexConsumer {
            return this
        }

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
            return this
        }

        override fun setLineWidth(width: Float): VertexConsumer {
            return this
        }

        fun reset() {
            index = 0
        }
    }

}
