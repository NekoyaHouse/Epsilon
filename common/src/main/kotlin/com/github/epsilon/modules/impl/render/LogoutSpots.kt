package com.github.epsilon.modules.impl.render

import com.github.epsilon.utils.*
import com.github.epsilon.Constants
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.RectRenderer
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.interfaces.WalkAnimationStateAccessor
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.ChatUtils
import com.github.epsilon.utils.render.SkeletonRenderer
import com.github.epsilon.utils.render.WireframeEntityRenderer
import com.github.epsilon.utils.render.WorldToScreen
import com.google.common.base.Suppliers
import com.google.common.collect.Maps
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Locale
import java.util.UUID
import java.util.function.Supplier

object LogoutSpots : Module("Logout Spots", Category.RENDER) {

    private enum class RenderMode {
        Model,
        Skeleton,
        Box
    }

    private val notifications by boolSetting("Notifications", true)
    private val ignoreBots by boolSetting("Ignore Bots", true)
    private val renderMode: EnumSetting<RenderMode> = enumSetting("Render Mode", RenderMode.Model)
    private val sideColor by colorSetting("Side Color", Color(255, 255, 255, 25))
    private val lineColor by colorSetting("Line Color", Color(255, 255, 255, 127))

    private val playerCache: MutableMap<UUID, Player> = Maps.newConcurrentMap()
    private val logoutCache: MutableMap<UUID, LogoutPlayer> = Maps.newConcurrentMap()

    private val rectRendererSupplier: Supplier<RectRenderer> = Suppliers.memoize { RectRenderer.create() }
    private val textRendererSupplier: Supplier<TextRenderer> = Suppliers.memoize { TextRenderer.create() }

    init {
        nonNullListener<PacketEvent.Receive> { event ->
            val packet = event.packet
            if (packet is ClientboundPlayerInfoUpdatePacket) {
                if (packet.actions().contains(Action.ADD_PLAYER)) {
                    for (entry in packet.entries()) {
                        val player = logoutCache[entry.profileId()]
                        if (player == null) continue
                        if (ignoreBots && !mc.getConnection()!!.onlinePlayerIds.contains(player.uuid))
                            continue
                        if (notifications) {
                            ChatUtils.addChatMessage(
                                player.name.string + " logged back at  X: " + player.x.toInt() +
                                        " Y: " + player.y.toInt() + " Z: " + player.z.toInt()
                            )
                        }
                        logoutCache.remove(entry.profileId())
                    }
                }
                playerCache.clear()
            }

            if (packet is ClientboundPlayerInfoRemovePacket) {
                for (uuid in packet.profileIds()) {
                    val player = playerCache[uuid]
                    if (player == null) continue
                    if (ignoreBots && !mc.getConnection()!!.onlinePlayerIds.contains(player.uuid))
                        continue
                    if (notifications) {
                        ChatUtils.addChatMessage(
                            player.name.string + " logged out at  X: " + player.x.toInt() +
                                    " Y: " + player.y.toInt() + " Z: " + player.z.toInt()
                        )
                    }
                    logoutCache.computeIfAbsent(uuid) { LogoutPlayer(level, player) }
                }
                playerCache.clear()
            }
        }

        nonNullListener<PlayerTickEvent.Pre> {
            for (target in level.players()) {
                if (target === player) continue
                playerCache[target.gameProfile.id()] = target
            }
        }

        handler<Render3DEvent> { event ->
            when (renderMode.value) {
                RenderMode.Model -> {
                    var batching = false
                    for (logoutPlayer in logoutCache.values) {
                        if (!batching) {
                            WireframeEntityRenderer.beginBatch(event.poseStack)
                            batching = true
                        }
                        logoutPlayer.render(event.poseStack)
                    }

                    if (batching) {
                        WireframeEntityRenderer.endBatch()
                    }
                }

                RenderMode.Skeleton -> {
                    for (logoutPlayer in logoutCache.values) {
                        SkeletonRenderer.render(logoutPlayer, lineColor, 2.0f)
                    }
                }

                RenderMode.Box -> {
                    for (logoutPlayer in logoutCache.values) {
                        Render3DScheduler.addFilledBox(logoutPlayer.boundingBox, sideColor)
                        Render3DScheduler.addOutlineBox(logoutPlayer.boundingBox, lineColor)
                    }
                }
            }
        }

        handler<Render2DEvent.Level> {
            val rectRenderer = rectRendererSupplier.get()
            val textRenderer = textRendererSupplier.get()

            val partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val screenWidth = LuminRenderSystem.getScaledWidth()
            val screenHeight = LuminRenderSystem.getScaledHeight()

            for (logoutPlayer in logoutCache.values) {
                val anchor = logoutPlayer.getPosition(partialTick).add(
                    0.0,
                    logoutPlayer.bbHeight + (if (logoutPlayer.isCrouching) 0.1 else 0.2),
                    0.0
                )
                val projectedAnchor = WorldToScreen.calcWorld2Screen(anchor) ?: continue

                val textScale = WorldToScreen.calcScale(anchor) * 0.4f
                val padding = 2.0f * textScale
                val text = logoutPlayer.name.string + " " +
                        String.format(
                            Locale.ROOT, "%.1f",
                            logoutPlayer.health + logoutPlayer.absorptionAmount
                        ) + " X: " + logoutPlayer.x.toInt() + " Z: " + logoutPlayer.z.toInt()

                val textWidth = textRenderer.getWidth(text, textScale)
                val textHeight = textRenderer.getHeight(textScale)
                val boxWidth = textWidth + padding * 2.0f
                val boxHeight = textHeight + padding * 2.0f
                if (!projectedAnchor.x.isFinite() || !projectedAnchor.y.isFinite()) {
                    continue
                }

                val centerX = projectedAnchor.x
                val boxX = centerX - boxWidth * 0.5f
                val boxY = projectedAnchor.y - boxHeight - 2.0f * textScale

                if (boxX + boxWidth < 0.0f || boxY + boxHeight < 0.0f || boxX > screenWidth || boxY > screenHeight) {
                    continue
                }

                rectRenderer.addRect(boxX, boxY, boxWidth, boxHeight, Color(0, 0, 0, 153))
                textRenderer.addText(text, boxX + padding, boxY + padding, textScale, Color.WHITE)
            }

            rectRenderer.drawAndClear()
            textRenderer.drawAndClear()
        }
    }

