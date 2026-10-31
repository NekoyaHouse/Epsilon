package com.github.epsilon.modules.impl.render

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.WorldToScreen
import com.github.epsilon.kernel.jvm.runSafe
import com.google.common.base.Suppliers
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.Mth
import net.minecraft.world.entity.item.PrimedTnt
import org.joml.Matrix4f
import java.awt.Color
import java.util.*
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object TNTTimer : Module("TNT Timer", Category.RENDER) {

    private val dangerRadius by doubleSetting("Danger Radius", 7.0, 0.5..12.0, 0.1)
    private val lethalRadius by doubleSetting("Lethal Radius", 4.0, 0.5..12.0, 0.1)
    private val outerThickness by doubleSetting("Outer Thickness", 0.12, 0.01..1.0, 0.01)
    private val innerThickness by doubleSetting("Inner Thickness", 0.16, 0.01..1.0, 0.01)
    private val alphaSetting by intSetting("Alpha", 16, 16..255, 1)
    private val segmentsSetting by intSetting("Segments", 360, 16..360, 1)
    private val ringsSetting by intSetting("Sphere Rings", 10, 2..64, 1)
    private val filledSphere by boolSetting("Filled Sphere", true)
    private val wireframeSphere by boolSetting("Wireframe Sphere", false)
    private val pulse by boolSetting("Pulse Near Explosion", true)
    private val pulseThresholdSec by doubleSetting("Pulse Threshold (s)", 2.0, 0.2..10.0, 0.1)
    private val pulseAmplitude by doubleSetting("Pulse Amplitude", 0.12, 0.0..0.5, 0.01)
    private val pulseSpeedHz by doubleSetting("Pulse Speed (Hz)", 2.0, 0.2..8.0, 0.1)

    private val scheduler: Supplier<Render2DScheduler> = Suppliers.memoize { Render2DScheduler() }

    private val FILLED_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/tnt_timer_filled"))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .build()

    private val LINES_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/tnt_timer_lines"))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    init {
        handler<Render2DEvent.Level> { onRender2D(it) }
        handler<Render3DEvent> { onRender3D(it) }
    }

    private fun onRender2D(event: Render2DEvent.Level) {
        runSafe {
            val render = scheduler.get()
            render.clear()
            val layer = render.layer(0)
            val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            for (entity in level.entitiesForRendering()) {
                if (entity !is PrimedTnt) continue
                val anchor = entity.getPosition(partial).add(0.0, entity.bbHeight + 0.4, 0.0)
                val screen = WorldToScreen.calcWorld2Screen(anchor) ?: continue
                if (!screen.x.isFinite() || !screen.y.isFinite()) continue
                val label = String.format(Locale.US, "%.1fs", maxOf(0, entity.fuse) / 20.0f)
                val scale = maxOf(0.45f, WorldToScreen.calcScale(anchor) * 0.7f)
                val width = render.textMetrics().getWidth(label, scale)
                layer.addText(
                    label,
                    screen.x - width * 0.5f,
                    screen.y,
                    scale,
                    Color.WHITE,
                    StaticFontLoader.defaultFont()
                )
            }
            render.flushAndClear()
        }
    }

    private fun onRender3D(event: Render3DEvent) {
        runSafe {
            val stack = event.poseStack
            val camera = mc.gameRenderer.mainCamera().position()
            val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val segments = segmentsSetting
            val rings = ringsSetting
            val alpha = alphaSetting.coerceIn(0, 255)
            val lines = LuminImmediateRenderer.beginLines(LINES_PIPELINE)
            val quads = if (filledSphere) LuminImmediateRenderer.beginPosColorQuads(FILLED_PIPELINE) else null
            val matrix = stack.last().pose()
            val pose = stack.last()

            for (entity in level.entitiesForRendering()) {
                if (entity !is PrimedTnt) continue
                val fuse = entity.fuse.toFloat()
                val norm = (fuse / 80.0f).coerceIn(0.0f, 1.0f)
                val color = fuseColor(norm, alpha)
                val scale = pulseScale(fuse, pt)
                val x = Mth.lerp(pt.toDouble(), entity.xOld, entity.x) - camera.x
                val y = Mth.lerp(pt.toDouble(), entity.yOld, entity.y) - camera.y
                val z = Mth.lerp(pt.toDouble(), entity.zOld, entity.z) - camera.z
                drawRing(
                    lines, matrix, pose, x, y + 0.01, z,
                    lethalRadius.toFloat(), innerThickness.toFloat(), segments, (alpha shl 24) or 0xFF301F
                )
                drawRing(
                    lines, matrix, pose, x, y + 0.01, z,
                    dangerRadius.toFloat() * scale, outerThickness.toFloat(), segments, color
                )
                if (quads != null) {
                    val radius = dangerRadius.toFloat() * scale
                    drawSphere(quads, matrix, x, y + entity.bbHeight * 0.5, z, radius, rings, segments, color)
                    drawSphere(
                        quads, matrix, x, y + entity.bbHeight * 0.5, z, radius * 0.96f, rings, segments,
                        withAlpha(color, (alpha * 0.6f).toInt().coerceIn(0, 255))
                    )
                    drawSphere(
                        quads, matrix, x, y + entity.bbHeight * 0.5, z, radius * 0.92f, rings, segments,
                        withAlpha(color, (alpha * 0.35f).toInt().coerceIn(0, 255))
                    )
                }
                if (wireframeSphere) {
                    drawWireSphere(
                        lines, matrix, pose, x, y + entity.bbHeight * 0.5, z,
                        dangerRadius.toFloat() * scale, rings, segments, color
                    )
                }
            }

            lines.end()
            quads?.end()
        }
    }

    private fun pulseScale(fuse: Float, pt: Float): Float {
        if (!pulse) return 1.0f
        val fade = (1.0f - (fuse / 20.0f) / pulseThresholdSec.toFloat()).coerceIn(0.0f, 1.0f)
        val gameTime = mc.level?.gameTime ?: 0L
        val phase = (gameTime + pt) / 20.0f * pulseSpeedHz.toFloat() * Mth.TWO_PI
        return 1.0f + Mth.sin(phase.toDouble()) * pulseAmplitude.toFloat() * fade
    }

    private fun fuseColor(norm: Float, alpha: Int): Int {
        val red = (alpha shl 24) or 0xFF3B1F
        val yellow = (alpha shl 24) or 0xFFD400
        val green = (alpha shl 24) or 0x42FF66
        return lerp(
            if (norm < 0.5f) red else yellow,
            if (norm < 0.5f) yellow else green,
            if (norm < 0.5f) norm * 2.0f else (norm - 0.5f) * 2.0f
        )
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val clamped = t.coerceIn(0.0f, 1.0f)
        val aa = (a ushr 24) and 255
        val ar = (a ushr 16) and 255
        val ag = (a ushr 8) and 255
        val ab = a and 255
        val ba = (b ushr 24) and 255
        val br = (b ushr 16) and 255
        val bg = (b ushr 8) and 255
        val bb = b and 255
        return (Mth.lerp(clamped, aa.toFloat(), ba.toFloat()).toInt() shl 24) or
                (Mth.lerp(clamped, ar.toFloat(), br.toFloat()).toInt() shl 16) or
                (Mth.lerp(clamped, ag.toFloat(), bg.toFloat()).toInt() shl 8) or
                Mth.lerp(clamped, ab.toFloat(), bb.toFloat()).toInt()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun drawRing(
        out: LuminImmediateRenderer.Lines, m: Matrix4f, pose: PoseStack.Pose,
        x: Double, y: Double, z: Double, radius: Float, thickness: Float, segments: Int, color: Int,
    ) {
        for (i in 0 until segments) {
            val a0 = i * PI * 2.0 / segments
            val a1 = (i + 1) * PI * 2.0 / segments
            val x0 = (x + cos(a0) * radius).toFloat()
            val z0 = (z + sin(a0) * radius).toFloat()
            val x1 = (x + cos(a1) * radius).toFloat()
            val z1 = (z + sin(a1) * radius).toFloat()
            val dx = x1 - x0
            val dz = z1 - z0
            val len = Mth.sqrt(dx * dx + dz * dz)
            out.vertex(m, pose, x0, y.toFloat(), z0, color, dx / len, 0.0f, dz / len, thickness)
            out.vertex(m, pose, x1, y.toFloat(), z1, color, dx / len, 0.0f, dz / len, thickness)
        }
    }

    private fun drawSphere(
        out: LuminImmediateRenderer.PosColorQuads, m: Matrix4f,
        x: Double, y: Double, z: Double, radius: Float, rings: Int, segments: Int, color: Int,
    ) {
        for (r in 0 until rings) {
            val p0 = PI * r / rings - PI / 2.0
            val p1 = PI * (r + 1) / rings - PI / 2.0
            for (s in 0 until segments) {
                val a0 = PI * 2.0 * s / segments
                val a1 = PI * 2.0 * (s + 1) / segments
                val v0 = sphereVertex(x, y, z, radius, p0, a0)
                val v1 = sphereVertex(x, y, z, radius, p0, a1)
                val v2 = sphereVertex(x, y, z, radius, p1, a1)
                val v3 = sphereVertex(x, y, z, radius, p1, a0)
                out.vertex(m, v0[0], v0[1], v0[2], color)
                out.vertex(m, v1[0], v1[1], v1[2], color)
                out.vertex(m, v2[0], v2[1], v2[2], color)
                out.vertex(m, v3[0], v3[1], v3[2], color)
            }
        }
    }

    private fun drawWireSphere(
        out: LuminImmediateRenderer.Lines, m: Matrix4f, pose: PoseStack.Pose,
        x: Double, y: Double, z: Double, radius: Float, rings: Int, segments: Int, color: Int,
    ) {
        for (r in 1 until rings) {
            val p = PI * r / rings - PI / 2.0
            val h = cos(p) * radius
            val yy = y + sin(p) * radius
            drawRing(out, m, pose, x, yy, z, h.toFloat(), 1.0f, segments, color)
        }
        for (s in 0 until segments) {
            val a = PI * 2.0 * s / segments
            val px = (x + cos(a) * radius).toFloat()
            val pz = (z + sin(a) * radius).toFloat()
            out.vertex(m, pose, px, (y - radius).toFloat(), pz, color, 0.0f, 1.0f, 0.0f, 1.0f)
            out.vertex(m, pose, px, (y + radius).toFloat(), pz, color, 0.0f, 1.0f, 0.0f, 1.0f)
        }
    }

    private fun sphereVertex(x: Double, y: Double, z: Double, radius: Float, pitch: Double, yaw: Double): FloatArray {
        val cp = cos(pitch).toFloat()
        return floatArrayOf(
            (x + radius * cp * cos(yaw)).toFloat(),
            (y + radius * sin(pitch)).toFloat(),
            (z + radius * cp * sin(yaw)).toFloat()
        )
    }

}
