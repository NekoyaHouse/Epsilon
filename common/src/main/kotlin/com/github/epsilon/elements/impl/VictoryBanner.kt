package com.github.epsilon.elements.impl

import com.github.epsilon.Constants
import com.github.epsilon.elements.HudModule
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.QQAvatarManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.settings.impl.StringSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.render.animation.Easing
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.player.Player
import java.awt.Color
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Supplier

private const val IDEAL_WIDTH = 500.0f
private const val BASE_HEIGHT = 66.0f

object VictoryBanner : HudModule("Victory Banner", 0.0f, 72.0f, IDEAL_WIDTH, BASE_HEIGHT) {

    private enum class TriggerMode {
        Off,
        Title,
        Kill,
        Chat,
    }

    private val recentTargets: MutableMap<Int, TrackedTarget> = HashMap()

    private val generalGroup: SettingGroup = settingGroup("General")
    private val triggerGroup: SettingGroup = settingGroup("Trigger")
    private val appearanceGroup: SettingGroup = settingGroup("Appearance")
    private val triggerMode by enumSetting(
        "Trigger Mode", TriggerMode.Kill,
        { _: TriggerMode -> recentTargets.clear() }
    ).group(triggerGroup)
    private val titleTriggers by stringListSetting(
        "Title Triggers", listOf("胜利", "挑战成功", "Victory"),
        dependency = { triggerMode == TriggerMode.Title }
    )
    private val chatTriggers by stringListSetting(
        "Chat Triggers", listOf("获胜", "胜利", "挑战成功", "Victory"),
        dependency = { triggerMode == TriggerMode.Chat }
    )
    private val title by stringSetting("Title", "TARGET ELIMINATED").group(generalGroup)
    private val subtitle by stringSetting("Subtitle", "ELIMINATION CONFIRMED").group(generalGroup)
    private val holdTime by doubleSetting("Hold Time", 1.9, 0.2, 10.0, 0.1).group(generalGroup)
    private val scale by doubleSetting("Scale", 0.75, 0.5, 2.0, 0.05).group(appearanceGroup)
    private val titleScale by doubleSetting("Title Scale", 1.5, 0.8, 1.8, 0.05).group(appearanceGroup)
    private val subtitleScale by doubleSetting("Subtitle Scale", 0.7, 0.6, 1.2, 0.05).group(appearanceGroup)
    private val accentColor by colorSetting("Accent Color", Color(244, 244, 20, 255)).group(appearanceGroup)
    private val subtitleColor by colorSetting("Subtitle Color", Color(141, 145, 148, 255)).group(appearanceGroup)
    private val backgroundColor by colorSetting("Background Color", Color(14, 15, 17, 158)).group(appearanceGroup)
    private val backgroundBlur by boolSetting("Background Blur", true).group(appearanceGroup)
    private val blurStrength by doubleSetting(
        "Blur Strength", 8.0, 1.0, 20.0, 1.0, dependency = { backgroundBlur }
    ).group(appearanceGroup)

    private val textRendererSupplier: Supplier<TextRenderer> = Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    @Volatile
    private var animationStartMs: Long = 0L

    @Volatile
    private var active: Boolean = false

    @Volatile
    private var currentTitle: String? = null

    init {
        setAnchorState(HudModule.HorizontalAnchor.Center, HudModule.VerticalAnchor.Top, 0.0f, 72.0f)
        handler<PacketEvent.Receive> { event -> onPacketReceive(event) }
        nonNullHandler<AttackEntityEvent> { event ->
            val target = event.entity
            if (triggerMode == TriggerMode.Kill && event.player === player && target is Player && target !== player && target.isAlive) {
                recentTargets[target.id] = TrackedTarget(target, System.currentTimeMillis())
            }
        }
    }

    fun isPlaying(): Boolean {
        return active
    }

    fun show(title: String?) {
        play(title)
    }

    override fun reset() {
        super.reset()
        setAnchorState(HudModule.HorizontalAnchor.Center, HudModule.VerticalAnchor.Top, 0.0f, 72.0f)
    }

    override fun onEnable() {
        clearRuntimeState()
        QQAvatarManager.requestLoad()
    }

    override fun onDisable() {
        clearRuntimeState()
        QQAvatarManager.release()
    }

