package com.github.epsilon.modules.impl.render

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.runSafe
import com.github.epsilon.utils.timer.TimerUtils
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.Camera
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.awt.Color
import java.util.*
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

object JumpCircle : Module("Jump Circle", Category.RENDER) {

    init {
        nonNullHandler<PlayerTickEvent.Pre> { onTick(it) }
        handler<Render3DEvent> { onRender3D(it) }
    }

    private enum class Mode {
        Default,
        Portal
    }

    private val mode by enumSetting("Mode", Mode.Default)
    private val easeOut by boolSetting("Ease Out", true)
    private val rotateSpeed by doubleSetting("Rotate Speed", 2.0, 0.5..5.0, 0.1)
    private val circleScale by doubleSetting("Circle Scale", 1.0, 0.5..5.0, 0.1)
    private val onlySelf by boolSetting("Only Self", false)

    private val circles: MutableList<Circle> = ArrayList()
    private val groundedCache: MutableList<UUID> = ArrayList()

    private val BUBBLE_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/hitbubble.png")
    private val CIRCLE_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/circle.png")

    private val JUMP_CIRCLE_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_jump_circle")
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    override fun onDisable() {
        circles.clear()
        groundedCache.clear()
    }

    private fun onTick(event: PlayerTickEvent.Pre) {
        runSafe {
            for (candidate in level.players()) {
                if (!shouldTrack(candidate)) {
                    groundedCache.remove(candidate.uuid)
                    continue
                }

                val id = candidate.uuid
                if (candidate.onGround()) {
                    if (!groundedCache.contains(id)) {
                        groundedCache.add(id)
                    }
                } else if (groundedCache.remove(id)) {
                    circles.add(
                        Circle(
                            Vec3(candidate.x, floor(candidate.y) + 0.001, candidate.z),
                            TimerUtils()
                        )
                    )
                }
            }
        }

        circles.removeAll { circle -> circle.timer.passedMillise(if (easeOut) 5000L else 6000L) }
    }

    private fun onRender3D(event: Render3DEvent) {
        if (circles.isEmpty()) return

        val poseStack = event.poseStack
        val texture = if (mode == Mode.Portal) BUBBLE_TEXTURE else CIRCLE_TEXTURE
        val renderer = LuminImmediateRenderer.beginPosTexColorQuads(JUMP_CIRCLE_PIPELINE, texture)

        circles.reverse()
        for (circle in circles) {
            renderCircle(poseStack, renderer, circle)
        }
        circles.reverse()

        renderer.end()
    }

    private fun shouldTrack(player: Player): Boolean =
        player.isAlive && (!onlySelf || player === mc.player)

    private fun renderCircle(
        poseStack: PoseStack,
        renderer: LuminImmediateRenderer.PosTexColorQuads,
        circle: Circle,
    ) {
        val colorAnim = circle.timer.getMs().toFloat() / 6000f
        val sizeProgress = 1f - (circle.timer.getMs() * (if (easeOut) 2f else 1f)) / 5000f
        val sizeAnim = circleScale.toFloat() - (sizeProgress.toDouble()).pow(4.0).toFloat()

        val camera: Camera = mc.gameRenderer.mainCamera()
        val pos = circle.pos

        poseStack.pushPose()
        poseStack.translate(pos.x - camera.position().x, pos.y - camera.position().y, pos.z - camera.position().z)
        poseStack.rotateDegrees(Axis.XP, 90.0f)
        poseStack.rotateDegrees(Axis.ZP, sizeAnim * rotateSpeed.toFloat() * 1000.0f)

        val scale = sizeAnim * 2.0f
        val matrix: Matrix4f = poseStack.last().pose()

        renderer.vertex(
            matrix,
            -sizeAnim,
            -sizeAnim + scale,
            0.0f,
            0.0f,
            1.0f,
            applyOpacity(syncColor(270), 1.0f - colorAnim).rgb
        )
        renderer.vertex(
            matrix,
            -sizeAnim + scale,
            -sizeAnim + scale,
            0.0f,
            1.0f,
            1.0f,
            applyOpacity(syncColor(0), 1.0f - colorAnim).rgb
        )
        renderer.vertex(
            matrix,
            -sizeAnim + scale,
            -sizeAnim,
            0.0f,
            1.0f,
            0.0f,
            applyOpacity(syncColor(180), 1.0f - colorAnim).rgb
        )
        renderer.vertex(
            matrix,
            -sizeAnim,
            -sizeAnim,
            0.0f,
            0.0f,
            0.0f,
            applyOpacity(syncColor(90), 1.0f - colorAnim).rgb
        )

        poseStack.popPose()
    }

    private fun syncColor(offset: Int): Color {
        val hue = Mth.frac((System.currentTimeMillis() + offset * 20L) / 4500.0f)
        val rainbow = Color.getHSBColor(hue, 0.65f, 1.0f)
        return Color(rainbow.red, rainbow.green, rainbow.blue, 255)
    }

    private fun applyOpacity(color: Color, opacity: Float): Color {
        val alpha = (color.alpha * opacity.coerceIn(0.0f, 1.0f)).roundToInt().coerceIn(0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

    private data class Circle(val pos: Vec3, val timer: TimerUtils)

}
