package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.interfaces.WalkAnimationStateAccessor
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.render.WireframeEntityRenderer
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.authlib.GameProfile
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.awt.Color
import java.util.ArrayList

object PopChams : Module("Pop Chams", Category.RENDER) {

    private val ignoreSelf by boolSetting("Ignore Self", true)
    private val renderTime by doubleSetting("Render Time", 1.0, 0.1, 6.0, 0.1)
    private val yModifier by doubleSetting("Y Modifier", 0.75, -4.0, 4.0, 0.05)
    private val yEasing: EnumSetting<Easing> = enumSetting("Y Easing", Easing.EASE_IN_OUT_EXPO)
    private val scaleModifier by doubleSetting("Scale Modifier", -0.25, -4.0, 4.0, 0.05)
    private val fadeOut by boolSetting("Fade Out", true)
    private val sideColor by colorSetting("Side Color", Color(255, 255, 255, 25))
    private val lineColor by colorSetting("Line Color", Color(255, 255, 255, 127))

    private val ghosts: MutableList<GhostPlayer> = ArrayList()
    private var nextEntityId = Int.MIN_VALUE

    init {
        nonNullHandler<PacketEvent.Receive> { event ->
            val packet = event.packet
            if (packet !is ClientboundEntityEventPacket || packet.eventId != EntityEvent.PROTECTED_FROM_DEATH) {
                return@nonNullHandler
            }

            val entity = packet.getEntity(level) ?: return@nonNullHandler
            if (entity !is Player) return@nonNullHandler
            if (ignoreSelf && entity === player) return@nonNullHandler

            synchronized(ghosts) {
                ghosts.add(GhostPlayer(level, entity, mc))
            }
        }

        handler<Render3DEvent> { event ->
            synchronized(ghosts) {
                if (ghosts.isEmpty()) {
                    return@synchronized
                }

                WireframeEntityRenderer.beginBatch(event.poseStack)
                ghosts.removeAll { ghostPlayer -> ghostPlayer.render(event) }
                WireframeEntityRenderer.endBatch()
            }
        }
    }

    override fun onDisable() {
        synchronized(ghosts) {
            ghosts.clear()
        }
    }

    private class GhostPlayer(
        private val level: ClientLevel,
        player: Player,
        private val minecraft: Minecraft,
    ) : RemotePlayer(level, GameProfile(player.gameProfile.id(), player.gameProfile.name())) {

        private var timer = 0.0
        private val walkPosition: Float
        private val walkSpeed: Float
        private val attackAnimation: Float
        private val swing: LivingEntity.SwingDescription?
        private val startY: Double

        init {
            setId(PopChams.nextEntityId++)
            val tickDelta =
                if (level.tickRateManager().isFrozen()) 1.0f
                else minecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
            walkPosition = player.walkAnimation.position(tickDelta)
            walkSpeed = player.walkAnimation.speed(tickDelta)
            attackAnimation = player.getSwingAnimation(tickDelta)
            startY = player.y

            copyPosition(player)
            setOldPosAndRot()
            yHeadRot = player.yHeadRot
            yHeadRotO = yHeadRot
            yBodyRot = player.yBodyRot
            yBodyRotO = yBodyRot
            attributes.assignAllValues(player.attributes)
            setPose(player.pose)
            swing = player.currentSwing
        }

        fun render(event: Render3DEvent): Boolean {
            val frameTime = minecraft.deltaTracker.gameTimeDeltaTicks / 20.0f
            timer += frameTime
            if (timer > PopChams.renderTime) return true

            val tickDelta =
                if (level.tickRateManager().isFrozen()) 1.0f
                else minecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
            tickCount = (timer * 20.0).toInt()

            val progress = Mth.clamp((timer / PopChams.renderTime).toFloat(), 0.0f, 1.0f)
            setPos(x, startY + PopChams.yModifier * PopChams.renderTime *
                    PopChams.yEasing.value.function.apply(progress), z)
            setOldPosAndRot()
            yHeadRotO = yHeadRot
            yBodyRotO = yBodyRot

            val scale = 1.0 + PopChams.scaleModifier * timer
            if (scale <= 0.0) return true

            val alphaSide = PopChams.sideColor.alpha
            val alphaLine = PopChams.lineColor.alpha
            val fadeFactor =
                if (PopChams.fadeOut) maxOf(0.0, 1.0 - timer / PopChams.renderTime).toFloat() else 1.0f

            (walkAnimation as WalkAnimationStateAccessor).`epsilon$freeze`(walkPosition, walkSpeed, tickDelta)

            val side = withAlpha(PopChams.sideColor, (alphaSide * fadeFactor).roundToInt())
            val line = withAlpha(PopChams.lineColor, (alphaLine * fadeFactor).roundToInt())

            WireframeEntityRenderer.render(event.poseStack, this, scale, side, line, 2.0f)
            return false
        }

        override fun getSwingAnimation(partialTicks: Float): Float = attackAnimation

        override fun getCurrentSwing(): LivingEntity.SwingDescription? = swing

        private fun withAlpha(color: Color, alpha: Int): Color =
            Color(color.red, color.green, color.blue, Mth.clamp(alpha, 0, 255))

        override fun shouldShowName(): Boolean = false

        override fun belowNameDisplay(): Component? = null

    }

}