    override fun onEnable() {
        playerCache.clear()
        logoutCache.clear()
    }

    private class LogoutPlayer(
        private val level: ClientLevel,
        player: Player,
    ) : RemotePlayer(level, GameProfile(player.gameProfile.id(), player.gameProfile.name())) {

        private val walkPosition: Float
        private val walkSpeed: Float
        private val attackAnimation: Float
        private val swing: LivingEntity.SwingDescription?

        init {
            setId(player.id)

            val tickDelta =
                if (level.tickRateManager().isFrozen()) 1.0f
                else Constants.client.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            walkPosition = player.walkAnimation.position(tickDelta)
            walkSpeed = player.walkAnimation.speed(tickDelta)
            attackAnimation = player.getSwingAnimation(tickDelta)

            copyPosition(player)
            setOldPosAndRot()
            yHeadRot = player.yHeadRot
            yHeadRotO = yHeadRot
            yBodyRot = player.yBodyRot
            yBodyRotO = yBodyRot
            attributes.assignAllValues(player.attributes)
            setPose(player.pose)
            health = player.health
            absorptionAmount = player.absorptionAmount
            swing = player.currentSwing
        }

        fun render(poseStack: PoseStack) {
            val tickDelta =
                if (level.tickRateManager().isFrozen()) 1.0f
                else Constants.client.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            (walkAnimation as WalkAnimationStateAccessor).`epsilon$freeze`(walkPosition, walkSpeed, tickDelta)

            WireframeEntityRenderer.render(
                poseStack, this, 1.0,
                LogoutSpots.sideColor, LogoutSpots.lineColor, 2.0f
            )
        }

        override fun shouldShowName(): Boolean = false

        override fun getSwingAnimation(partialTicks: Float): Float = attackAnimation

        override fun getCurrentSwing(): LivingEntity.SwingDescription? = swing

        override fun belowNameDisplay(): Component? = null

    }

}
