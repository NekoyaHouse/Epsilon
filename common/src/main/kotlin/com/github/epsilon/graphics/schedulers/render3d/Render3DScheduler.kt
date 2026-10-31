package com.github.epsilon.graphics.schedulers.render3d

import com.github.epsilon.Constants.mc
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.modules.impl.render.AntiAlias
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.Color
import java.util.*

object Render3DScheduler {

    private val blurredBoxes: MutableList<BlurredBoxCommand> = ArrayList()
    private val filledBoxes: MutableList<FilledBoxCommand> = ArrayList()
    private val filledSides: MutableList<FilledSideCommand> = ArrayList()
    private val outlineBoxes: MutableList<OutlineBoxCommand> = ArrayList()
    private val sideOutlines: MutableList<SideOutlineCommand> = ArrayList()
    private val lines: MutableList<LineCommand> = ArrayList()

    init {
        handler<Render3DEvent>(priority = -999, alwaysListening = true) { event ->
            flush(event.poseStack)
        }
    }

    fun isEmpty(): Boolean = blurredBoxes.isEmpty() &&
            filledBoxes.isEmpty() &&
            filledSides.isEmpty() &&
            outlineBoxes.isEmpty() &&
            sideOutlines.isEmpty() &&
            lines.isEmpty()

    fun clear() {
        blurredBoxes.clear()
        filledBoxes.clear()
        filledSides.clear()
        outlineBoxes.clear()
        sideOutlines.clear()
        lines.clear()
    }

    fun addBlurredBox(box: AABB, blurStrength: Double) {
        blurredBoxes.add(BlurredBoxCommand(box, blurStrength))
    }

    fun addFilledBox(box: AABB, color: Color) {
        addFilledBox(box, color.rgb)
    }

    fun addFilledBox(box: AABB, color: Int) {
        addFilledFadeBox(box, color, color)
    }

    fun addFilledFadeBox(box: AABB, bottomColor: Int, topColor: Int) {
        filledBoxes.add(FilledBoxCommand(box, bottomColor, topColor))
    }

    fun addFilledSide(box: AABB, color: Int, direction: Direction) {
        filledSides.add(FilledSideCommand(box, color, direction))
    }

    fun addOutlineBox(box: AABB, color: Color) {
        addOutlineBox(box, color.rgb)
    }

    fun addOutlineBox(box: AABB, color: Color, thickness: Float) {
        addOutlineBox(box, color.rgb, thickness)
    }

    fun addOutlineBox(box: AABB, color: Int) {
        addOutlineBox(box, color, 2.0f)
    }

    fun addOutlineBox(box: AABB, color: Int, thickness: Float) {
        outlineBoxes.add(OutlineBoxCommand(box, color, thickness))
    }

    fun addSideOutline(stack: PoseStack, box: AABB, color: Int, thickness: Float, direction: Direction) {
        sideOutlines.add(SideOutlineCommand(box, color, thickness, direction))
    }

    fun addSideOutline(box: AABB, color: Int, thickness: Float, direction: Direction) {
        sideOutlines.add(SideOutlineCommand(box, color, thickness, direction))
    }

    fun addLine(from: Vec3, to: Vec3, color: Color, thickness: Float) {
        addLine(from, to, color.rgb, thickness)
    }

    fun addLine(from: Vec3, to: Vec3, color: Int, thickness: Float) {
        if (from.distanceToSqr(to) < 1.0E-6) return
        lines.add(LineCommand(from, to, color, thickness))
    }

    fun flush(stack: PoseStack) {
        if (isEmpty()) {
            return
        }

        try {
            flushBlur()
            if (filledBoxes.isNotEmpty() || filledSides.isNotEmpty() || outlineBoxes.isNotEmpty() || sideOutlines.isNotEmpty() || lines.isNotEmpty()) {
                AntiAlias.render {
                    flushFilled()
                    flushLines(stack)
                }
            }
        } finally {
            clear()
        }
    }


    private fun flushBlur() {
        if (blurredBoxes.isEmpty()) {
            return
        }

        for (command in blurredBoxes) {
            BlurShader.render3DBox(command.box, command.blurStrength)
        }
    }

    private fun flushFilled() {
        if (filledBoxes.isEmpty() && filledSides.isEmpty()) {
            return
        }

        val builder = LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE)
        val matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix
        // 3D 渲染阶段 camera 必然已由 GameRenderer 准备好；原 Java 同样直接解引用，这里保留相同的 NPE 时机。
        val camPos = mc.entityRenderDispatcher.camera!!.position()

        for (command in filledBoxes) {
            emitFilledBox(builder, matrix, camPos, command)
        }

        for (command in filledSides) {
            emitFilledSide(builder, matrix, camPos, command)
        }

