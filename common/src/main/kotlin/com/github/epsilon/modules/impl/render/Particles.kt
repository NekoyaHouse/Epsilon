package com.github.epsilon.modules.impl.render

import com.github.epsilon.assets.resources.ParticleAtlas
import com.github.epsilon.assets.resources.ParticleAtlas.Sprite
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.math.MathUtils
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import java.awt.Color
import kotlin.math.hypot

object Particles : Module("Particles", Category.RENDER) {

    private enum class Mode(val sprite: Sprite) {
        Star(Sprite.STAR),
        FireFly(Sprite.FIREFLY),
        Dollar(Sprite.DOLLAR),
        Heart(Sprite.HEART),
        SnowFlake(Sprite.SNOWFLAKE),
        Drop(Sprite.DROP),
        Cloud(Sprite.CLOUD)
    }

    private enum class ColorMode {
        Custom,
        Sync,
        RGB,
        Sky,
        LightRGB
    }

    private enum class Physics {
        Drop,
        Fly,
        FallingStars,
        Rain
    }

    private val mode by enumSetting("Mode", Mode.SnowFlake)
    private val colorMode by enumSetting("Color Mode", ColorMode.Sync)
    private val physics by enumSetting("Physics", Physics.Fly)
    private val color by colorSetting("Color", Color.WHITE, true, { colorMode == ColorMode.Custom })
    private val amount by intSetting("Amount", 10, 1..50, 1)
    private val lifetime by intSetting("Time", 256, 5..80, 1)
    private val size by doubleSetting("Size", 1.0, 0.1..1.5, 0.1)

    private val particles = ArrayList<Particle>()
    private val ripples = ArrayList<Ripple>()
    private val splashes = ArrayList<Splash>()

    private val particlePipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/particles"))
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build()

    private val trailPipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipeline/particle_trails"))
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build()