    override fun render(deltaTracker: DeltaTracker) {
        val preview = mc.gui.screen() is HudEditorScreen
        if (preview) {
            renderBanner(true, 0L)
        } else {
            updateKillTrigger()
            if (!active) return

            val elapsed = Math.max(0L, System.currentTimeMillis() - animationStartMs)
            if (elapsed >= totalDuration()) {
                active = false
                currentTitle = null
                return
            }

            renderBanner(false, elapsed)
        }
    }

    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet: Packet<*> = event.packet
        when (triggerMode) {
            TriggerMode.Title -> {
                val text: Component? = when (packet) {
                    is ClientboundSetTitleTextPacket -> packet.text()
                    is ClientboundSetSubtitleTextPacket -> packet.text()
                    else -> null
                }
                if (text != null && matches(text.string, titleTriggers)) {
                    play(null)
                }
            }

            TriggerMode.Chat -> {
                val message: String? = when (packet) {
                    is ClientboundSystemChatPacket -> if (!packet.overlay()) packet.content().string else null
                    is ClientboundDisguisedChatPacket -> packet.message().string
                    is ClientboundPlayerChatPacket -> visiblePlayerChat(packet)
                    else -> null
                }
                if (matches(message, chatTriggers)) {
                    play(null)
                }
            }

            TriggerMode.Kill -> {
                if (packet is ClientboundEntityEventPacket && packet.eventId == EntityEvent.DEATH) {
                    mc.execute { confirmDeathEvent(packet) }
                }
            }

            else -> {}
        }
    }

    private fun updateKillTrigger() {
        val level = NonNullContext.current?.level
        if (triggerMode == TriggerMode.Kill && level != null) {
            val now = System.currentTimeMillis()
            val iterator = recentTargets.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val target = entry.value
                val targetPlayer = target.player
                if (!target.isRecent(now)) {
                    iterator.remove()
                    continue
                }

                if (targetPlayer.level() !== level) {
                    iterator.remove()
                    continue
                }

                val dead = targetPlayer.isDeadOrDying || targetPlayer.deathTime > 0 || targetPlayer.health <= 0.0f
                val removed = targetPlayer.isRemoved || level.getEntity(entry.key) !== targetPlayer
                if (dead || removed) {
                    iterator.remove()
                    play(null)
                }
            }
        } else {
            recentTargets.clear()
        }
    }

    private fun confirmTrackedDeath(entityId: Int) {
        if (!isEnabled || triggerMode != TriggerMode.Kill) return
        val level = NonNullContext.current?.level ?: return
        val target = recentTargets[entityId]
        if (target == null || target.player.level() !== level || !target.isRecent(System.currentTimeMillis())) {
            return
        }
        recentTargets.remove(entityId)
        play(null)
    }

    private fun confirmDeathEvent(packet: ClientboundEntityEventPacket) {
        if (!isEnabled || triggerMode != TriggerMode.Kill) return
        val level = NonNullContext.current?.level ?: return
        val entity = packet.getEntity(level) ?: return
        confirmTrackedDeath(entity.id)
    }

    private fun play(overrideTitle: String?) {
        if (!isEnabled || active) return
        val requested = overrideTitle ?: title
        currentTitle = if (requested == null || requested.isBlank()) "TARGET ELIMINATED" else requested
        animationStartMs = System.currentTimeMillis()
        active = true
    }

    private fun renderBanner(preview: Boolean, elapsed: Long) {
        QQAvatarManager.requestLoad()
        val textRenderer = textRendererSupplier.get()
        val uiScale = scale.toFloat()
        val bannerTitle = if (preview) displayTitle(title) else displayTitle(currentTitle)
        val bannerSubtitle = subtitle ?: ""
        val layout = createLayout(textRenderer, bannerTitle, bannerSubtitle, uiScale)
        setBounds(layout.width, layout.height)

        val frameProgress = if (preview) 1.0f else frameProgress(elapsed)
        val contentAlpha = if (preview) 1.0f else contentAlpha(elapsed)
        val visibleWidth = Math.max(1.0f, layout.width * frameProgress)
        val visibleX = x + (layout.width - visibleWidth) * 0.5f
        val frameAlpha = Math.min(1.0f, frameProgress * 1.8f)
        val offset = (1.0f - Easing.EASE_OUT_CUBIC.function.apply(contentAlpha)) * 5.0f * uiScale

        if (backgroundBlur) {
            BlurShader.render(visibleX, y, visibleWidth, layout.height, 0.0f, blurStrength.toFloat())
        }

        val scope = renderScope()
        scope.rect(visibleX, y, visibleWidth, layout.height, withAlpha(backgroundColor, frameAlpha))
        scope.rect(visibleX, y, visibleWidth, 2.0f * uiScale, withAlpha(accentColor, frameAlpha))

        if (contentAlpha <= 0.0f || frameProgress < 0.72f) return

        scope.scissor(UiRect(visibleX, y, visibleWidth, layout.height)) { clipped ->
            drawIdentityModule(clipped, layout, contentAlpha, offset, uiScale)
            drawMessage(clipped, textRenderer, layout, bannerTitle, bannerSubtitle, contentAlpha, offset, uiScale, preview)
            drawStatusModule(clipped, textRenderer, layout, contentAlpha, offset, uiScale, preview)
        }
    }

    private fun createLayout(renderer: TextRenderer, bannerTitle: String, bannerSubtitle: String, uiScale: Float): Layout {
        val requestedTitleScale = titleScale.toFloat() * uiScale
        val requestedSubtitleScale = subtitleScale.toFloat() * uiScale
        val titleWidth = renderer.getWidth(bannerTitle, requestedTitleScale)
        val subtitleWidth = renderer.getWidth(bannerSubtitle, requestedSubtitleScale)
        val desired = Math.max(IDEAL_WIDTH * uiScale, Math.max(titleWidth, subtitleWidth) + 230.0f * uiScale)
        val available = Math.max(1.0f, LuminRenderSystem.getScaledWidth() - 24.0f)
        val width = Math.min(available, Mth.clamp(desired, MIN_WIDTH * uiScale, MAX_WIDTH * uiScale))
        return Layout(width, BASE_HEIGHT * uiScale)
    }

    private fun drawIdentityModule(scope: UiTree.Scope, layout: Layout, alpha: Float, offset: Float, uiScale: Float) {
        val dividerX = x + 70.0f * uiScale
        scope.rect(dividerX, y + 10.0f * uiScale, uiScale, layout.height - 20.0f * uiScale, withAlpha(HAIRLINE, alpha))
        val avatarX = x + 18.0f * uiScale
        val avatarY = y + 13.0f * uiScale + offset
        val avatarSize = 40.0f * uiScale
        val avatarRadius = 2.0f * uiScale
        scope.texture(
            QQAvatarManager.texture(), avatarX, avatarY, avatarSize, avatarSize,
            0.0f, 0.0f, 1.0f, 1.0f, withAlpha(Color.WHITE, alpha), QQAvatarManager.linearFilter()
        )
        scope.outline(avatarX, avatarY, avatarSize, avatarSize, avatarRadius, uiScale, withAlpha(HAIRLINE, alpha))
    }

    private fun drawMessage(
        scope: UiTree.Scope,
        renderer: TextRenderer,
        layout: Layout,
        bannerTitle: String,
        bannerSubtitle: String,
        alpha: Float,
        offset: Float,
        uiScale: Float,
        preview: Boolean,
    ) {
        val left = x + 84.0f * uiScale
        val showStatus = layout.width >= 360.0f * uiScale
        val right = x + layout.width - (if (showStatus) 88.0f else 18.0f) * uiScale
        val available = Math.max(80.0f * uiScale, right - left)

        val eyebrow = if (preview) "SYSTEM // VISUAL PREVIEW" else "COMBAT // ELIMINATION CONFIRMED"
        val eyebrowScale = 0.55f * uiScale
        val fittedTitleScale = fitScale(renderer, bannerTitle, titleScale.toFloat() * uiScale, available, null)
        val hasSubtitle = bannerSubtitle.isNotBlank()
        val fittedSubtitleScale = fitScale(
            renderer, bannerSubtitle.uppercase(Locale.ROOT), subtitleScale.toFloat() * uiScale, available, null
        )

        val eyebrowHeight = renderer.getHeight(eyebrowScale)
        val titleHeight = renderer.getHeight(fittedTitleScale)
        val subtitleHeight = if (hasSubtitle) renderer.getHeight(fittedSubtitleScale) else 0.0f
        val blockHeight = eyebrowHeight + 3.0f * uiScale + titleHeight + (if (hasSubtitle) 2.0f * uiScale + subtitleHeight else 0.0f)
        var lineY = y + (layout.height - blockHeight) * 0.5f + offset

        scope.text(eyebrow, left, lineY, eyebrowScale, withAlpha(MUTED, alpha))
        lineY += eyebrowHeight + 2.0f * uiScale
        scope.text(bannerTitle, left, lineY, fittedTitleScale, withAlpha(TEXT, alpha))

        if (hasSubtitle) {
            lineY += titleHeight + 1.0f * uiScale
            scope.text(bannerSubtitle.uppercase(Locale.ROOT), left, lineY, fittedSubtitleScale, withAlpha(subtitleColor, alpha))
        }
    }

    private fun drawStatusModule(
        scope: UiTree.Scope,
        renderer: TextRenderer,
        layout: Layout,
        alpha: Float,
        offset: Float,
        uiScale: Float,
        preview: Boolean,
    ) {
        if (layout.width < 360.0f * uiScale) return

        val moduleX = x + layout.width - 76.0f * uiScale
        scope.rect(moduleX, y + 10.0f * uiScale, uiScale, layout.height - 20.0f * uiScale, withAlpha(HAIRLINE, alpha))

        val numberScale = 1.0f * uiScale
        val labelScale = 0.8f * uiScale
        val statusScale = 0.6f * uiScale
        val numberHeight = renderer.getHeight(numberScale)
        val labelHeight = renderer.getHeight(labelScale)
        val statusHeight = renderer.getHeight(statusScale)
        val primaryHeight = Math.max(numberHeight, labelHeight)
        val groupHeight = primaryHeight + 3.0f * uiScale + statusHeight
        val groupY = y + (layout.height - groupHeight) * 0.5f + offset

        scope.text("01", moduleX + 9.0f * uiScale, groupY, numberScale, withAlpha(TEXT, alpha))
        scope.text(
            if (preview) "TEST" else "KILL", moduleX + 30.0f * uiScale,
            groupY + (primaryHeight - labelHeight) * 0.5f, labelScale, withAlpha(accentColor, alpha)
        )
        scope.text("STATUS / OK", moduleX + 9.0f * uiScale, groupY + primaryHeight + 3.0f * uiScale, statusScale, withAlpha(MUTED, alpha))
    }

    private fun frameProgress(elapsed: Long): Float {
        if (elapsed < FRAME_IN_DURATION) {
            return Easing.EASE_OUT_QUINT.function.apply(elapsed / FRAME_IN_DURATION.toFloat())
        } else {
            val frameOutStart = FRAME_IN_DURATION + holdDurationMs() + CONTENT_OUT_DURATION
            if (elapsed < frameOutStart) return 1.0f
            return 1.0f - Easing.EASE_IN_CUBIC.function.apply((elapsed - frameOutStart) / FRAME_OUT_DURATION.toFloat())
        }
    }

    private fun contentAlpha(elapsed: Long): Float {
        if (elapsed < CONTENT_DELAY) return 0.0f
        if (elapsed < CONTENT_DELAY + CONTENT_IN_DURATION) {
            return Easing.EASE_OUT_CUBIC.function.apply((elapsed - CONTENT_DELAY) / CONTENT_IN_DURATION.toFloat())
        } else {
            val contentOutStart = FRAME_IN_DURATION + holdDurationMs()
            if (elapsed < contentOutStart) return 1.0f
            return 1.0f - Easing.EASE_IN_CUBIC.function.apply((elapsed - contentOutStart) / CONTENT_OUT_DURATION.toFloat())
        }
    }

    private fun totalDuration(): Long {
        return FRAME_IN_DURATION + holdDurationMs() + CONTENT_OUT_DURATION + FRAME_OUT_DURATION
    }

    private fun holdDurationMs(): Long {
        return Math.round(holdTime * 1_000.0)
    }

    private fun clearRuntimeState() {
        active = false
        animationStartMs = 0L
        currentTitle = null
        recentTargets.clear()
    }

    private data class Layout(val width: Float, val height: Float)

    private data class TrackedTarget(val player: Player, val attackedAtMs: Long) {

        fun isRecent(now: Long): Boolean {
            return now - attackedAtMs <= KILL_CONFIRMATION_WINDOW_MS
        }

    }


    private const val FRAME_IN_DURATION = 240L
    private const val CONTENT_DELAY = 110L
    private const val CONTENT_IN_DURATION = 180L
    private const val CONTENT_OUT_DURATION = 110L
    private const val FRAME_OUT_DURATION = 180L
    private const val KILL_CONFIRMATION_WINDOW_MS = 4_000L

    private const val MIN_WIDTH = 340.0f
    private const val MAX_WIDTH = 620.0f

    private val TEXT: Color = Color(244, 245, 240, 255)
    private val MUTED: Color = Color(128, 132, 135, 255)
    private val HAIRLINE: Color = Color(224, 226, 220, 45)

    private fun matches(text: String?, triggers: List<String>?): Boolean {
        if (text == null || text.isEmpty() || triggers == null) return false
        for (candidate in triggers) {
            if (!candidate.isEmpty() && text.contains(candidate)) return true
        }
        return false
    }

    private fun visiblePlayerChat(packet: ClientboundPlayerChatPacket): String {
        return packet.unsignedContent()
            .map { content -> content.string }
            .orElseGet { packet.filterMask().apply(packet.body().content()) }
    }

    private fun displayTitle(value: String?): String {
        return if (value == null || value.isBlank()) "TARGET ELIMINATED" else value
    }

    private fun fitScale(renderer: TextRenderer, text: String?, requested: Float, maxWidth: Float, font: TtfFontLoader?): Float {
        if (text == null || text.isEmpty()) return requested
        val natural = if (font == null) renderer.getWidth(text, 1.0f) else renderer.getWidth(text, 1.0f, font)
        if (natural <= 0.0f) return requested
        return Mth.clamp(maxWidth / natural, 0.1f, requested)
    }

    private fun withAlpha(color: Color?, alphaMultiplier: Float): Color {
        val source = color ?: Color.WHITE
        val alpha = Mth.clamp(Math.round(source.alpha * Mth.clamp(alphaMultiplier, 0.0f, 1.0f)), 0, 255)
        return Color(source.red, source.green, source.blue, alpha)
    }

}
