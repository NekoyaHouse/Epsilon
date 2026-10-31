package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object TotemAnimation : Module("Totem Animation", Category.RENDER) {

    init {
        nonNullHandler<PlayerTickEvent.Pre> { onPlayerTick(it) }
    }

    private enum class Mode {
        FadeOut,
        Size,
        Otkisuli,
        Insert,
        Fall,
        Rocket,
        Roll
    }

    private val mode by enumSetting("Mode", Mode.FadeOut)
    private val speed by intSetting("Speed", 40, 1..100, 1)

    private var floatingItem: ItemStack? = null
    private var floatingItemTimeLeft = 0

    override fun onDisable() {
        floatingItem = null
        floatingItemTimeLeft = 0
    }

    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        if (floatingItemTimeLeft > 0 && --floatingItemTimeLeft == 0) {
            floatingItem = null
        }
    }

    fun showFloatingItem(item: ItemStack) {
        this.floatingItem = item
        this.floatingItemTimeLeft = getTime()
    }

    fun renderFloatingItem(tickDelta: Float, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector) {
        val item = floatingItem ?: return
        if (floatingItemTimeLeft <= 0) return

        val elapsedTime = getTime() - floatingItemTimeLeft
        val animationProgress = (elapsedTime + tickDelta) / getTime().toFloat()
        val progressSquared = animationProgress * animationProgress
        val progressCubed = animationProgress * progressSquared
        val oscillationFactor = 10.25f * progressCubed * progressSquared -
                24.95f * progressSquared * progressSquared +
                25.5f * progressCubed -
                13.8f * progressSquared +
                4.0f * animationProgress
        val oscillationRadians = oscillationFactor * PI.toFloat()
        val adjustedProgress = elapsedTime + tickDelta
        val scale = 50.0f + 175.0f * Mth.sin(oscillationRadians.toDouble())

        poseStack.pushPose()
        when (mode) {
            Mode.FadeOut -> {
                val x = sin((adjustedProgress * 112.0f / 180.0f).toDouble()).toFloat() * 100.0f
                val y = cos((adjustedProgress * 112.0f / 180.0f).toDouble()).toFloat() * 50.0f
                poseStack.translate(x * 0.008f, y * 0.008f, -10.0f)
                poseStack.scale(scale * 0.016f, scale * 0.016f, scale * 0.016f)
            }

            Mode.Size -> {
                poseStack.translate(0.0f, 0.0f, -10.0f)
                poseStack.scale(scale * 0.016f, scale * 0.016f, scale * 0.016f)
            }

            Mode.Otkisuli -> {
                poseStack.translate(0.0f, 0.0f, -10.0f)
                poseStack.rotateDegrees(Axis.XP, adjustedProgress * 2.0f)
                poseStack.rotateDegrees(Axis.ZP, adjustedProgress * 2.0f)
                val size = 200.0f - adjustedProgress * 1.5f
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f)
            }

            Mode.Insert -> {
                poseStack.translate(0.0f, 0.0f, -10.0f)
                poseStack.rotateDegrees(Axis.XP, adjustedProgress * 3.0f)
                val size = 200.0f - adjustedProgress * 1.5f
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f)
            }

            Mode.Fall -> {
                val downFactor = (adjustedProgress.toDouble()).pow(3.0).toFloat() * 0.2f
                poseStack.translate(0.0f, downFactor * 0.008f, -10.0f)
                poseStack.rotateDegrees(Axis.ZP, adjustedProgress * 5.0f)
                val size = 200.0f - adjustedProgress * 1.5f
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f)
            }

            Mode.Rocket -> {
                val downFactor = (adjustedProgress.toDouble()).pow(3.0).toFloat() * 0.2f - 20.0f
                poseStack.translate(0.0f, -downFactor * 0.008f, -10.0f)
                poseStack.rotateDegrees(Axis.YP, adjustedProgress * floatingItemTimeLeft * 2.0f)
                val size = 200.0f - adjustedProgress * 1.5f
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f)
            }

            Mode.Roll -> {
                val rightFactor = (adjustedProgress.toDouble()).pow(2.0).toFloat() * 4.5f
                poseStack.translate(rightFactor * 0.008f, 0.0f, -10.0f)
                poseStack.rotateDegrees(Axis.ZP, adjustedProgress * 40.0f)
                val size = 200.0f - adjustedProgress * 1.5f
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f)
            }
        }

        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D)
        val itemState = ItemStackRenderState()
        mc.itemModelResolver.updateForTopItem(itemState, item, ItemDisplayContext.FIXED, mc.level, null, 0)
        itemState.submit(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()
    }

    private fun getTime(): Int {
        val invertedSpeed = 101 - speed
        return when (mode) {
            Mode.FadeOut -> maxOf(1, invertedSpeed / 4)
            Mode.Insert -> maxOf(1, invertedSpeed / 2)
            else -> maxOf(1, invertedSpeed)
        }
    }

}