    init {
        onDisable {
            particles.clear()
            ripples.clear()
            splashes.clear()
        }
        safeEventHandler<PlayerTickEvent.Pre> {
            splashes.removeAll { it.tick() }
            particles.removeAll { it.tick(player.position()) }
            ripples.removeAll { it.age++ > 15 }

            val center = player.position().add(player.deltaMovement.scale(10.0))
            repeat(amount) {
                val particle = if (physics == Physics.Rain) {
                    Particle(
                        (center.x + MathUtils.getRandom(-48.0f, 48.0f)).toFloat(),
                        (center.y + 24 + MathUtils.getRandom(-2.0f, 2.0f)).toFloat(),
                        (center.z + MathUtils.getRandom(-48.0f, 48.0f)).toFloat(),
                        0.0f, MathUtils.getRandom(-0.3f, -0.1f), 0.0f,
                        MathUtils.getRandom(0.0f, 360.0f), MathUtils.getRandom(3.0f, 10.0f)
                    )
                } else {
                    Particle(
                        (center.x + MathUtils.getRandom(-64.0f, 64.0f) * 2).toFloat(),
                        (center.y + MathUtils.getRandom(2.0f, 48.0f)).toFloat(),
                        (center.z + MathUtils.getRandom(-64.0f, 64.0f) * 2).toFloat(),
                        when (physics) {
                            Physics.Drop -> 0.0f
                            Physics.Fly -> MathUtils.getRandom(-0.4f, 0.4f)
                            else -> MathUtils.getRandom(0.1f, 0.25f)
                        },
                        when (physics) {
                            Physics.Drop -> MathUtils.getRandom(-0.2f, -0.05f)
                            Physics.Fly -> MathUtils.getRandom(-0.1f, 0.1f)
                            else -> MathUtils.getRandom(-0.4f, -0.1f)
                        },
                        when (physics) {
                            Physics.Drop -> 0.0f
                            Physics.Fly -> MathUtils.getRandom(-0.4f, 0.4f)
                            else -> MathUtils.getRandom(0.1f, 0.25f)
                        },
                        0.0f, 0.0f
                    )
                }
                val start = Vec3(particle.x.toDouble(), particle.y.toDouble(), particle.z.toDouble())
                val hit = level.clip(
                    ClipContext(
                        start,
                        start.add(0.0, -128.0, 0.0),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        CollisionContext.empty()
                    )
                )
                particle.groundY =
                    if (hit.type == HitResult.Type.BLOCK) hit.blockPos.above().y.toFloat() else particle.y
                particles.add(particle)
            }
        }
        safeEventHandler<Render3DEvent> { event ->
            if (particles.isEmpty() && ripples.isEmpty() && splashes.isEmpty()) return@safeEventHandler

            val stack = event.poseStack
            val camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val quads = LuminImmediateRenderer.beginPosTexColorQuads(particlePipeline, ParticleAtlas.texture)
            val lines = LuminImmediateRenderer.beginLines(trailPipeline)

            for (particle in particles) {
                if (!particle.visible || !camera.cullFrustum.pointInFrustum(
                        particle.x.toDouble(), particle.y.toDouble(), particle.z.toDouble()
                    )
                ) continue

                val x = Mth.lerp(partialTick, particle.prevX, particle.x).toDouble() - camera.pos.x
                val y = Mth.lerp(partialTick, particle.prevY, particle.y).toDouble() - camera.pos.y
                val z = Mth.lerp(partialTick, particle.prevZ, particle.z).toDouble() - camera.pos.z
                val particleColor = if (physics == Physics.Rain && !particle.bounced) {
                    particle.color.rgb
                } else {
                    withAlpha(particle.color, particle.remaining.toFloat() / particle.initialLifetime)
                }

                stack.pushPose()
                stack.translate(x, y, z)
                stack.rotateDegrees(Axis.YP, -camera.yRot)
                stack.rotateDegrees(Axis.XP, camera.xRot)
                if (physics == Physics.Rain) {
                    stack.rotateDegrees(Axis.ZP, Mth.lerp(partialTick, particle.prevRotation, particle.rotation))
                }
                drawQuad(stack, quads, mode.sprite, size.toFloat(), particleColor)
                stack.popPose()

                if (physics == Physics.Rain && !particle.bounced) {
                    val alpha = (particle.remaining / 20.0f).coerceIn(0.0f, 1.0f) / 3.0f
                    val pose = stack.last()
                    lines.vertex(
                        pose.pose(), pose, x.toFloat(), y.toFloat(), z.toFloat(),
                        withAlpha(particle.color, alpha), 0.0f, 1.0f, 0.0f, 1.0f
                    )
                    lines.vertex(
                        pose.pose(), pose, x.toFloat(), (y + 3.0).toFloat(), z.toFloat(),
                        withAlpha(particle.color, 0.0f), 0.0f, 1.0f, 0.0f, 1.0f
                    )
                }
            }

            for (ripple in ripples) {
                if (!camera.cullFrustum.pointInFrustum(
                        ripple.x.toDouble(),
                        ripple.y.toDouble(),
                        ripple.z.toDouble()
                    )
                ) continue
                val age = Mth.lerp(partialTick, maxOf(1, ripple.age - 1).toFloat(), ripple.age.toFloat())
                stack.pushPose()
                stack.translate(
                    ripple.x.toDouble() - camera.pos.x, ripple.y.toDouble() - camera.pos.y,
                    ripple.z.toDouble() - camera.pos.z
                )
                stack.rotateDegrees(Axis.YP, -camera.yRot)
                stack.rotateDegrees(Axis.XP, 90.0f)
                drawQuad(stack, quads, Sprite.CIRCLE, 0.5f + age / 15.0f, withAlpha(ripple.color, 1.0f - age / 15.0f))
                stack.popPose()
            }

            for (splash in splashes) {
                if (!camera.cullFrustum.pointInFrustum(
                        splash.position.x,
                        splash.position.y,
                        splash.position.z
                    )
                ) continue
                val position =
                    splash.previousPosition.lerp(splash.position, partialTick.toDouble()).subtract(camera.pos)
                val progress = Mth.lerp(partialTick, splash.remaining + 1.0f, splash.remaining.toFloat()) / 8.0f
                stack.pushPose()
                stack.translate(position)
                stack.rotateDegrees(Axis.YP, -camera.yRot)
                stack.rotateDegrees(Axis.XP, camera.xRot)
                drawQuad(stack, quads, Mode.FireFly.sprite, 0.1f * progress, withAlpha(splash.color, progress))
                stack.popPose()
            }

            quads.end()
            lines.end()
        }
    }

    private fun drawQuad(
        stack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads,
        sprite: Sprite, size: Float, color: Int
    ) {
        stack.translate(-size / 2.0, -size / 2.0, -0.01)
        val matrix = stack.last().pose()
        renderer.vertex(matrix, 0.0f, size, 0.0f, sprite.u1, sprite.v0, color)
        renderer.vertex(matrix, size, size, 0.0f, sprite.u0, sprite.v0, color)
        renderer.vertex(matrix, size, 0.0f, 0.0f, sprite.u0, sprite.v1, color)
        renderer.vertex(matrix, 0.0f, 0.0f, 0.0f, sprite.u1, sprite.v1, color)
    }

