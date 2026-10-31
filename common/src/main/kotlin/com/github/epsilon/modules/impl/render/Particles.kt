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
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.awt.Color
import java.util.ArrayList

object Particles : Module("Particles", Category.RENDER) {

    private enum class ColorMode {
        Custom,
        Sync
    }

    private enum class Mode {
        Off,
        SnowFlake,
        Stars,
        Hearts,
        Dollars,
        Bloom
    }

    private enum class Physics {
        Drop,
        Fly
    }

    private val fireFliesEnabled by boolSetting("Fire Flies", true)
    private val ffCount by intSetting(
        "FF Count", 30, 20, 200, 1, dependency = { fireFliesEnabled }
    )
    private val ffSize by doubleSetting(
        "FF Size", 1.0, 0.1, 2.0, 0.1, dependency = { fireFliesEnabled }
    )
    private val mode: EnumSetting<Mode> = enumSetting("Mode", Mode.SnowFlake)
    private val count by intSetting(
        "Count", 100, 20, 800, 1, dependency = { !mode.`is`(Mode.Off) }
    )
    private val size by doubleSetting(
        "Size", 1.0, 0.1, 6.0, 0.1, dependency = { !mode.`is`(Mode.Off) }
    )
    private val colorMode: EnumSetting<ColorMode> = enumSetting("Color Mode", ColorMode.Sync)
    private val color by colorSetting(
        "Color", Color(3649978), dependency = { colorMode.`is`(ColorMode.Custom) }
    )
    private val physics: EnumSetting<Physics> = enumSetting(
        "Physics", Physics.Fly, dependency = { !mode.`is`(Mode.Off) }
    )

    private val fireFlies: MutableList<ParticleBase> = ArrayList()
    private val particles: MutableList<ParticleBase> = ArrayList()

    private val FIREFLY_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/firefly.png")
    private val SNOWFLAKE_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/snowflake.png")
    private val STAR_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/star.png")
    private val HEART_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/heart.png")
    private val DOLLAR_TEXTURE: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/dollar.png")

    private val PARTICLE_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_particles")
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build()

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            fireFlies.removeAll { particle -> particle.tick() }
            particles.removeAll { particle -> particle.tick() }

            var i = fireFlies.size
            while (i < ffCount) {
                if (fireFliesEnabled) {
                    fireFlies.add(
                        FireFly(
                            (player.x + MathUtils.getRandom(-25.0f, 25.0f)).toFloat(),
                            (player.y + MathUtils.getRandom(2.0f, 15.0f)).toFloat(),
                            (player.z + MathUtils.getRandom(-25.0f, 25.0f)).toFloat(),
                            MathUtils.getRandom(-0.2f, 0.2f),
                            MathUtils.getRandom(-0.1f, 0.1f),
                            MathUtils.getRandom(-0.2f, 0.2f)
                        )
                    )
                }
                i++
            }

