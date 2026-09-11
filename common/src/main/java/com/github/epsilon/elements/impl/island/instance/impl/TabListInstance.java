package com.github.epsilon.elements.impl.island.instance.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.impl.island.instance.LandInstance;
import com.github.epsilon.elements.impl.island.pattern.LCPattern;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.UiTree;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

import static com.github.epsilon.Constants.mc;

public class TabListInstance extends LandInstance {

    private static final float UI_SCALE = 0.7f;
    private static final float HEADER_SCALE = 1.15f * UI_SCALE;
    private static final float ROW_SCALE = 1.0f * UI_SCALE;
    private static final float RADIUS = 0.15f;
    private static final float OUTER_PADDING = 12f * UI_SCALE;
    private static final float INNER_PADDING = 8f * UI_SCALE;
    private static final float HEADER_FOOTER_PADDING_Y = 4f * UI_SCALE;
    private static final float LIST_INNER_PADDING_Y = 4f * UI_SCALE;
    private static final float ROW_HEIGHT = 22f * UI_SCALE;
    private static final float HEADER_LINE_HEIGHT = 18f * UI_SCALE;
    private static final float COL_GAP = 8f * UI_SCALE;
    private static final float LINE_GAP = 8f * UI_SCALE;
    private static final int MAX_ROWS_PER_COL = 20;
    private static final int MAX_COLS = 4;
    private static final float HEAD_SIZE = 12f * UI_SCALE;
    private static final float HEAD_GAP = 6f * UI_SCALE;
    private static final float HEAD_OUTLINE_WIDTH = 0.5f * UI_SCALE;
    private static final float PING_GAP = 8f * UI_SCALE;
    private static final float PING_ICON_WIDTH = 16f * UI_SCALE;
    private static final float PING_BAR_WIDTH = 2.5f * UI_SCALE;
    private static final float PING_BAR_GAP = 1f * UI_SCALE;
    private static final float PING_MAX_HEIGHT = 11f * UI_SCALE;
    private static final float SCORE_GAP = 8f * UI_SCALE;
    private static final float TEXT_SHADOW_OFFSET = 0.65f * UI_SCALE;

    private static final Color ROW_EVEN = new Color(255, 255, 255, 0x25);
    private static final Color ROW_ODD = new Color(255, 255, 255, 0x15);
    private static final Color HEADER_FOOTER = new Color(0, 0, 0, 0x30);
    private static final Color TEXT_DEFAULT = new Color(0xf0, 0xf0, 0xf0);
    private static final Color TEXT_HEADER = Color.WHITE;
    private static final Color TEXT_SPECTATOR = new Color(0xa0, 0xa0, 0xa0);
    private static final Color TEXT_SCORE = new Color(0xff, 0xff, 0x55);
    private static final Color TEXT_SHADOW = new Color(0, 0, 0, 160);
    private static final Color PING_BACKGROUND = new Color(255, 255, 255, 0x40);
    private static final Color PING_GOOD = new Color(0x55, 0xff, 0x55);
    private static final Color PING_MEDIUM = new Color(0xff, 0xff, 0x55);
    private static final Color PING_BAD = new Color(0xff, 0x55, 0x55);