    private fun resolveColor(offset: Int): Color {
        if (colorMode == ColorMode.Custom) return color
        if (colorMode == ColorMode.Sync) return syncColor(offset)
        val hue = ((System.currentTimeMillis() / 18 + offset) % 360) / 360.0f
        val saturation = when (colorMode) {
            ColorMode.Sky -> 0.5f
            ColorMode.LightRGB -> 0.6f
            else -> 1.0f
        }
        return Color.getHSBColor(hue, saturation, 1.0f)
    }

    private fun syncColor(offset: Int): Color {
        val angle = ((System.currentTimeMillis() / 14.0 + offset) % 360).toInt()
        val progress = (if (angle < 180) angle else 360 - angle) / 360.0f
        val primary = MD3Theme.PRIMARY
        val secondary = MD3Theme.SECONDARY
        return Color(
            Mth.lerp(progress, primary.red.toFloat(), secondary.red.toFloat()).toInt(),
            Mth.lerp(progress, primary.green.toFloat(), secondary.green.toFloat()).toInt(),
            Mth.lerp(progress, primary.blue.toFloat(), secondary.blue.toFloat()).toInt(),
            Mth.lerp(progress, primary.alpha.toFloat(), secondary.alpha.toFloat()).toInt()
        )
    }

    private fun withAlpha(color: Color, alpha: Float): Int =
        ((255.0f * alpha.coerceIn(0.0f, 1.0f)).toInt() shl 24) or (color.rgb and 0xFFFFFF)

    private class Particle(
        var x: Float, var y: Float, var z: Float,
        var motionX: Float, var motionY: Float, var motionZ: Float,
        var rotation: Float, var rotationSpeed: Float
    ) {
        var prevX = x
        var prevY = y
        var prevZ = z
        var prevRotation = rotation
        val initialLifetime = if (physics == Physics.Rain) 80 else
            (lifetime + lifetime * MathUtils.getRandom(0.0f, 0.5f)).toInt()
        var remaining = initialLifetime
        var bounced = false
        var visible = false
        val color = resolveColor((360 * MathUtils.getRandom(0.0f, 1.0f)).toInt())
        var groundY = y

        fun tick(playerPosition: Vec3): Boolean {
            remaining -= if (playerPosition.distanceToSqr(
                    x.toDouble(),
                    y.toDouble(),
                    z.toDouble()
                ) > 4096.0 || bounced
            ) 16 else 1
            if (remaining < 0) return true

            prevX = x
            prevY = y
            prevZ = z
            x += motionX
            y += motionY
            z += motionZ
            prevRotation = rotation
            rotation += rotationSpeed

            if (physics != Physics.FallingStars) {
                motionX *= 0.9f
                motionZ *= 0.9f
            }
            if (physics == Physics.Fly || physics == Physics.Rain && bounced) motionY *= 0.9f
            motionY -= when {
                physics == Physics.Rain && bounced -> 0.08f
                physics == Physics.Rain -> 0.02f
                else -> 0.001f
            }

            if (y - size.toFloat() < groundY && !bounced && physics == Physics.Rain) {
                motionY = -motionY / 4.0f
                bounced = true
                remaining = 128
                motionX = MathUtils.getRandom(-0.2f, 0.2f)
                motionZ = MathUtils.getRandom(-0.2f, 0.2f)
                repeat(5) {
                    splashes.add(
                        Splash(
                            Vec3(x.toDouble(), groundY.toDouble(), z.toDouble()),
                            Vec3(Math.random() / 2.0 - 0.25, Math.random() / 4.0, Math.random() / 2.0 - 0.25),
                            syncColor(it * 10)
                        )
                    )
                }
                rotationSpeed = MathUtils.getRandom(-20.0f, 20.0f)
                ripples.add(Ripple(x, groundY, z, color))
            }

            visible = hypot(x - playerPosition.x, z - playerPosition.z) <
                    if (physics == Physics.Rain) 1024.0 else 2304.0
            return false
        }
    }

    private class Ripple(val x: Float, val y: Float, val z: Float, val color: Color, var age: Int = 0)

    private class Splash(var position: Vec3, var velocity: Vec3, val color: Color) {
        var previousPosition = position
        var remaining = 8

        fun tick(): Boolean {
            if (--remaining < 0) return true
            previousPosition = position
            position = position.add(velocity)
            velocity = velocity.scale(0.99).add(0.0, -0.03, 0.0)
            return false
        }
    }

}
