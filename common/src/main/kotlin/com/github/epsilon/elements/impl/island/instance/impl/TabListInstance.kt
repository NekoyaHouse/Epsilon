package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.modules.impl.render.ACSpoof
import com.github.epsilon.utils.safety.NonNullContext
import net.minecraft.client.gui.components.PlayerTabOverlay
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.*
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.awt.Color
import java.util.*
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.roundToInt

class TabListInstance(
    private val textRendererSupplier: Supplier<TextRenderer>,
    pattern: LCPattern,
) : LandInstance(pattern, 5) {

    private val entries: MutableList<Entry> = ArrayList()
    private val headerLines: MutableList<List<TextSegment>> = ArrayList()
    private val footerLines: MutableList<List<TextSegment>> = ArrayList()
    private val columnWidths = FloatArray(MAX_COLS)

    private lateinit var font: TtfFontLoader
    private var columns: Int = 1
    private var rows: Int = 0
    private var maxNameWidth: Float = 0f
    private var maxScoreWidth: Float = 0f
    private var contentWidth: Float = 0f
    private var listWidth: Float = 0f
    private var headerBoxHeight: Float = 0f
    private var footerBoxHeight: Float = 0f
    private var listHeight: Float = 0f
    private var blockRadius: Float = 0f

    override fun update() {
        resetLayout()
        font = StaticFontLoader.defaultFont()

        val nn = NonNullContext.current
        val connection: ClientPacketListener? = Constants.client.connection
        val scoreboard: Scoreboard? = nn?.level?.scoreboard
        val objective: Objective? = scoreboard?.getDisplayObjective(DisplaySlot.LIST)
        val showScore = objective != null && objective.renderType != ObjectiveCriteria.RenderType.HEARTS

        rebuildEntries(getSortedPlayers(connection), scoreboard, objective, showScore)

        val total = minOf(entries.size, MAX_COLS * MAX_ROWS_PER_COL)
        if (entries.size > total) {
            entries.subList(total, entries.size).clear()
        }

        var computedColumns = 1
        while (computedColumns < MAX_COLS && ceil(total / computedColumns.toDouble()) > MAX_ROWS_PER_COL) {
            computedColumns++
        }
        columns = computedColumns
        rows = if (total == 0) 0 else ceil(total / columns.toDouble()).toInt()

        val gapsWidth = COL_GAP * maxOf(0, columns - 1)
        val screenMaxWidth = maxOf(1f, Constants.client.window.guiScaledWidth - 50f)
        val maxColumnWidth = maxOf(1f, (screenMaxWidth - gapsWidth) / columns)
        val columnWidth = minOf(computeBaseRowWidth(showScore), maxColumnWidth)

        columnWidths.fill(columnWidth)
        contentWidth = columnWidth * columns + gapsWidth

        val overlay: PlayerTabOverlay = Constants.client.gui.hud.tabList
        val textRenderer = textRendererSupplier.get()
        splitComponentByNewline(overlay.header, TEXT_HEADER, HEADER_SCALE, headerLines, textRenderer)
        splitComponentByNewline(overlay.footer, TEXT_HEADER, HEADER_SCALE, footerLines, textRenderer)

        headerBoxHeight = if (headerLines.isEmpty()) 0f else headerLines.size * HEADER_LINE_HEIGHT + HEADER_FOOTER_PADDING_Y * 2f
        footerBoxHeight = if (footerLines.isEmpty()) 0f else footerLines.size * HEADER_LINE_HEIGHT + HEADER_FOOTER_PADDING_Y * 2f

        listWidth = maxOf(contentWidth, computeMaxLineWidth() + INNER_PADDING * 2f)
        if (listWidth > contentWidth && columns > 0) {
            val extraPerColumn = (listWidth - contentWidth) / columns
            for (i in 0 until columns) {
                columnWidths[i] += extraPerColumn
            }
            contentWidth = listWidth
        }

        listHeight = maxOf(1, rows) * ROW_HEIGHT + LIST_INNER_PADDING_Y * 2f
        val headerHeight = if (headerLines.isEmpty()) 0f else headerBoxHeight + LINE_GAP
        val footerHeight = if (footerLines.isEmpty()) 0f else footerBoxHeight + LINE_GAP

        targetRadius = RADIUS
        targetWidth = OUTER_PADDING * 2f + listWidth
        targetHeight = OUTER_PADDING * 2f + headerHeight + listHeight + footerHeight

        val targetRadiusPixels = RADIUS * listWidth * 0.5f
        blockRadius = approximateGnRadius(listWidth, listHeight, targetRadiusPixels)
    }

    private fun resetLayout() {
        entries.clear()
        headerLines.clear()
        footerLines.clear()
        columns = 1
        rows = 0
        maxNameWidth = 0f
        maxScoreWidth = 0f
        contentWidth = 0f
        listWidth = 0f
        headerBoxHeight = 0f
        footerBoxHeight = 0f
        listHeight = 0f
        blockRadius = 0f
    }

    private fun getSortedPlayers(connection: ClientPacketListener?): List<PlayerInfo> {
        val online: Collection<PlayerInfo> = if (connection == null) emptyList() else connection.listedOnlinePlayers
        val sorted: MutableList<PlayerInfo> = ArrayList(online)
        sorted.sortWith(PLAYER_COMPARATOR)
        return sorted
    }

    private fun rebuildEntries(players: List<PlayerInfo>, scoreboard: Scoreboard?, objective: Objective?, showScore: Boolean) {
        val overlay: PlayerTabOverlay = Constants.client.gui.hud.tabList
        val textRenderer = textRendererSupplier.get()

        for (info in players) {
            val spectator = info.gameMode == GameType.SPECTATOR
            val fallbackColor = if (spectator) TEXT_SPECTATOR else resolveTeamColor(info.team)
            val displayName: Component = overlay.getNameForDisplay(info)
            val display = flatten(displayName, fallbackColor, textRenderer)
            val displayWidth = lineWidth(display)

            var scoreText: String? = null
            var scoreWidth = 0f
            if (showScore && scoreboard != null && objective != null) {
                val score: ReadOnlyScoreInfo? = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.profile), objective)
                if (score != null) {
                    scoreText = score.value().toString()
                    scoreWidth = textRenderer.getWidth(scoreText, ROW_SCALE, font)
                }
            }

            entries.add(Entry(info.skin.body().texturePath(), display, info.latency, scoreText, scoreWidth))
            maxNameWidth = maxOf(maxNameWidth, displayWidth)
            maxScoreWidth = maxOf(maxScoreWidth, scoreWidth)
        }
    }

    private fun computeBaseRowWidth(showScore: Boolean): Float {
        var width = INNER_PADDING * 2f + HEAD_SIZE + HEAD_GAP + maxNameWidth
        if (showScore) {
            width += SCORE_GAP + maxScoreWidth
        }
        return width + PING_GAP + PING_ICON_WIDTH
    }

    override fun draw(scope: UiTree.Scope, translateX: Float, translateY: Float, width: Float, height: Float) {
        val left = (width - listWidth) * 0.5f
        var y = OUTER_PADDING

        y = drawHeader(scope, left, y)
        y = if (entries.isEmpty()) drawEmptyList(scope, left, y) else drawList(scope, left, y)
        drawFooter(scope, left, y)
    }

    private fun drawHeader(scope: UiTree.Scope, left: Float, y: Float): Float {
        if (headerLines.isEmpty()) return y
        val radius = computeBlockRadius(listWidth, headerBoxHeight)
        scope.roundRect(left, y, listWidth, headerBoxHeight, radius, fade(HEADER_FOOTER))
        drawCenteredLines(scope, headerLines, left, y, headerBoxHeight)
        return y + headerBoxHeight + LINE_GAP
    }

    private fun drawEmptyList(scope: UiTree.Scope, left: Float, y: Float): Float {
        scope.roundRect(left, y, listWidth, listHeight, blockRadius, fade(ROW_EVEN))

        val text = EpsilonTranslations.PlayerInfo.EMPTY.getTranslatedName()
        val renderer = textRendererSupplier.get()
        val textX = left + (listWidth - renderer.getWidth(text, ROW_SCALE, font)) * 0.5f
        val textY = centerTextY(renderer, ROW_SCALE, y, listHeight)
        drawShadowedText(scope, text, textX, textY, ROW_SCALE, TEXT_DEFAULT)
        return y + listHeight + LINE_GAP
    }

    private fun drawList(scope: UiTree.Scope, left: Float, y: Float): Float {
        val baseColor = if (rows % 2 == 0) ROW_ODD else ROW_EVEN
        scope.roundRect(left, y, listWidth, listHeight, blockRadius, fade(baseColor))

        scope.scissor(
            left, y, listWidth, listHeight
        ) { clipped ->
            var columnX = left
            for (column in 0 until columns) {
                drawColumn(clipped, column, columnX, y + LIST_INNER_PADDING_Y, columnWidths[column])
                columnX += columnWidths[column] + COL_GAP
            }
        }
        return y + listHeight + LINE_GAP
    }

    private fun drawColumn(scope: UiTree.Scope, column: Int, columnX: Float, top: Float, columnWidth: Float) {
        for (row in 0 until rows) {
            val index = row + column * rows
            if (index >= entries.size) break
            val rowY = top + row * ROW_HEIGHT
            drawEntry(scope, entries[index], columnX, rowY, columnWidth)
        }
    }

    private fun drawEntry(scope: UiTree.Scope, entry: Entry, cellX: Float, cellY: Float, cellWidth: Float) {
        val renderer = textRendererSupplier.get()
        val centerY = cellY + ROW_HEIGHT * 0.5f
        var x = cellX + INNER_PADDING
        val headY = centerY - HEAD_SIZE * 0.5f

        scope.playerHead(entry.skin, x, headY, HEAD_SIZE, 0f, fade(Color.WHITE))
        scope.outline(x, headY, HEAD_SIZE, HEAD_SIZE, 0f, HEAD_OUTLINE_WIDTH, fade(Color(255, 255, 255, 0x30)))
        x += HEAD_SIZE + HEAD_GAP

        val textY = centerTextY(renderer, ROW_SCALE, cellY, ROW_HEIGHT)
        for (segment in entry.display) {
            drawShadowedText(scope, segment.text, x, textY, ROW_SCALE, segment.color)
            x += segment.width
        }

        val right = cellX + cellWidth - INNER_PADDING
        val pingX = right - PING_ICON_WIDTH
        drawPing(scope, pingX, centerY, entry.latency)

        val scoreText = entry.scoreText
        if (scoreText != null) {
            val scoreX = pingX - SCORE_GAP - entry.scoreWidth
            drawShadowedText(scope, scoreText, scoreX, textY, ROW_SCALE, TEXT_SCORE)
        }
    }

    private fun drawFooter(scope: UiTree.Scope, left: Float, y: Float) {
        if (footerLines.isNotEmpty()) {
            val radius = computeBlockRadius(listWidth, footerBoxHeight)
            scope.roundRect(left, y, listWidth, footerBoxHeight, radius, fade(HEADER_FOOTER))
            drawCenteredLines(scope, footerLines, left, y, footerBoxHeight)
        }
    }

    private fun drawCenteredLines(
        scope: UiTree.Scope,
        lines: List<List<TextSegment>>,
        left: Float,
        top: Float,
        boxHeight: Float,
    ) {
        val renderer = textRendererSupplier.get()
        val contentHeight = lines.size * HEADER_LINE_HEIGHT
        val contentTop = top + (boxHeight - contentHeight) * 0.5f

        for (i in lines.indices) {
            val line = lines[i]
            var x = (left + (listWidth - lineWidth(line)) * 0.5f).roundToInt().toFloat()
            val textY = centerTextY(renderer, HEADER_SCALE, contentTop + i * HEADER_LINE_HEIGHT, HEADER_LINE_HEIGHT)
            for (segment in line) {
                drawShadowedText(scope, segment.text, x, textY, HEADER_SCALE, segment.color)
                x += segment.width
            }
        }
    }

    private fun drawShadowedText(scope: UiTree.Scope, text: String, x: Float, y: Float, scale: Float, color: Color) {
        if (text.isEmpty()) return
        scope.text(text, x + TEXT_SHADOW_OFFSET, y + TEXT_SHADOW_OFFSET, scale, fade(TEXT_SHADOW), font)
        scope.text(text, x, y, scale, fade(color), font)
    }

    private fun drawPing(scope: UiTree.Scope, x: Float, centerY: Float, latency: Int) {
        val strength: Int = when {
            latency < 0 -> 0
            latency < 150 -> 5
            latency < 300 -> 4
            latency < 600 -> 3
            latency < 1000 -> 2
            else -> 1
        }

        val active = if (strength >= 4) PING_GOOD else if (strength >= 2) PING_MEDIUM else PING_BAD
        val bottom = centerY + PING_MAX_HEIGHT * 0.5f
        for (i in 1..5) {
            val barHeight = PING_MAX_HEIGHT / 5f * i
            val barX = x + (i - 1) * (PING_BAR_WIDTH + PING_BAR_GAP)
            scope.rect(
                barX, bottom - barHeight, PING_BAR_WIDTH, barHeight,
                fade(if (i <= strength) active else PING_BACKGROUND)
            )
        }
    }

    private fun flatten(component: Component, fallback: Color, renderer: TextRenderer): List<TextSegment> {
        val raw: MutableList<TextSegment> = ArrayList()
        component.visit(
            { style: Style, text: String ->
                val color = resolveStyleColor(style, fallback)
                appendLegacySegments(raw, text, color, color, ROW_SCALE, renderer)
                Optional.empty<Any>()
            },
            Style.EMPTY
        )
        return mergeSegments(raw, ROW_SCALE, renderer)
    }

    private fun splitComponentByNewline(
        component: Component?,
        fallback: Color,
        scale: Float,
        output: MutableList<List<TextSegment>>,
        renderer: TextRenderer,
    ) {
        if (component == null) return

        val lines: MutableList<MutableList<TextSegment>> = ArrayList()
        lines.add(ArrayList())
        component.visit(
            { style: Style, text: String ->
                val color = resolveStyleColor(style, fallback)
                appendLegacyIntoLines(lines, text, color, color, scale, renderer)
                Optional.empty<Any>()
            },
            Style.EMPTY
        )

        for (rawLine in lines) {
            val line = mergeSegments(rawLine, scale, renderer)
            if (isBlankLine(line)) continue
            output.add(line)
        }
    }

    private fun appendLegacyIntoLines(
        lines: MutableList<MutableList<TextSegment>>,
        text: String?,
        baseColor: Color,
        resetColor: Color,
        scale: Float,
        renderer: TextRenderer,
    ) {
        if (text == null || text.isEmpty()) return
        var currentColor = baseColor
        val builder = StringBuilder()

        var i = 0
        while (i < text.length) {
            val character = text[i]
            if (character == '\n') {
                flushSegment(lines.last(), builder, currentColor, scale, renderer)
                lines.add(ArrayList())
                currentColor = baseColor
                i++
                continue
            }
            if (character == '\u00a7' && i + 1 < text.length) {
                val code = Character.toLowerCase(text[i + 1])
                if (code == 'x') {
                    val parsed = parseLegacyHexColor(text, i)
                    if (parsed != null) {
                        flushSegment(lines.last(), builder, currentColor, scale, renderer)
                        currentColor = parsed
                        i += 13
                        i++
                        continue
                    }
                }
                val mapped = legacyColor(code)
                if (mapped != null) {
                    flushSegment(lines.last(), builder, currentColor, scale, renderer)
                    currentColor = mapped
                    i += 2
                    continue
                }
                if (code == 'r') {
                    flushSegment(lines.last(), builder, currentColor, scale, renderer)
                    currentColor = resetColor
                }
                i += 2
                continue
            }
            builder.append(character)
            i++
        }
        flushSegment(lines.last(), builder, currentColor, scale, renderer)
    }

    private fun appendLegacySegments(
        output: MutableList<TextSegment>,
        text: String?,
        baseColor: Color,
        resetColor: Color,
        scale: Float,
        renderer: TextRenderer,
    ) {
        if (text == null || text.isEmpty()) return
        var currentColor = baseColor
        val builder = StringBuilder()

        var i = 0
        while (i < text.length) {
            val character = text[i]
            if (character == '\u00a7' && i + 1 < text.length) {
                val code = Character.toLowerCase(text[i + 1])
                if (code == 'x') {
                    val parsed = parseLegacyHexColor(text, i)
                    if (parsed != null) {
                        flushSegment(output, builder, currentColor, scale, renderer)
                        currentColor = parsed
                        i += 14
                        continue
                    }
                }
                val mapped = legacyColor(code)
                if (mapped != null) {
                    flushSegment(output, builder, currentColor, scale, renderer)
                    currentColor = mapped
                    i += 2
                    continue
                }
                if (code == 'r') {
                    flushSegment(output, builder, currentColor, scale, renderer)
                    currentColor = resetColor
                }
                i += 2
                continue
            }
            builder.append(if (character == '\n') ' ' else character)
            i++
        }
        flushSegment(output, builder, currentColor, scale, renderer)
    }

    private fun mergeSegments(raw: List<TextSegment>, scale: Float, renderer: TextRenderer): List<TextSegment> {
        if (raw.isEmpty()) return emptyList()

        var segments = raw
        if (ACSpoof.isEnabled) {
            val original = FormattedCharSequence.composite(
                segments.map { FormattedCharSequence.forward(it.text, Style.EMPTY.withColor(it.color.rgb)) }
            )
            val replacement = ACSpoof.text(original, TEXT_DEFAULT.rgb)
            if (replacement !== original) {
                val spoofed: MutableList<TextSegment> = ArrayList()
                replacement.accept(object : FormattedCharSink {
                    override fun accept(index: Int, style: Style, codePoint: Int): Boolean {
                        spoofed.add(
                            TextSegment(
                                String(Character.toChars(codePoint)), resolveStyleColor(style, TEXT_DEFAULT), 0f
                            )
                        )
                        return true
                    }
                })
                segments = spoofed
            }
        }

        val merged: MutableList<TextSegment> = ArrayList()
        val builder = StringBuilder()
        var color = segments.first().color
        for (segment in segments) {
            if (color == segment.color) {
                builder.append(segment.text)
                continue
            }
            addMergedSegment(merged, builder, color, scale, renderer)
            color = segment.color
            builder.append(segment.text)
        }
        addMergedSegment(merged, builder, color, scale, renderer)
        return merged
    }

    private fun addMergedSegment(output: MutableList<TextSegment>, builder: StringBuilder, color: Color, scale: Float, renderer: TextRenderer) {
        if (builder.isEmpty()) return
        val text = builder.toString()
        builder.setLength(0)
        output.add(TextSegment(text, color, renderer.getWidth(text, scale, font)))
    }

    private fun flushSegment(output: MutableList<TextSegment>, builder: StringBuilder, color: Color, scale: Float, renderer: TextRenderer) {
        if (builder.isEmpty()) return
        val text = builder.toString()
        builder.setLength(0)
        output.add(TextSegment(text, color, renderer.getWidth(text, scale, font)))
    }

    private fun computeMaxLineWidth(): Float {
        var max = 0f
        for (line in headerLines) max = maxOf(max, lineWidth(line))
        for (line in footerLines) max = maxOf(max, lineWidth(line))
        return max
    }

    private fun computeBlockRadius(width: Float, height: Float): Float {
        return approximateGnRadius(width, height, RADIUS * listWidth * 0.5f)
    }

    private data class TextSegment(val text: String, val color: Color, val width: Float)

    private data class Entry(
        val skin: Identifier,
        val display: List<TextSegment>,
        val latency: Int,
        val scoreText: String?,
        val scoreWidth: Float,
    )

    private companion object {

        private const val UI_SCALE = 0.7f
        private const val HEADER_SCALE = 1.15f * UI_SCALE
        private const val ROW_SCALE = 1.0f * UI_SCALE
        private const val RADIUS = 0.15f
        private const val OUTER_PADDING = 12f * UI_SCALE
        private const val INNER_PADDING = 8f * UI_SCALE
        private const val HEADER_FOOTER_PADDING_Y = 4f * UI_SCALE
        private const val LIST_INNER_PADDING_Y = 4f * UI_SCALE
        private const val ROW_HEIGHT = 22f * UI_SCALE
        private const val HEADER_LINE_HEIGHT = 18f * UI_SCALE
        private const val COL_GAP = 8f * UI_SCALE
        private const val LINE_GAP = 8f * UI_SCALE
        private const val MAX_ROWS_PER_COL = 20
        private const val MAX_COLS = 4
        private const val HEAD_SIZE = 12f * UI_SCALE
        private const val HEAD_GAP = 6f * UI_SCALE
        private const val HEAD_OUTLINE_WIDTH = 0.5f * UI_SCALE
        private const val PING_GAP = 8f * UI_SCALE
        private const val PING_ICON_WIDTH = 16f * UI_SCALE
        private const val PING_BAR_WIDTH = 2.5f * UI_SCALE
        private const val PING_BAR_GAP = 1f * UI_SCALE
        private const val PING_MAX_HEIGHT = 11f * UI_SCALE
        private const val SCORE_GAP = 8f * UI_SCALE
        private const val TEXT_SHADOW_OFFSET = 0.65f * UI_SCALE

        private val ROW_EVEN: Color = Color(255, 255, 255, 0x25)
        private val ROW_ODD: Color = Color(255, 255, 255, 0x15)
        private val HEADER_FOOTER: Color = Color(0, 0, 0, 0x30)
        private val TEXT_DEFAULT: Color = Color(0xf0, 0xf0, 0xf0)
        private val TEXT_HEADER: Color = Color.WHITE
        private val TEXT_SPECTATOR: Color = Color(0xa0, 0xa0, 0xa0)
        private val TEXT_SCORE: Color = Color(0xff, 0xff, 0x55)
        private val TEXT_SHADOW: Color = Color(0, 0, 0, 160)
        private val PING_BACKGROUND: Color = Color(255, 255, 255, 0x40)
        private val PING_GOOD: Color = Color(0x55, 0xff, 0x55)
        private val PING_MEDIUM: Color = Color(0xff, 0xff, 0x55)
        private val PING_BAD: Color = Color(0xff, 0x55, 0x55)

        private val PLAYER_COMPARATOR: Comparator<PlayerInfo> = compareBy<PlayerInfo> { -it.tabListOrder }
            .thenBy { if (it.gameMode == GameType.SPECTATOR) 1 else 0 }
            .thenBy { it.team?.name ?: "" }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.name }

        private fun isBlankLine(line: List<TextSegment>): Boolean {
            for (segment in line) {
                if (!segment.text.isBlank()) return false
            }
            return true
        }

        private fun lineWidth(line: List<TextSegment>): Float {
            var width = 0f
            for (segment in line) width += segment.width
            return width
        }

        private fun centerTextY(renderer: TextRenderer, scale: Float, top: Float, height: Float): Float {
            return top + (height - renderer.getHeight(scale)) * 0.5f
        }

        private fun resolveStyleColor(style: Style, fallback: Color): Color {
            val textColor = style.color
            return if (textColor == null) fallback else Color(textColor.value)
        }

        private fun resolveTeamColor(team: PlayerTeam?): Color {
            if (team == null || team.color.isEmpty) return TEXT_DEFAULT
            return Color(team.color.get().rgb())
        }

        private fun approximateGnRadius(width: Float, height: Float, targetRadiusPixels: Float): Float {
            val maximum = minOf(width, height) * 0.5f
            return minOf(targetRadiusPixels, maximum) * 0.8f
        }

        private fun legacyColor(code: Char): Color? {
            return when (code) {
                '0' -> Color(0x000000)
                '1' -> Color(0x0000aa)
                '2' -> Color(0x00aa00)
                '3' -> Color(0x00aaaa)
                '4' -> Color(0xaa0000)
                '5' -> Color(0xaa00aa)
                '6' -> Color(0xffaa00)
                '7' -> Color(0xaaaaaa)
                '8' -> Color(0x555555)
                '9' -> Color(0x5555ff)
                'a' -> Color(0x55ff55)
                'b' -> Color(0x55ffff)
                'c' -> Color(0xff5555)
                'd' -> Color(0xff55ff)
                'e' -> Color(0xffff55)
                'f' -> Color.WHITE
                else -> null
            }
        }

        private fun parseLegacyHexColor(text: String, sectionIndex: Int): Color? {
            if (sectionIndex < 0 || sectionIndex + 13 >= text.length) return null
            if (text[sectionIndex] != '\u00a7' || Character.toLowerCase(text[sectionIndex + 1]) != 'x') {
                return null
            }

            val hex = CharArray(6)
            var index = sectionIndex + 2
            for (i in hex.indices) {
                if (index + 1 >= text.length || text[index] != '\u00a7') return null
                val digit = text[index + 1]
                if (Character.digit(digit, 16) < 0) return null
                hex[i] = digit
                index += 2
            }

            return try {
                Color(Integer.parseInt(String(hex), 16))
            } catch (ignored: NumberFormatException) {
                null
            }
        }

    }

}