    private static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator
            .<PlayerInfo>comparingInt(info -> -info.getTabListOrder())
            .thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
            .thenComparing(info -> Optional.ofNullable(info.getTeam()).map(PlayerTeam::getName).orElse(""))
            .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);

    private final Supplier<TextRenderer> textRendererSupplier;
    private final List<Entry> entries = new ArrayList<>();
    private final List<List<TextSegment>> headerLines = new ArrayList<>();
    private final List<List<TextSegment>> footerLines = new ArrayList<>();
    private final float[] columnWidths = new float[MAX_COLS];

    private TtfFontLoader font;
    private int columns = 1;
    private int rows;
    private float maxNameWidth;
    private float maxScoreWidth;
    private float contentWidth;
    private float listWidth;
    private float headerBoxHeight;
    private float footerBoxHeight;
    private float listHeight;
    private float blockRadius;

    public TabListInstance(Supplier<TextRenderer> textRendererSupplier, LCPattern pattern) {
        super(pattern, 5);
        this.textRendererSupplier = textRendererSupplier;
    }

    @Override
    public void update() {
        resetLayout();
        font = StaticFontLoader.defaultFont();

        ClientPacketListener connection = mc.getConnection();
        Scoreboard scoreboard = mc.level == null ? null : mc.level.getScoreboard();
        Objective objective = scoreboard == null ? null : scoreboard.getDisplayObjective(DisplaySlot.LIST);
        boolean showScore = objective != null && objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS;

        rebuildEntries(getSortedPlayers(connection), scoreboard, objective, showScore);

        int total = Math.min(entries.size(), MAX_COLS * MAX_ROWS_PER_COL);
        if (entries.size() > total) {
            entries.subList(total, entries.size()).clear();
        }

        int computedColumns = 1;
        while (computedColumns < MAX_COLS && Math.ceil(total / (double) computedColumns) > MAX_ROWS_PER_COL) {
            computedColumns++;
        }
        columns = computedColumns;
        rows = total == 0 ? 0 : (int) Math.ceil(total / (double) columns);

        float gapsWidth = COL_GAP * Math.max(0, columns - 1);
        float screenMaxWidth = Math.max(1f, mc.getWindow().getGuiScaledWidth() - 50f);
        float maxColumnWidth = Math.max(1f, (screenMaxWidth - gapsWidth) / columns);
        float columnWidth = Math.min(computeBaseRowWidth(showScore), maxColumnWidth);

        Arrays.fill(columnWidths, columnWidth);
        contentWidth = columnWidth * columns + gapsWidth;

        PlayerTabOverlay overlay = mc.gui.hud.getTabList();
        TextRenderer textRenderer = textRendererSupplier.get();
        splitComponentByNewline(overlay.header, TEXT_HEADER, HEADER_SCALE, headerLines, textRenderer);
        splitComponentByNewline(overlay.footer, TEXT_HEADER, HEADER_SCALE, footerLines, textRenderer);

        headerBoxHeight = headerLines.isEmpty() ? 0f : headerLines.size() * HEADER_LINE_HEIGHT + HEADER_FOOTER_PADDING_Y * 2f;
        footerBoxHeight = footerLines.isEmpty() ? 0f : footerLines.size() * HEADER_LINE_HEIGHT + HEADER_FOOTER_PADDING_Y * 2f;

        listWidth = Math.max(contentWidth, computeMaxLineWidth() + INNER_PADDING * 2f);
        if (listWidth > contentWidth && columns > 0) {
            float extraPerColumn = (listWidth - contentWidth) / columns;
            for (int i = 0; i < columns; i++) {
                columnWidths[i] += extraPerColumn;
            }
            contentWidth = listWidth;
        }

        listHeight = Math.max(1, rows) * ROW_HEIGHT + LIST_INNER_PADDING_Y * 2f;
        float headerHeight = headerLines.isEmpty() ? 0f : headerBoxHeight + LINE_GAP;
        float footerHeight = footerLines.isEmpty() ? 0f : footerBoxHeight + LINE_GAP;

        targetRadius = RADIUS;
        targetWidth = OUTER_PADDING * 2f + listWidth;
        targetHeight = OUTER_PADDING * 2f + headerHeight + listHeight + footerHeight;

        float targetRadiusPixels = RADIUS * listWidth * 0.5f;
        blockRadius = approximateGnRadius(listWidth, listHeight, targetRadiusPixels);
    }

    private void resetLayout() {
        entries.clear();
        headerLines.clear();
        footerLines.clear();
        columns = 1;
        rows = 0;
        maxNameWidth = 0f;
        maxScoreWidth = 0f;
        contentWidth = 0f;
        listWidth = 0f;
        headerBoxHeight = 0f;
        footerBoxHeight = 0f;
        listHeight = 0f;
        blockRadius = 0f;
    }

    private static List<PlayerInfo> getSortedPlayers(ClientPacketListener connection) {
        Collection<PlayerInfo> online = connection == null ? List.of() : connection.getListedOnlinePlayers();
        List<PlayerInfo> sorted = new ArrayList<>(online);
        sorted.sort(PLAYER_COMPARATOR);
        return sorted;
    }

    private void rebuildEntries(List<PlayerInfo> players, Scoreboard scoreboard, Objective objective, boolean showScore) {
        PlayerTabOverlay overlay = mc.gui.hud.getTabList();
        TextRenderer textRenderer = textRendererSupplier.get();

        for (PlayerInfo info : players) {
            boolean spectator = info.getGameMode() == GameType.SPECTATOR;
            Color fallbackColor = spectator ? TEXT_SPECTATOR : resolveTeamColor(info.getTeam());
            Component displayName = overlay.getNameForDisplay(info);
            List<TextSegment> display = flatten(displayName, fallbackColor, textRenderer);
            float displayWidth = lineWidth(display);

            String scoreText = null;
            float scoreWidth = 0f;
            if (showScore && scoreboard != null && objective != null) {
                ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.getProfile()), objective);
                if (score != null) {
                    scoreText = Integer.toString(score.value());
                    scoreWidth = textRenderer.getWidth(scoreText, ROW_SCALE, font);
                }
            }

            entries.add(new Entry(info.getSkin().body().texturePath(), display, info.getLatency(), scoreText, scoreWidth));
            maxNameWidth = Math.max(maxNameWidth, displayWidth);
            maxScoreWidth = Math.max(maxScoreWidth, scoreWidth);
        }
    }

    private float computeBaseRowWidth(boolean showScore) {
        float width = INNER_PADDING * 2f + HEAD_SIZE + HEAD_GAP + maxNameWidth;
        if (showScore) {
            width += SCORE_GAP + maxScoreWidth;
        }
        return width + PING_GAP + PING_ICON_WIDTH;
    }

    @Override
    public void draw(UiTree.Scope scope, float translateX, float translateY, float width, float height) {
        float left = (width - listWidth) * 0.5f;
        float y = OUTER_PADDING;

        y = drawHeader(scope, left, y);
        y = entries.isEmpty() ? drawEmptyList(scope, left, y) : drawList(scope, left, y);
        drawFooter(scope, left, y);
    }

    private float drawHeader(UiTree.Scope scope, float left, float y) {
        if (headerLines.isEmpty()) return y;
        float radius = blockRadius(listWidth, headerBoxHeight);
        scope.roundRect(left, y, listWidth, headerBoxHeight, radius, fade(HEADER_FOOTER));
        drawCenteredLines(scope, headerLines, left, y, headerBoxHeight);
        return y + headerBoxHeight + LINE_GAP;
    }

    private float drawEmptyList(UiTree.Scope scope, float left, float y) {
        scope.roundRect(left, y, listWidth, listHeight, blockRadius, fade(ROW_EVEN));

        String text = EpsilonTranslations.PlayerInfo.EMPTY.getTranslatedName();
        TextRenderer renderer = textRendererSupplier.get();
        float textX = left + (listWidth - renderer.getWidth(text, ROW_SCALE, font)) * 0.5f;
        float textY = centerTextY(renderer, ROW_SCALE, y, listHeight);
        drawShadowedText(scope, text, textX, textY, ROW_SCALE, TEXT_DEFAULT);
        return y + listHeight + LINE_GAP;
    }

    private float drawList(UiTree.Scope scope, float left, float y) {
        Color baseColor = rows % 2 == 0 ? ROW_ODD : ROW_EVEN;
        scope.roundRect(left, y, listWidth, listHeight, blockRadius, fade(baseColor));

        scope.scissor(left, y, listWidth, listHeight, clipped -> {
            float columnX = left;
            for (int column = 0; column < columns; column++) {
                drawColumn(clipped, column, columnX, y + LIST_INNER_PADDING_Y, columnWidths[column]);
                columnX += columnWidths[column] + COL_GAP;
            }
        });
        return y + listHeight + LINE_GAP;
    }

    private void drawColumn(UiTree.Scope scope, int column, float columnX, float top, float columnWidth) {
        for (int row = 0; row < rows; row++) {
            int index = row + column * rows;
            if (index >= entries.size()) break;
            float rowY = top + row * ROW_HEIGHT;
            drawEntry(scope, entries.get(index), columnX, rowY, columnWidth);
        }
    }

    private void drawEntry(UiTree.Scope scope, Entry entry, float cellX, float cellY, float cellWidth) {
        TextRenderer renderer = textRendererSupplier.get();
        float centerY = cellY + ROW_HEIGHT * 0.5f;
        float x = cellX + INNER_PADDING;
        float headY = centerY - HEAD_SIZE * 0.5f;

        scope.playerHead(entry.skin(), x, headY, HEAD_SIZE, 0f, fade(Color.WHITE));
        scope.outline(x, headY, HEAD_SIZE, HEAD_SIZE, 0f, HEAD_OUTLINE_WIDTH, fade(new Color(255, 255, 255, 0x30)));
        x += HEAD_SIZE + HEAD_GAP;

        float textY = centerTextY(renderer, ROW_SCALE, cellY, ROW_HEIGHT);
        for (TextSegment segment : entry.display()) {
            drawShadowedText(scope, segment.text(), x, textY, ROW_SCALE, segment.color());
            x += segment.width();
        }

        float right = cellX + cellWidth - INNER_PADDING;
        float pingX = right - PING_ICON_WIDTH;
        drawPing(scope, pingX, centerY, entry.latency());

        if (entry.scoreText() != null) {
            float scoreX = pingX - SCORE_GAP - entry.scoreWidth();
            drawShadowedText(scope, entry.scoreText(), scoreX, textY, ROW_SCALE, TEXT_SCORE);
        }
    }

    private void drawFooter(UiTree.Scope scope, float left, float y) {
        if (!footerLines.isEmpty()) {
            float radius = blockRadius(listWidth, footerBoxHeight);
            scope.roundRect(left, y, listWidth, footerBoxHeight, radius, fade(HEADER_FOOTER));
            drawCenteredLines(scope, footerLines, left, y, footerBoxHeight);
        }
    }

    private void drawCenteredLines(UiTree.Scope scope, List<List<TextSegment>> lines, float left, float top, float boxHeight) {
        TextRenderer renderer = textRendererSupplier.get();
        float contentHeight = lines.size() * HEADER_LINE_HEIGHT;
        float contentTop = top + (boxHeight - contentHeight) * 0.5f;

        for (int i = 0; i < lines.size(); i++) {
            List<TextSegment> line = lines.get(i);
            float x = Math.round(left + (listWidth - lineWidth(line)) * 0.5f);
            float textY = centerTextY(renderer, HEADER_SCALE, contentTop + i * HEADER_LINE_HEIGHT, HEADER_LINE_HEIGHT);
            for (TextSegment segment : line) {
                drawShadowedText(scope, segment.text(), x, textY, HEADER_SCALE, segment.color());
                x += segment.width();
            }
        }
    }

    private void drawShadowedText(UiTree.Scope scope, String text, float x, float y, float scale, Color color) {
        if (text.isEmpty()) return;
        scope.text(text, x + TEXT_SHADOW_OFFSET, y + TEXT_SHADOW_OFFSET, scale, fade(TEXT_SHADOW), font);
        scope.text(text, x, y, scale, fade(color), font);
    }

    private void drawPing(UiTree.Scope scope, float x, float centerY, int latency) {
        int strength;
        if (latency < 0) strength = 0;
        else if (latency < 150) strength = 5;
        else if (latency < 300) strength = 4;
        else if (latency < 600) strength = 3;
        else if (latency < 1000) strength = 2;
        else strength = 1;

        Color active = strength >= 4 ? PING_GOOD : strength >= 2 ? PING_MEDIUM : PING_BAD;
        float bottom = centerY + PING_MAX_HEIGHT * 0.5f;
        for (int i = 1; i <= 5; i++) {
            float barHeight = PING_MAX_HEIGHT / 5f * i;
            float barX = x + (i - 1) * (PING_BAR_WIDTH + PING_BAR_GAP);
            scope.rect(barX, bottom - barHeight, PING_BAR_WIDTH, barHeight,
                    fade(i <= strength ? active : PING_BACKGROUND));
        }
    }

    private List<TextSegment> flatten(Component component, Color fallback, TextRenderer renderer) {
        List<TextSegment> raw = new ArrayList<>();
        component.visit((style, text) -> {
            Color color = resolveStyleColor(style, fallback);
            appendLegacySegments(raw, text, color, color, ROW_SCALE, renderer);
            return Optional.empty();
        }, Style.EMPTY);
        return mergeSegments(raw, ROW_SCALE, renderer);
    }

    private void splitComponentByNewline(Component component, Color fallback, float scale, List<List<TextSegment>> output, TextRenderer renderer) {
        if (component == null) return;

        List<List<TextSegment>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        component.visit((style, text) -> {
            Color color = resolveStyleColor(style, fallback);
            appendLegacyIntoLines(lines, text, color, color, scale, renderer);
            return Optional.empty();
        }, Style.EMPTY);

        for (List<TextSegment> rawLine : lines) {
            List<TextSegment> line = mergeSegments(rawLine, scale, renderer);
            if (isBlankLine(line)) continue;
            output.add(line);
        }
    }

    private static boolean isBlankLine(List<TextSegment> line) {
        for (TextSegment segment : line) {
            if (!segment.text().isBlank()) return false;
        }
        return true;
    }

    private void appendLegacyIntoLines(List<List<TextSegment>> lines, String text, Color baseColor, Color resetColor, float scale, TextRenderer renderer) {
        if (text == null || text.isEmpty()) return;
        Color currentColor = baseColor;
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\n') {
                flushSegment(lines.getLast(), builder, currentColor, scale, renderer);
                lines.add(new ArrayList<>());
                currentColor = baseColor;
                continue;
            }
            if (character == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'x') {
                    Color parsed = parseLegacyHexColor(text, i);
                    if (parsed != null) {
                        flushSegment(lines.getLast(), builder, currentColor, scale, renderer);
                        currentColor = parsed;
                        i += 13;
                        continue;
                    }
                }
                Color mapped = legacyColor(code);
                if (mapped != null) {
                    flushSegment(lines.getLast(), builder, currentColor, scale, renderer);
                    currentColor = mapped;
                    i++;
                    continue;
                }
                if (code == 'r') {
                    flushSegment(lines.getLast(), builder, currentColor, scale, renderer);
                    currentColor = resetColor;
                }
                i++;
                continue;
            }
            builder.append(character);
        }
        flushSegment(lines.getLast(), builder, currentColor, scale, renderer);
    }

    private void appendLegacySegments(List<TextSegment> output, String text, Color baseColor, Color resetColor, float scale, TextRenderer renderer) {
        if (text == null || text.isEmpty()) return;
        Color currentColor = baseColor;
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'x') {
                    Color parsed = parseLegacyHexColor(text, i);
                    if (parsed != null) {
                        flushSegment(output, builder, currentColor, scale, renderer);
                        currentColor = parsed;
                        i += 13;
                        continue;
                    }
                }
                Color mapped = legacyColor(code);
                if (mapped != null) {
                    flushSegment(output, builder, currentColor, scale, renderer);
                    currentColor = mapped;
                    i++;
                    continue;
                }
                if (code == 'r') {
                    flushSegment(output, builder, currentColor, scale, renderer);
                    currentColor = resetColor;
                }
                i++;
                continue;
            }
            builder.append(character == '\n' ? ' ' : character);
        }
        flushSegment(output, builder, currentColor, scale, renderer);
    }

    private List<TextSegment> mergeSegments(List<TextSegment> raw, float scale, TextRenderer renderer) {
        if (raw.isEmpty()) return List.of();

        List<TextSegment> merged = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        Color color = raw.getFirst().color();
        for (TextSegment segment : raw) {
            if (color.equals(segment.color())) {
                builder.append(segment.text());
                continue;
            }
            addMergedSegment(merged, builder, color, scale, renderer);
            color = segment.color();
            builder.append(segment.text());
        }
        addMergedSegment(merged, builder, color, scale, renderer);
        return merged;
    }

    private void addMergedSegment(List<TextSegment> output, StringBuilder builder, Color color, float scale, TextRenderer renderer) {
        if (builder.isEmpty()) return;
        String text = builder.toString();
        builder.setLength(0);
        output.add(new TextSegment(text, color, renderer.getWidth(text, scale, font)));
    }

    private void flushSegment(List<TextSegment> output, StringBuilder builder, Color color, float scale, TextRenderer renderer) {
        if (builder.isEmpty()) return;
        String text = builder.toString();
        builder.setLength(0);
        output.add(new TextSegment(text, color, renderer.getWidth(text, scale, font)));
    }

    private float computeMaxLineWidth() {
        float max = 0f;
        for (List<TextSegment> line : headerLines) max = Math.max(max, lineWidth(line));
        for (List<TextSegment> line : footerLines) max = Math.max(max, lineWidth(line));
        return max;
    }

    private static float lineWidth(List<TextSegment> line) {
        float width = 0f;
        for (TextSegment segment : line) width += segment.width();
        return width;
    }

    private static float centerTextY(TextRenderer renderer, float scale, float top, float height) {
        return top + (height - renderer.getHeight(scale)) * 0.5f;
    }

    private static Color resolveStyleColor(Style style, Color fallback) {
        return style.getColor() == null ? fallback : new Color(style.getColor().getValue());
    }

    private static Color resolveTeamColor(PlayerTeam team) {
        if (team == null || team.getColor().isEmpty()) return TEXT_DEFAULT;
        return new Color(team.getColor().get().rgb());
    }

    private static float approximateGnRadius(float width, float height, float targetRadiusPixels) {
        float maximum = Math.min(width, height) * 0.5f;
        return Math.min(targetRadiusPixels, maximum) * 0.8f;
    }

    private float blockRadius(float width, float height) {
        return approximateGnRadius(width, height, RADIUS * listWidth * 0.5f);
    }

    private static Color legacyColor(char code) {
        return switch (code) {
            case '0' -> new Color(0x000000);
            case '1' -> new Color(0x0000aa);
            case '2' -> new Color(0x00aa00);
            case '3' -> new Color(0x00aaaa);
            case '4' -> new Color(0xaa0000);
            case '5' -> new Color(0xaa00aa);
            case '6' -> new Color(0xffaa00);
            case '7' -> new Color(0xaaaaaa);
            case '8' -> new Color(0x555555);
            case '9' -> new Color(0x5555ff);
            case 'a' -> new Color(0x55ff55);
            case 'b' -> new Color(0x55ffff);
            case 'c' -> new Color(0xff5555);
            case 'd' -> new Color(0xff55ff);
            case 'e' -> new Color(0xffff55);
            case 'f' -> Color.WHITE;
            default -> null;
        };
    }

    private static Color parseLegacyHexColor(String text, int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex + 13 >= text.length()) return null;
        if (text.charAt(sectionIndex) != '\u00a7' || Character.toLowerCase(text.charAt(sectionIndex + 1)) != 'x')
            return null;

        char[] hex = new char[6];
        int index = sectionIndex + 2;
        for (int i = 0; i < hex.length; i++) {
            if (index + 1 >= text.length() || text.charAt(index) != '\u00a7') return null;
            char digit = text.charAt(index + 1);
            if (Character.digit(digit, 16) < 0) return null;
            hex[i] = digit;
            index += 2;
        }

        try {
            return new Color(Integer.parseInt(new String(hex), 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record TextSegment(String text, Color color, float width) {
    }

    private record Entry(Identifier skin, List<TextSegment> display, int latency, String scoreText, float scoreWidth) {
    }

}
