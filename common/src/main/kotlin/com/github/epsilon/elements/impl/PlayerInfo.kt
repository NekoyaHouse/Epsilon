package com.github.epsilon.elements.impl

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.elements.HudModule
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.level
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.HealthManager
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import java.awt.Color
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.math.roundToInt

object PlayerInfo : HudModule("Player Info", 4.0f, 48.0f, 250.0f, 70.0f) {

    private val scale by doubleSetting("Scale", 1.0, 0.5..2.0, 0.05)
    private val panelWidthSetting by doubleSetting("Width", 250.0, 200.0..300.0, 1.0)
    private val textGlow by boolSetting("Text Glow", true)
    private val glowRadius by doubleSetting("Glow Radius", 3.0, 0.1..6.0, 0.1, { textGlow })
    private val glowIntensity by intSetting("Glow Intensity", 2, 1..5, 1, { textGlow })
    private val playerRange by doubleSetting("Range", 128.0, 8.0..256.0, 1.0)
    private val maxPlayers by intSetting("Max Players", 8, 1..20, 1)
    private val showSelf by boolSetting("Show Self", false)
    private val showHeads by boolSetting("Show Heads", true)
    private val cornerRadius by doubleSetting("Corner Radius", 6.0, 0.0..20.0, 0.5)
    private val backgroundColor by colorSetting("Background Color", Color(18, 24, 21, 150))
    private val headerColor by colorSetting("Header Color", Color(255, 255, 255, 240))
    private val textColor by colorSetting("Text Color", Color(240, 243, 241, 230))
    private val drawShadow by boolSetting("Drop Shadow", true)
    private val shadowBlur by doubleSetting("Shadow Blur", 9.0, 2.0..32.0, 1.0, { drawShadow })
    private val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 90), true, { drawShadow })
    private val backgroundBlur by boolSetting("Background Blur", true)
    private val blurStrength by intSetting("Blur Strength", 5, 1..16, 1, { backgroundBlur })

    private val popCounts: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()
    private val nameScrollStartTimes: MutableMap<UUID, Long> = HashMap()
    private val textRendererSupplier: Supplier<TextRenderer> =
        Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })
    private var cachedRows: List<PlayerRow> = emptyList()
    private var rowsInitialized: Boolean = false

    init {
        safeEventHandler<PacketEvent.Receive> { event -> onPacketReceive(event, level) }
        safeEventHandler<PlayerTickEvent.Post> { refreshRows() }
        handler<GameLeftEvent> {
            popCounts.clear()
            nameScrollStartTimes.clear()
            cachedRows = emptyList()
            rowsInitialized = false
        }
    }

    override fun onEnable() {
        popCounts.clear()
        nameScrollStartTimes.clear()
        cachedRows = emptyList()
        rowsInitialized = false
    }

    override fun onDisable() {
        popCounts.clear()
        nameScrollStartTimes.clear()
        cachedRows = emptyList()
        rowsInitialized = false
    }

    override fun render(deltaTracker: DeltaTracker) {
        if (NonNullContext.instance == null) return

        val textRenderer = textRendererSupplier.get()
        if (!rowsInitialized) {
            refreshRows()
        }
        val rows = cachedRows
        val layout = createLayout(textRenderer, rows)
        setBounds(layout.panelWidth, layout.panelHeight)

        val panelX = x
        val panelY = y
        val panelRadius = cornerRadius.toFloat() * layout.scale
        val scope = renderScope()

        if (backgroundBlur) {
            BlurShader.render(
                panelX,
                panelY,
                layout.panelWidth,
                layout.panelHeight,
                panelRadius,
                blurStrength.toFloat()
            )
        }
        if (drawShadow) {
            scope.shadow(
                panelX, panelY, layout.panelWidth, layout.panelHeight, panelRadius,
                shadowBlur.toFloat() * layout.scale, shadowColor
            )
        }
        scope.roundRect(panelX, panelY, layout.panelWidth, layout.panelHeight, panelRadius, backgroundColor)

        drawHeaders(scope, textRenderer, layout, panelX, panelY)

        val dividerY = panelY + layout.paddingY + layout.headerHeight + layout.headerBodyGap * 0.5f
        scope.rect(
            panelX + layout.paddingX, dividerY, layout.tableWidth, maxOf(0.5f, layout.scale * 0.75f),
            withAlpha(headerColor, 0.14f)
        )

        var rowY = panelY + layout.paddingY + layout.headerHeight + layout.headerBodyGap
        if (rows.isEmpty()) {
            val emptyText = EpsilonTranslations.PlayerInfo.EMPTY.getTranslatedName()
            val emptyX = panelX + (layout.panelWidth - textRenderer.getWidth(emptyText, layout.textScale)) * 0.5f
            val emptyY = rowY + (layout.rowHeight - textRenderer.getHeight(layout.textScale)) * 0.5f
            text(scope, emptyText, emptyX, emptyY, layout.textScale, withAlpha(textColor, 0.72f))
            return
        }

        for (row in rows) {
            drawRow(scope, textRenderer, layout, row, panelX, rowY)
            rowY += layout.rowHeight
        }
    }

    private fun onPacketReceive(event: PacketEvent.Receive, level: ClientLevel) {
        val packet = event.packet
        if (packet !is ClientboundEntityEventPacket || packet.eventId != EntityEvent.PROTECTED_FROM_DEATH) {
            return
        }

        val entity: Entity? = packet.getEntity(level)
        if (entity is AbstractClientPlayer) {
            popCounts.merge(entity.uuid, 1) { oldValue, _ -> oldValue + 1 }
        }
    }

    private fun refreshRows() {
        val nn = NonNullContext.instance ?: return
        cachedRows = context(nn) { collectRows() }
        rowsInitialized = true
        pruneNameScrollState(cachedRows)
    }

    context(nn: NonNullContext)
    private fun collectRows(): List<PlayerRow> {
        val maxDistanceSquared = Mth.square(playerRange)
        val players: MutableList<AbstractClientPlayer> = ArrayList()

        val self = player
        for (player in level.players()) {
            if ((!showSelf && player === self) || player.isRemoved) continue
            if (self.distanceToSqr(player) > maxDistanceSquared) continue
            players.add(player)
        }

        players.sortWith(compareBy<AbstractClientPlayer> { self.distanceToSqr(it) })

        val count = minOf(maxPlayers, players.size)
        val rows: MutableList<PlayerRow> = ArrayList(count)
        for (i in 0 until count) {
            val player = players[i]
            val health = if (HealthManager != null) {
                HealthManager.getHealth(player)
            } else {
                player.health + player.absorptionAmount
            }
            val maxHealth = maxOf(1.0f, player.maxHealth + maxOf(0.0f, player.absorptionAmount))
            val distance = (self.distanceTo(player)).roundToInt()
            val direction = (Mth.wrapDegrees(player.yRot)).roundToInt()

            rows.add(
                PlayerRow(
                    player.uuid,
                    player.skin.body().texturePath(),
                    player.gameProfile.name,
                    distance.toString() + "m",
                    String.format(Locale.ROOT, "%.1f", health),
                    (popCounts[player.uuid] ?: 0).toString(),
                    direction.toString(),
                    resolveNameColor(player),
                    resolveHealthColor(health / maxHealth)
                )
            )
        }
        return rows
    }

    private fun pruneNameScrollState(rows: List<PlayerRow>) {
        if (nameScrollStartTimes.isEmpty()) return

        val visiblePlayers: MutableSet<UUID> = HashSet(rows.size)
        for (row in rows) {
            visiblePlayers.add(row.playerId)
        }
        nameScrollStartTimes.keys.retainAll(visiblePlayers)
    }

    private fun createLayout(textRenderer: TextRenderer, rows: List<PlayerRow>): Layout {
        val scale = this.scale.toFloat()
        val headerScale = HEADER_SCALE * scale
        val textScale = TEXT_SCALE * scale
        val paddingX = PANEL_PADDING_X * scale
        val paddingY = PANEL_PADDING_Y * scale
        val rowHeight = ROW_HEIGHT * scale
        val headSize = if (showHeads) HEAD_SIZE * scale else 0.0f
        val headTextGap = if (showHeads) HEAD_TEXT_GAP * scale else 0.0f
        val columnGap = COLUMN_GAP * scale
        val headerBodyGap = HEADER_BODY_GAP * scale

        var distanceWidth =
            textRenderer.getWidth(EpsilonTranslations.PlayerInfo.DISTANCE.getTranslatedName(), headerScale)
        var healthWidth = textRenderer.getWidth(EpsilonTranslations.PlayerInfo.HEALTH.getTranslatedName(), headerScale)
        var popsWidth = textRenderer.getWidth(EpsilonTranslations.PlayerInfo.POPS.getTranslatedName(), headerScale)
        var directionWidth =
            textRenderer.getWidth(EpsilonTranslations.PlayerInfo.DIRECTION.getTranslatedName(), headerScale)

        for (row in rows) {
            distanceWidth = maxOf(distanceWidth, textRenderer.getWidth(row.distance, textScale))
            healthWidth = maxOf(healthWidth, textRenderer.getWidth(row.health, textScale))
            popsWidth = maxOf(popsWidth, textRenderer.getWidth(row.pops, textScale))
            directionWidth = maxOf(directionWidth, textRenderer.getWidth(row.direction, textScale))
        }

        val panelWidth = panelWidthSetting.toFloat() * scale
        val tableWidth = panelWidth - paddingX * 2.0f
        val nameWidth =
            maxOf(0.0f, tableWidth - distanceWidth - healthWidth - popsWidth - directionWidth - columnGap * 4.0f)
        val headerHeight = textRenderer.getHeight(headerScale)
        val bodyHeight = maxOf(1, rows.size) * rowHeight
        val panelHeight = paddingY * 2.0f + headerHeight + headerBodyGap + bodyHeight

        return Layout(
            scale, headerScale, textScale, paddingX, paddingY, rowHeight, headSize, headTextGap,
            columnGap, headerBodyGap, headerHeight, nameWidth, distanceWidth, healthWidth,
            popsWidth, directionWidth, tableWidth, panelWidth, panelHeight
        )
    }

    private fun drawHeaders(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        layout: Layout,
        panelX: Float,
        panelY: Float
    ) {
        var x = panelX + layout.paddingX
        val y = panelY + layout.paddingY
        val color = headerColor

        val playersText = EpsilonTranslations.PlayerInfo.PLAYERS.getTranslatedName()
        text(scope, playersText, x, y, layout.headerScale, color)
        x += layout.nameWidth + layout.columnGap
        text(scope, EpsilonTranslations.PlayerInfo.DISTANCE.getTranslatedName(), x, y, layout.headerScale, color)
        x += layout.distanceWidth + layout.columnGap
        text(scope, EpsilonTranslations.PlayerInfo.HEALTH.getTranslatedName(), x, y, layout.headerScale, color)
        x += layout.healthWidth + layout.columnGap
        text(scope, EpsilonTranslations.PlayerInfo.POPS.getTranslatedName(), x, y, layout.headerScale, color)
        x += layout.popsWidth + layout.columnGap
        text(scope, EpsilonTranslations.PlayerInfo.DIRECTION.getTranslatedName(), x, y, layout.headerScale, color)
    }

    private fun drawRow(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        layout: Layout,
        row: PlayerRow,
        panelX: Float,
        rowY: Float,
    ) {
        var x = panelX + layout.paddingX
        val textY = rowY + (layout.rowHeight - textRenderer.getHeight(layout.textScale)) * 0.5f

        if (showHeads) {
            val headY = rowY + (layout.rowHeight - layout.headSize) * 0.5f
            scope.playerHead(row.skinTexture, x, headY, layout.headSize, layout.headSize * 0.12f, Color.WHITE)
            x += layout.headSize + layout.headTextGap
        }

        val nameTextWidth = maxOf(0.0f, layout.nameWidth - layout.headSize - layout.headTextGap)
        val fullNameWidth = textRenderer.getWidth(row.name, layout.textScale)
        val playerId = row.playerId
        if (nameTextWidth > 0.0f && fullNameWidth > nameTextWidth) {
            val overflow = fullNameWidth - nameTextWidth
            val scrollOffset = getNameScrollOffset(playerId, overflow, layout.scale)
            val nameX = x
            scope.scissor(
                nameX, rowY, nameTextWidth, layout.rowHeight
            ) { clipped ->
                text(clipped, row.name, nameX - scrollOffset, textY, layout.textScale, row.nameColor)
            }
        } else {
            nameScrollStartTimes.remove(playerId)
            if (nameTextWidth > 0.0f) {
                text(scope, row.name, x, textY, layout.textScale, row.nameColor)
            }
        }
        x = panelX + layout.paddingX + layout.nameWidth + layout.columnGap
        text(scope, row.distance, x, textY, layout.textScale, textColor)
        x += layout.distanceWidth + layout.columnGap
        text(scope, row.health, x, textY, layout.textScale, row.healthColor)
        x += layout.healthWidth + layout.columnGap
        text(scope, row.pops, x, textY, layout.textScale, textColor)
        x += layout.popsWidth + layout.columnGap
        text(scope, row.direction, x, textY, layout.textScale, textColor)
    }

    private fun resolveNameColor(player: AbstractClientPlayer): Color {
        val friendManager = FriendManager
        return if (friendManager != null && friendManager.isFriend(player)) FRIEND_COLOR else textColor
    }

    private fun resolveHealthColor(ratio: Float): Color {
        if (ratio > 0.75f) return HEALTH_HIGH_COLOR
        if (ratio > 0.35f) return HEALTH_MEDIUM_COLOR
        return HEALTH_LOW_COLOR
    }

    private fun withAlpha(color: Color, multiplier: Float): Color {
        val alpha = (color.alpha * multiplier).roundToInt().coerceIn(0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

    private fun getNameScrollOffset(playerId: UUID, overflow: Float, scale: Float): Float {
        val now = Util.getMillis()
        val startedAt = nameScrollStartTimes.getOrPut(playerId) { now }
        val travelDurationMs = overflow / (NAME_SCROLL_SPEED * scale) * 1000.0
        val cycleDurationMs = NAME_SCROLL_HOLD_MS * 2.0 + travelDurationMs * 2.0
        var phase = (now - startedAt) % cycleDurationMs

        if (phase < NAME_SCROLL_HOLD_MS) return 0.0f
        phase -= NAME_SCROLL_HOLD_MS
        if (phase < travelDurationMs) return (overflow * phase / travelDurationMs).toFloat()
        phase -= travelDurationMs
        if (phase < NAME_SCROLL_HOLD_MS) return overflow
        phase -= NAME_SCROLL_HOLD_MS
        return overflow - (overflow * phase / travelDurationMs).toFloat()
    }

    private fun text(scope: UiTree.Scope, text: String, x: Float, y: Float, scale: Float, color: Color) {
        if (textGlow) {
            scope.blurredText(text, x, y, scale, glowRadius.toFloat() * scale, glowIntensity, color)
        }
        scope.text(text, x, y, scale, color)
    }

    private data class PlayerRow(
        val playerId: UUID,
        val skinTexture: Identifier,
        val name: String,
        val distance: String,
        val health: String,
        val pops: String,
        val direction: String,
        val nameColor: Color,
        val healthColor: Color,
    )

    private data class Layout(
        val scale: Float,
        val headerScale: Float,
        val textScale: Float,
        val paddingX: Float,
        val paddingY: Float,
        val rowHeight: Float,
        val headSize: Float,
        val headTextGap: Float,
        val columnGap: Float,
        val headerBodyGap: Float,
        val headerHeight: Float,
        val nameWidth: Float,
        val distanceWidth: Float,
        val healthWidth: Float,
        val popsWidth: Float,
        val directionWidth: Float,
        val tableWidth: Float,
        val panelWidth: Float,
        val panelHeight: Float,
    )


    private const val PANEL_PADDING_X = 8.0f
    private const val PANEL_PADDING_Y = 7.0f
    private const val HEADER_SCALE = 0.68f
    private const val TEXT_SCALE = 0.66f
    private const val ROW_HEIGHT = 19.0f
    private const val HEAD_SIZE = 14.0f
    private const val HEAD_TEXT_GAP = 5.0f
    private const val COLUMN_GAP = 12.0f
    private const val HEADER_BODY_GAP = 6.0f
    private const val NAME_SCROLL_SPEED = 18.0f
    private const val NAME_SCROLL_HOLD_MS = 800L

    private val FRIEND_COLOR: Color = Color(112, 225, 168, 235)
    private val HEALTH_HIGH_COLOR: Color = Color(116, 228, 135, 235)
    private val HEALTH_MEDIUM_COLOR: Color = Color(238, 201, 47, 235)
    private val HEALTH_LOW_COLOR: Color = Color(244, 93, 94, 235)

}
