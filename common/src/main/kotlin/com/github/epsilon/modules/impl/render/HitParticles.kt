package com.github.epsilon.modules.impl.render

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.math.MathUtils
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.Camera
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.awt.Color
import java.util.ArrayList

object HitParticles : Module("Hit Particles", Category.RENDER) {

    private enum class Physics {
        Fall,
        Fly
    }

    private enum class Mode {
        Stars,
        Hearts,
        Bloom
    }

    private enum class ColorMode {
        Custom,
        Sync
    }

    private val mode: EnumSetting<Mode> = enumSetting("Mode", Mode.Stars)
    private val physics: EnumSetting<Physics> = enumSetting("Physics", Physics.Fall)
    private val colorMode: EnumSetting<ColorMode> = enumSetting("Color Mode", ColorMode.Sync)
    private val color by colorSetting(
        "Color", Color(255, 255, 255, 53), true, dependency = { colorMode.`is`(ColorMode.Custom) }
    )
    private val onlySelf by boolSetting("Only Self", false)
    private val amount by intSetting("Amount", 2, 1, 5, 1)
    private val lifeTime by intSetting("Life Time", 2, 1, 10, 1)
    private val speed by intSetting("Speed", 2, 1, 20, 1)
    private val scale by doubleSetting("Scale", 3.0, 1.0, 10.0, 0.1)

    private val particles: MutableList<Particle> = ArrayList()

    private val STAR_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/star.png")
    private val HEART_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/heart.png")
    private val BLOOM_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/firefly.png")

    private val HIT_PARTICLE_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_hit_particles")
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            particles.removeAll { particle -> particle.tick() }