        builder.end()
    }

    private fun flushLines(stack: PoseStack) {
        if (outlineBoxes.isEmpty() && sideOutlines.isEmpty() && lines.isEmpty()) {
            return
        }

        val builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE)
        val camPos = mc.entityRenderDispatcher.camera!!.position()
        val pose = stack.last()
        val matrix = pose.pose()

        for (command in outlineBoxes) {
            emitOutlineBox(builder, matrix, pose, camPos, command)
        }

        for (command in sideOutlines) {
            emitSideOutline(builder, matrix, pose, camPos, command)
        }

        for (command in lines) {
            emitLine(builder, matrix, pose, camPos, command)
        }

        builder.end()
    }

    private fun emitFilledBox(
        builder: LuminImmediateRenderer.PosColorQuads, matrix: Matrix4f, camPos: Vec3,
        command: FilledBoxCommand,
    ) {
        val box = command.box
        val minX = (box.minX - camPos.x).toFloat()
        val minY = (box.minY - camPos.y).toFloat()
        val minZ = (box.minZ - camPos.z).toFloat()
        val maxX = (box.maxX - camPos.x).toFloat()
        val maxY = (box.maxY - camPos.y).toFloat()
        val maxZ = (box.maxZ - camPos.z).toFloat()

        quad(
            builder, matrix,
            minX, minY, minZ, command.bottomColor,
            minX, minY, maxZ, command.bottomColor,
            maxX, minY, maxZ, command.bottomColor,
            maxX, minY, minZ, command.bottomColor
        )

        quad(
            builder, matrix,
            minX, maxY, minZ, command.topColor,
            maxX, maxY, minZ, command.topColor,
            maxX, maxY, maxZ, command.topColor,
            minX, maxY, maxZ, command.topColor
        )

        quad(
            builder, matrix,
            minX, minY, minZ, command.bottomColor,
            maxX, minY, minZ, command.bottomColor,
            maxX, maxY, minZ, command.topColor,
            minX, maxY, minZ, command.topColor
        )

        quad(
            builder, matrix,
            maxX, minY, minZ, command.bottomColor,
            maxX, minY, maxZ, command.bottomColor,
            maxX, maxY, maxZ, command.topColor,
            maxX, maxY, minZ, command.topColor
        )

        quad(
            builder, matrix,
            minX, minY, maxZ, command.bottomColor,
            minX, maxY, maxZ, command.topColor,
            maxX, maxY, maxZ, command.topColor,
            maxX, minY, maxZ, command.bottomColor
        )

        quad(
            builder, matrix,
            minX, minY, minZ, command.bottomColor,
            minX, maxY, minZ, command.topColor,
            minX, maxY, maxZ, command.topColor,
            minX, minY, maxZ, command.bottomColor
        )
    }

    private fun emitFilledSide(
        builder: LuminImmediateRenderer.PosColorQuads, matrix: Matrix4f, camPos: Vec3,
        command: FilledSideCommand,
    ) {
        val box = command.box
        val minX = (box.minX - camPos.x).toFloat()
        val minY = (box.minY - camPos.y).toFloat()
        val minZ = (box.minZ - camPos.z).toFloat()
        val maxX = (box.maxX - camPos.x).toFloat()
        val maxY = (box.maxY - camPos.y).toFloat()
        val maxZ = (box.maxZ - camPos.z).toFloat()

        when (command.direction) {
            Direction.DOWN -> quad(
                builder, matrix,
                minX, minY, minZ, command.color,
                maxX, minY, minZ, command.color,
                maxX, minY, maxZ, command.color,
                minX, minY, maxZ, command.color
            )

            Direction.NORTH -> quad(
                builder, matrix,
                minX, minY, minZ, command.color,
                minX, maxY, minZ, command.color,
                maxX, maxY, minZ, command.color,
                maxX, minY, minZ, command.color
            )

            Direction.EAST -> quad(
                builder, matrix,
                maxX, minY, minZ, command.color,
                maxX, maxY, minZ, command.color,
                maxX, maxY, maxZ, command.color,
                maxX, minY, maxZ, command.color
            )

            Direction.SOUTH -> quad(
                builder, matrix,
                minX, minY, maxZ, command.color,
                maxX, minY, maxZ, command.color,
                maxX, maxY, maxZ, command.color,
                minX, maxY, maxZ, command.color
            )

            Direction.WEST -> quad(
                builder, matrix,
                minX, minY, minZ, command.color,
                minX, minY, maxZ, command.color,
                minX, maxY, maxZ, command.color,
                minX, maxY, minZ, command.color
            )

            Direction.UP -> quad(
                builder, matrix,
                minX, maxY, minZ, command.color,
                minX, maxY, maxZ, command.color,
                maxX, maxY, maxZ, command.color,
                maxX, maxY, minZ, command.color
            )
        }
    }

    private fun emitOutlineBox(
        builder: LuminImmediateRenderer.Lines, matrix: Matrix4f, pose: PoseStack.Pose, camPos: Vec3,
        command: OutlineBoxCommand,
    ) {
        val box = command.box
        val minX = (box.minX - camPos.x).toFloat()
        val minY = (box.minY - camPos.y).toFloat()
        val minZ = (box.minZ - camPos.z).toFloat()
        val maxX = (box.maxX - camPos.x).toFloat()
        val maxY = (box.maxY - camPos.y).toFloat()
        val maxZ = (box.maxZ - camPos.z).toFloat()

        vertexLine(builder, matrix, pose, minX, minY, minZ, maxX, minY, minZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, minY, minZ, command.color, command.thickness)

        vertexLine(builder, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, command.color, command.thickness)

        vertexLine(builder, matrix, pose, minX, minY, minZ, minX, maxY, minZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, command.color, command.thickness)
        vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, command.color, command.thickness)
    }

    private fun emitSideOutline(
        builder: LuminImmediateRenderer.Lines, matrix: Matrix4f, pose: PoseStack.Pose, camPos: Vec3,
        command: SideOutlineCommand,
    ) {
        val box = command.box
        val minX = (box.minX - camPos.x).toFloat()
        val minY = (box.minY - camPos.y).toFloat()
        val minZ = (box.minZ - camPos.z).toFloat()
        val maxX = (box.maxX - camPos.x).toFloat()
        val maxY = (box.maxY - camPos.y).toFloat()
        val maxZ = (box.maxZ - camPos.z).toFloat()

        when (command.direction) {
            Direction.UP -> {
                vertexLine(builder, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, command.color, command.thickness)
            }

            Direction.DOWN -> {
                vertexLine(builder, matrix, pose, minX, minY, minZ, maxX, minY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, minY, minZ, command.color, command.thickness)
            }

            Direction.EAST -> {
                vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, maxY, maxZ, maxX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, minY, minZ, command.color, command.thickness)
            }

            Direction.WEST -> {
                vertexLine(builder, matrix, pose, minX, minY, minZ, minX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, minY, minZ, command.color, command.thickness)
            }

            Direction.NORTH -> {
                vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, minY, minZ, minX, maxY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, minY, minZ, minX, minY, minZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, maxY, minZ, minX, maxY, minZ, command.color, command.thickness)
            }

            Direction.SOUTH -> {
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, minY, maxZ, maxX, minY, maxZ, command.color, command.thickness)
                vertexLine(builder, matrix, pose, minX, maxY, maxZ, maxX, maxY, maxZ, command.color, command.thickness)
            }
        }
    }

    private fun emitLine(
        builder: LuminImmediateRenderer.Lines, matrix: Matrix4f, pose: PoseStack.Pose, camPos: Vec3,
        command: LineCommand,
    ) {
        val from = command.from.subtract(camPos)
        val to = command.to.subtract(camPos)
        vertexLine(
            builder, matrix, pose,
            from.x.toFloat(), from.y.toFloat(), from.z.toFloat(),
            to.x.toFloat(), to.y.toFloat(), to.z.toFloat(),
            command.color, command.thickness
        )
    }

    private fun quad(
        builder: LuminImmediateRenderer.PosColorQuads, matrix: Matrix4f,
        x1: Float, y1: Float, z1: Float, c1: Int,
        x2: Float, y2: Float, z2: Float, c2: Int,
        x3: Float, y3: Float, z3: Float, c3: Int,
        x4: Float, y4: Float, z4: Float, c4: Int,
    ) {
        builder.vertex(matrix, x1, y1, z1, c1)
        builder.vertex(matrix, x2, y2, z2, c2)
        builder.vertex(matrix, x3, y3, z3, c3)
        builder.vertex(matrix, x4, y4, z4, c4)
    }

    private fun vertexLine(
        builder: LuminImmediateRenderer.Lines, matrix: Matrix4f, pose: PoseStack.Pose,
        x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: Int, thickness: Float,
    ) {
        val normal = getNormal(x1, y1, z1, x2, y2, z2)
        builder.vertex(matrix, pose, x1, y1, z1, color, normal.x, normal.y, normal.z, thickness)
        builder.vertex(matrix, pose, x2, y2, z2, color, normal.x, normal.y, normal.z, thickness)
    }

    private fun getNormal(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Vector3f {
        val xNormal = x2 - x1
        val yNormal = y2 - y1
        val zNormal = z2 - z1
        val normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal)
        return Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt)
    }

    private data class BlurredBoxCommand(val box: AABB, val blurStrength: Double)

    private data class FilledBoxCommand(val box: AABB, val bottomColor: Int, val topColor: Int)

    private data class FilledSideCommand(val box: AABB, val color: Int, val direction: Direction)

    private data class OutlineBoxCommand(val box: AABB, val color: Int, val thickness: Float)

    private data class SideOutlineCommand(val box: AABB, val color: Int, val thickness: Float, val direction: Direction)

    private data class LineCommand(val from: Vec3, val to: Vec3, val color: Int, val thickness: Float)

    private val FILLED_BOX_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filled_box"))
        .withDepthStencilState(Optional.empty())
        .withCull(false)
        .build()

    private val LINES_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/lines"))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(Optional.empty())
        .withCull(false)
        .build()

}

