package com.github.epsilon.utils.render.esp

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.render.animation.Easing
import com.github.epsilon.utils.world
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.Camera
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.awt.Color

object DeobfESP {

    enum class TextureMode(val texture: Identifier) {
        Niuren(ResourceLocationUtils.getIdentifier("textures/esp/niuren.png")),
        Mengcha(ResourceLocationUtils.getIdentifier("textures/esp/mengcha.png")),
    }

    private const val INTRO_DURATION_MS = 1600L
    private const val KILL_DURATION_MS = 1800L
    private const val CONTINUOUS_ROTATION_SPEED = 180.0f

    private val PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/epsilon_deobf_esp")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .build()

    private val effects: MutableMap<Int, Effect> = LinkedHashMap()

    /**
     * 为目标记录一次命中特效。
     *
     * @param entity 目标实体
     */
    fun markHit(entity: Entity) {
        if (entity is LivingEntity && !entity.isDeadOrDying) {
            val now = System.currentTimeMillis()
            val effect = effects[entity.id]
            if (effect == null || effect.target !== entity || effect.killedAtMs >= 0L) {
                effects[entity.id] = Effect(entity, now)
            }
        }
    }

    /**
     * 清除全部活动命中特效。
     */
    fun clear() {
        effects.clear()
    }

    /**
     * 仅保留仍处于上升阶段的命中特效。
     */
    fun retainRisingEffects() {
        if (effects.isEmpty()) return
        val nn = NonNullContext.current
        if (nn == null) {
            clear()
            return
        }
        context(nn) {
            val now = System.currentTimeMillis()
            val partialTick = Constants.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val iterator = effects.values.iterator()

            while (iterator.hasNext()) {
                val effect = iterator.next()
                val target = effect.target

                if (target.level() !== world) {
                    iterator.remove()
                } else if (effect.killedAtMs < 0L) {
                    if (target.isDeadOrDying) {
                        effect.beginKill(now, partialTick)
                    } else {
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * 渲染全部活动命中特效。
     *
     * @param poseStack 渲染姿态栈
     * @param size      特效尺寸
     * @param spins     旋转圈数
     * @param wobble    摆动幅度
     * @param flyHeight 特效上升高度
     */
    fun render(poseStack: PoseStack, size: Float, spins: Float, wobble: Float, flyHeight: Float) {
        if (effects.isEmpty()) return
        val nn = NonNullContext.current
        if (nn == null) {
            clear()
            return
        }
        context(nn) {
            val now = System.currentTimeMillis()
            val partialTick = Constants.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val camera: Camera = Constants.mc.gameRenderer.mainCamera()
            val texture = KillAura.deobfMode.texture
            val renderer = LuminImmediateRenderer.beginPosTexColorQuads(PIPELINE, texture)
            val iterator = effects.values.iterator()

            while (iterator.hasNext()) {
                val effect = iterator.next()
                val target = effect.target

                if (target.level() !== world) {
                    iterator.remove()
                    continue
                }

                if (effect.killedAtMs < 0L && target.isDeadOrDying) {
                    effect.beginKill(now, partialTick)
                } else if (effect.killedAtMs < 0L && target.isRemoved) {
                    iterator.remove()
                    continue
                }

                if (effect.killedAtMs >= 0L && now - effect.killedAtMs >= KILL_DURATION_MS) {
                    iterator.remove()
                    continue
                }

                renderEffect(poseStack, renderer, camera, effect, now, partialTick, size, spins, wobble, flyHeight)
            }

            renderer.end()
        }
    }

    private fun renderEffect(
        poseStack: PoseStack,
        renderer: LuminImmediateRenderer.PosTexColorQuads,
        camera: Camera,
        effect: Effect,
        now: Long,
        partialTick: Float,
        size: Float,
        spins: Float,
        wobble: Float,
        flyHeight: Float,
    ) {
        val baseWidth = Math.max(0.01f, size)
        val baseHeight = baseWidth * (if (KillAura.deobfMode == TextureMode.Niuren) 751.0f / 376.0f else 1280.0f / 1073.0f)
        val drawWidth: Float
        val drawHeight: Float
        val rotation: Float
        val offsetX: Float
        val offsetY: Float
        val tint: Color
        val anchor: Vec3

        if (effect.killedAtMs < 0L) {
            val introProgress = progress(now - effect.createdAtMs, INTRO_DURATION_MS)
            val reactionProgress = progress(now - effect.lastHitAtMs, INTRO_DURATION_MS)
            val pop = Easing.EASE_OUT_ELASTIC.function.apply(Mth.clamp(introProgress / 0.28f, 0.0f, 1.0f))
            val damping = 1.0f - reactionProgress
            val squash = Mth.sin((reactionProgress * Mth.PI * 10.0f).toDouble()) * damping * 0.14f * wobble

            drawWidth = baseWidth * pop * (1.0f + squash)
            drawHeight = baseHeight * pop * (1.0f - squash * 0.55f)
            rotation = aliveRotation(effect, now, spins, wobble)
            offsetX = Mth.sin((reactionProgress * Mth.PI * 24.0f).toDouble()) * damping * baseWidth * 0.09f * wobble
            offsetY = -Math.abs(Mth.sin((reactionProgress * Mth.PI * 8.0f).toDouble())) * damping * baseWidth * 0.08f * wobble
            tint = hitTint(reactionProgress, 255)
            anchor = livingAnchor(effect.target, partialTick)
        } else {
            val killProgress = progress(now - effect.killedAtMs, KILL_DURATION_MS)
            val flyProgress = Easing.EASE_IN_CUBIC.function.apply(killProgress)
            val scale = 1.0f + Easing.EASE_OUT_CUBIC.function.apply(killProgress) * 0.45f
            val squash = Mth.sin((killProgress * Mth.PI * 10.0f).toDouble()) * (1.0f - killProgress) * 0.18f * wobble

            drawWidth = baseWidth * scale * (1.0f + squash)
            drawHeight = baseHeight * scale * (1.0f - squash * 0.55f)
            rotation = aliveRotation(effect, effect.killedAtMs, spins, wobble) + flyProgress * 1440.0f
            offsetX = Mth.sin((killProgress * Mth.PI * 6.0f).toDouble()) * killProgress * baseWidth * 0.35f * wobble
            offsetY = 0.0f

            val fadeProgress = Mth.clamp((killProgress - 0.58f) / 0.42f, 0.0f, 1.0f)
            val alpha = Mth.clamp(Math.round(255.0f * (1.0f - Easing.EASE_IN_CUBIC.function.apply(fadeProgress))), 0, 255)
            val redProgress = 1.0f - Easing.EASE_OUT_CUBIC.function.apply(killProgress)
            tint = redTint(redProgress, alpha)
            anchor = effect.deathAnchor!!.add(0.0, (drawHeight / 2.0f + flyProgress * flyHeight).toDouble(), 0.0)
        }

        val relative = anchor.subtract(camera.position())
        poseStack.pushPose()
        poseStack.translate(relative.x, relative.y, relative.z)
        poseStack.rotateDegrees(Axis.YP, -camera.yRot())
        poseStack.rotateDegrees(Axis.XP, camera.xRot())
        poseStack.translate(offsetX, offsetY, 0.0f)
        poseStack.rotateDegrees(Axis.ZP, rotation)

        val matrix: Matrix4f = poseStack.last().pose()
        val halfWidth = drawWidth / 2.0f
        val halfHeight = drawHeight / 2.0f
        val argb = tint.getRGB()

        renderer.vertex(matrix, -halfWidth, halfHeight, 0.0f, 0.0f, 1.0f, argb)
        renderer.vertex(matrix, halfWidth, halfHeight, 0.0f, 1.0f, 1.0f, argb)
        renderer.vertex(matrix, halfWidth, -halfHeight, 0.0f, 1.0f, 0.0f, argb)
        renderer.vertex(matrix, -halfWidth, -halfHeight, 0.0f, 0.0f, 0.0f, argb)

        poseStack.popPose()
    }

    private fun livingAnchor(target: LivingEntity, partialTick: Float): Vec3 {
        return target.getEyePosition(partialTick)
    }

    private fun aliveRotation(effect: Effect, now: Long, spins: Float, wobble: Float): Float {
        val aliveElapsed = Math.max(0L, now - effect.createdAtMs)
        val introProgress = progress(aliveElapsed, INTRO_DURATION_MS)
        val reactionProgress = progress(Math.max(0L, now - effect.lastHitAtMs), INTRO_DURATION_MS)
        val introRotation = spins * 360.0f * Easing.EASE_OUT_EXPO.function.apply(introProgress)
        val continuousSeconds = Math.max(0L, aliveElapsed - INTRO_DURATION_MS) / 1000.0f
        val reactionWobble = Mth.sin((reactionProgress * Mth.PI * 12.0f).toDouble()) *
                (1.0f - reactionProgress) * 28.0f * wobble
        return introRotation + continuousSeconds * CONTINUOUS_ROTATION_SPEED + reactionWobble
    }

    private fun hitTint(reactionProgress: Float, alpha: Int): Color {
        val redProgress = Mth.clamp(Mth.sin((Mth.clamp(reactionProgress / 0.72f, 0.0f, 1.0f) * Mth.PI).toDouble()), 0.0f, 1.0f)
        return redTint(redProgress, alpha)
    }

    private fun redTint(redProgress: Float, alpha: Int): Color {
        val greenBlue = Mth.clamp(Math.round(Mth.lerp(Mth.clamp(redProgress, 0.0f, 1.0f), 255.0f, 42.0f)), 0, 255)
        return Color(255, greenBlue, greenBlue, Mth.clamp(alpha, 0, 255))
    }

    private fun progress(elapsed: Long, duration: Long): Float {
        return Mth.clamp(elapsed / duration.toFloat(), 0.0f, 1.0f)
    }

    private class Effect(val target: LivingEntity, now: Long) {

        val createdAtMs: Long = now
        val lastHitAtMs: Long = now
        var killedAtMs: Long = -1L
        var deathAnchor: Vec3? = null

        fun beginKill(now: Long, partialTick: Float) {
            this.killedAtMs = now
            this.deathAnchor = target.getEyePosition(partialTick)
        }
    }

}