            for (entity in level.entitiesForRendering()) {
                if (entity !is LivingEntity) continue
                if (onlySelf && entity !== player) continue
                if (entity.hurtTime <= 0) continue

                val particleColor = resolveColor(MathUtils.getRandom(1.0f, 228.0f).toInt())
                for (i in 0 until amount) {
                    particles.add(
                        Particle(
                            entity.x.toFloat(),
                            MathUtils.getRandom(entity.y.toFloat(), (entity.y + entity.bbHeight).toFloat()),
                            entity.z.toFloat(),
                            particleColor,
                            MathUtils.getRandom(0.0f, 180.0f),
                            MathUtils.getRandom(10.0f, 60.0f)
                        )
                    )
                }
            }
        }

        handler<Render3DEvent> { event ->
            if (particles.isEmpty()) return@handler

            val texture = when (mode.value) {
                Mode.Stars -> STAR_TEXTURE
                Mode.Hearts -> HEART_TEXTURE
                Mode.Bloom -> BLOOM_TEXTURE
            }
            val renderer = LuminImmediateRenderer.beginPosTexColorQuads(HIT_PARTICLE_PIPELINE, texture)
            for (particle in particles) {
                particle.renderTexture(event.poseStack, renderer)
            }
            renderer.end()
        }
    }

    override fun onDisable() {
        particles.clear()
    }

    private fun resolveColor(offset: Int): Color =
        if (colorMode.`is`(ColorMode.Sync)) syncColor(offset) else color

    private fun syncColor(offset: Int): Color {
        val hue = Mth.frac((System.currentTimeMillis() + offset * 20L) / 4500.0f)
        val rainbow = Color.getHSBColor(hue, 0.65f, 1.0f)
        return Color(rainbow.red, rainbow.green, rainbow.blue, 255)
    }

    private class Particle(
        private var x: Float,
        private var y: Float,
        private var z: Float,
        private val particleColor: Color,
        private var rotationAngle: Float,
        private val rotationSpeed: Float,
    ) {

        private var prevX: Float = x
        private var prevY: Float = y
        private var prevZ: Float = z
        private var motionX: Float =
            MathUtils.getRandom(-HitParticles.speed / 50.0f, HitParticles.speed / 50.0f)

        private var motionY: Float =
            MathUtils.getRandom(-HitParticles.speed / 50.0f, HitParticles.speed / 50.0f)

        private var motionZ: Float =
            MathUtils.getRandom(-HitParticles.speed / 50.0f, HitParticles.speed / 50.0f)

        private val spawnTime: Long = System.currentTimeMillis()
        private var lastRenderTime: Long = spawnTime

        fun tick(): Boolean {
            val horizontalSpeed = Math.sqrt((motionX * motionX + motionZ * motionZ).toDouble())

            prevX = x
            prevY = y
            prevZ = z

            x += motionX
            y += motionY
            z += motionZ

            if (isSolidBlock(x.toDouble(), (y - HitParticles.scale.toFloat() / 10.0f).toDouble(), z.toDouble())) {
                motionY = -motionY / 1.1f
                motionX /= 1.1f
                motionZ /= 1.1f
            } else if (isSolidBlock((x - horizontalSpeed).toDouble(), y.toDouble(), (z - horizontalSpeed).toDouble()) ||
                isSolidBlock((x + horizontalSpeed).toDouble(), y.toDouble(), (z + horizontalSpeed).toDouble()) ||
                isSolidBlock((x + horizontalSpeed).toDouble(), y.toDouble(), (z - horizontalSpeed).toDouble()) ||
                isSolidBlock((x - horizontalSpeed).toDouble(), y.toDouble(), (z + horizontalSpeed).toDouble()) ||
                isSolidBlock((x + horizontalSpeed).toDouble(), y.toDouble(), z.toDouble()) ||
                isSolidBlock((x - horizontalSpeed).toDouble(), y.toDouble(), z.toDouble()) ||
                isSolidBlock(x.toDouble(), y.toDouble(), (z + horizontalSpeed).toDouble()) ||
                isSolidBlock(x.toDouble(), y.toDouble(), (z - horizontalSpeed).toDouble())
            ) {
                motionX = -motionX
                motionZ = -motionZ
            }

            if (HitParticles.physics.`is`(Physics.Fall)) {
                motionY -= 0.035f
            }

            motionX /= 1.005f
            motionY /= 1.005f
            motionZ /= 1.005f

            return System.currentTimeMillis() - spawnTime > HitParticles.lifeTime * 1000L
        }

        fun renderTexture(poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads) {
            val partialTick = Constants.client.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val particleScale = 0.07f
            val size = HitParticles.scale.toFloat()
            val position = interpolatedCameraRelativePosition(partialTick)

            poseStack.pushPose()
            poseStack.translate(position.x, position.y + 0.1, position.z)
            poseStack.scale(particleScale, particleScale, particleScale)
            poseStack.translate(size / 2.0f, size / 2.0f, size / 2.0f)
            applyCameraRotation(poseStack)
            poseStack.rotateDegrees(Axis.ZP, nextRotation())
            poseStack.translate(-size / 2.0f, -size / 2.0f, -size / 2.0f)

            val matrix: Matrix4f = poseStack.last().pose()
            val argb = particleColor.rgb

            renderer.vertex(matrix, 0.0f, size, 0.0f, 0.0f, 1.0f, argb)
            renderer.vertex(matrix, size, size, 0.0f, 1.0f, 1.0f, argb)
            renderer.vertex(matrix, size, 0.0f, 0.0f, 1.0f, 0.0f, argb)
            renderer.vertex(matrix, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, argb)

            poseStack.popPose()
        }

        private fun interpolatedCameraRelativePosition(partialTick: Float): Vec3 {
            val camera: Camera = Constants.client.gameRenderer.mainCamera()
            val renderX = Mth.lerp(partialTick.toDouble(), prevX.toDouble(), x.toDouble()) - camera.position().x
            val renderY = Mth.lerp(partialTick.toDouble(), prevY.toDouble(), y.toDouble()) - camera.position().y
            val renderZ = Mth.lerp(partialTick.toDouble(), prevZ.toDouble(), z.toDouble()) - camera.position().z
            return Vec3(renderX, renderY, renderZ)
        }

        private fun applyCameraRotation(poseStack: PoseStack) {
            val camera = Constants.client.gameRenderer.mainCamera()
            poseStack.rotateDegrees(Axis.YP, -camera.yRot())
            poseStack.rotateDegrees(Axis.XP, camera.xRot())
        }

        private fun nextRotation(): Float {
            val now = System.currentTimeMillis()
            val delta = Math.max(0.0f, (now - lastRenderTime) / 1000.0f)
            lastRenderTime = now
            rotationAngle += delta * rotationSpeed
            return rotationAngle
        }

        private fun isSolidBlock(x: Double, y: Double, z: Double): Boolean {
            val level = Constants.client.level ?: return false
            val state = level.getBlockState(BlockPos.containing(x, y, z))
            return !state.isAir && !state.`is`(Blocks.WATER) && !state.`is`(Blocks.LAVA)
        }

    }

}