            i = particles.size
            while (i < count) {
                val drop = physics.`is`(Physics.Drop)
                if (!mode.`is`(Mode.Off)) {
                    particles.add(
                        ParticleBase(
                            (player.x + MathUtils.getRandom(-48.0f, 48.0f)).toFloat(),
                            (player.y + MathUtils.getRandom(2.0f, 48.0f)).toFloat(),
                            (player.z + MathUtils.getRandom(-48.0f, 48.0f)).toFloat(),
                            if (drop) 0.0f else MathUtils.getRandom(-0.4f, 0.4f),
                            if (drop) MathUtils.getRandom(-0.2f, -0.05f) else MathUtils.getRandom(-0.1f, 0.1f),
                            if (drop) 0.0f else MathUtils.getRandom(-0.4f, 0.4f)
                        )
                    )
                }
                i++
            }
        }

        handler<Render3DEvent> { event ->
            if (fireFliesEnabled && fireFlies.isNotEmpty()) {
                renderParticleList(event.poseStack, fireFlies, FIREFLY_TEXTURE)
            }

            if (!mode.`is`(Mode.Off) && particles.isNotEmpty()) {
                textureForMode(mode.value)?.let { renderParticleList(event.poseStack, particles, it) }
            }
        }
    }

    override fun onDisable() {
        fireFlies.clear()
        particles.clear()
    }

    private fun renderParticleList(
        poseStack: PoseStack, list: List<ParticleBase>, texture: Identifier,
    ) {
        val renderer = LuminImmediateRenderer.beginPosTexColorQuads(PARTICLE_PIPELINE, texture)
        for (particle in list) {
            particle.render(poseStack, renderer)
        }
        renderer.end()
    }

    private fun textureForMode(mode: Mode): Identifier? = when (mode) {
        Mode.Off -> null
        Mode.Bloom -> FIREFLY_TEXTURE
        Mode.SnowFlake -> SNOWFLAKE_TEXTURE
        Mode.Stars -> STAR_TEXTURE
        Mode.Hearts -> HEART_TEXTURE
        Mode.Dollars -> DOLLAR_TEXTURE
    }

    private fun resolveColor(offset: Int): Color =
        if (colorMode.`is`(ColorMode.Sync)) syncColor(offset) else color

    private fun syncColor(offset: Int): Color {
        val hue = Mth.frac((System.currentTimeMillis() + offset * 20L) / 4500.0f)
        val rainbow = Color.getHSBColor(hue, 0.65f, 1.0f)
        return Color(rainbow.red, rainbow.green, rainbow.blue, 255)
    }

    private fun withAlpha(source: Color, alpha: Int): Color =
        Color(source.red, source.green, source.blue, Mth.clamp(alpha, 0, 255))

    private class FireFly(
        posX: Float, posY: Float, posZ: Float,
        motionX: Float, motionY: Float, motionZ: Float,
    ) : ParticleBase(posX, posY, posZ, motionX, motionY, motionZ) {

        private val trails: MutableList<Trail> = ArrayList()

        override fun tick(): Boolean {
            val mc = Constants.client
            if (Constants.client.player!!.distanceToSqr(posX.toDouble(), posY.toDouble(), posZ.toDouble()) > 100.0) {
                age -= 4
            } else if (mc.level != null &&
                !mc.level!!.getBlockState(BlockPos(posX.toInt(), posY.toInt(), posZ.toInt())).isAir
            ) {
                age -= 8
            } else {
                age--
            }

            if (age < 0) {
                return true
            }

            trails.removeAll { trail -> trail.update() }

            prevPosX = posX
            prevPosY = posY
            prevPosZ = posZ

            posX += motionX
            posY += motionY
            posZ += motionZ

            trails.add(
                Trail(
                    Vec3(prevPosX.toDouble(), prevPosY.toDouble(), prevPosZ.toDouble()),
                    Vec3(posX.toDouble(), posY.toDouble(), posZ.toDouble()),
                    Particles.resolveColor(age * 10)
                )
            )

            motionX *= 0.99f
            motionY *= 0.99f
            motionZ *= 0.99f

            return false
        }

        override fun render(poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads) {
            if (trails.isEmpty()) return

            val tickDelta = Constants.client.deltaTracker.getGameTimeDeltaPartialTick(true)
            val particleSize = ffSize.toFloat()

            for (trail in trails) {
                val position = trail.interpolate(tickDelta)
                val alpha = (255.0f * (age.toFloat() / maxAge.toFloat()) * trail.animation(tickDelta)).toInt()
                drawBillboard(poseStack, renderer, position, particleSize, Particles.withAlpha(trail.color, alpha))
            }
        }

    }

    private open class ParticleBase(
        protected var posX: Float,
        protected var posY: Float,
        protected var posZ: Float,
        protected var motionX: Float,
        protected var motionY: Float,
        protected var motionZ: Float,
    ) {

        protected var prevPosX: Float = posX
        protected var prevPosY: Float = posY
        protected var prevPosZ: Float = posZ
        protected var age: Int = MathUtils.getRandom(100.0f, 300.0f).toInt()
        protected var maxAge: Int = age

        open fun tick(): Boolean {
            val mc = Constants.client
            if (Constants.client.player!!.distanceToSqr(posX.toDouble(), posY.toDouble(), posZ.toDouble()) > 4096.0) {
                age -= 8
            } else {
                age--
            }

            if (age < 0) {
                return true
            }

            prevPosX = posX
            prevPosY = posY
            prevPosZ = posZ

            posX += motionX
            posY += motionY
            posZ += motionZ

            motionX *= 0.9f
            if (Particles.physics.`is`(Physics.Fly)) {
                motionY *= 0.9f
            }
            motionZ *= 0.9f
            motionY -= 0.001f

            return false
        }

        open fun render(poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads) {
            val particleColor = Particles.withAlpha(
                Particles.resolveColor(age * 2),
                (255.0f * (age.toFloat() / maxAge.toFloat())).toInt()
            )
            drawBillboard(poseStack, renderer, interpolatePos(), Particles.size.toFloat(), particleColor)
        }

        protected fun interpolatePos(): Vec3 {
            val tickDelta = Constants.client.deltaTracker.getGameTimeDeltaPartialTick(true)
            val cameraPos = Constants.client.gameRenderer.mainCamera().position()
            val x = Mth.lerp(tickDelta.toDouble(), prevPosX.toDouble(), posX.toDouble()) - cameraPos.x
            val y = Mth.lerp(tickDelta.toDouble(), prevPosY.toDouble(), posY.toDouble()) - cameraPos.y
            val z = Mth.lerp(tickDelta.toDouble(), prevPosZ.toDouble(), posZ.toDouble()) - cameraPos.z
            return Vec3(x, y, z)
        }

        protected fun drawBillboard(
            poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads,
            position: Vec3, particleSize: Float, particleColor: Color,
        ) {
            val camera: Camera = Constants.client.gameRenderer.mainCamera()

            poseStack.pushPose()
            poseStack.translate(position.x, position.y, position.z)
            poseStack.rotateDegrees(Axis.YP, -camera.yRot())
            poseStack.rotateDegrees(Axis.XP, camera.xRot())

            val matrix: Matrix4f = poseStack.last().pose()
            val argb = particleColor.rgb

            renderer.vertex(matrix, 0.0f, -particleSize, 0.0f, 0.0f, 1.0f, argb)
            renderer.vertex(matrix, -particleSize, -particleSize, 0.0f, 1.0f, 1.0f, argb)
            renderer.vertex(matrix, -particleSize, 0.0f, 0.0f, 1.0f, 0.0f, argb)
            renderer.vertex(matrix, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, argb)

            poseStack.popPose()
        }

    }

    private class Trail(private val from: Vec3, private val to: Vec3, val color: Color) {

        private var ticks = 10
        private var prevTicks = 10

        fun interpolate(tickDelta: Float): Vec3 {
            val camera: Camera = Constants.client.gameRenderer.mainCamera()
            val x = Mth.lerp(tickDelta.toDouble(), from.x, to.x) - camera.position().x
            val y = Mth.lerp(tickDelta.toDouble(), from.y, to.y) - camera.position().y
            val z = Mth.lerp(tickDelta.toDouble(), from.z, to.z) - camera.position().z
            return Vec3(x, y, z)
        }

        fun animation(tickDelta: Float): Double =
            (prevTicks + (ticks - prevTicks) * tickDelta) / 10.0

        fun update(): Boolean {
            prevTicks = ticks
            return ticks-- <= 0
        }

    }

}
