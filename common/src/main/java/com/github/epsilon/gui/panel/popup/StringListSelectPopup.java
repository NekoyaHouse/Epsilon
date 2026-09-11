package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StringListSelectPopup implements PanelPopupHost.Popup {

    private static final float PADDING = 8.0f;
    private static final float TITLE_HEIGHT = 18.0f;
    private static final float INPUT_HEIGHT = 18.0f;
    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float ACTION_WIDTH = 14.0f;
    private static final float ACTION_GAP = 3.0f;
    private static final float SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 1.0f;
    private static final float SCROLL_STEP = 24.0f;
    private static final float SCROLL_DECAY = 0.86f;
    private static final float MIN_SCROLL_VELOCITY = 0.3f;
    private static final int MAX_QUERY_LENGTH = 64;

    private final UiRect bounds;
    private final Setting<List<String>> setting;
    private final Consumer<String> addFn;
    private final Consumer<String> removeFn;
    private final UiContentBuffer contentBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();

    private String input = "";
    private float scroll;
    private float scrollVelocity;
    private float maxScroll;
    private String hoveredRemove;
    private String hoveredMoveUp;
    private String hoveredMoveDown;
    private UiRect lastViewport;

    public StringListSelectPopup(UiRect bounds, Setting<List<String>> setting, Consumer<String> addFn, Consumer<String> removeFn) {
        this.bounds = bounds;
        this.setting = setting;
        this.addFn = addFn;
        this.removeFn = removeFn;
        this.openAnimation.setStartValue(0.0f);
    }

    @Override
    public UiRect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        contentBuffer.clear();
        List<String> entries = entries();
        float contentHeight = entries.size() * (ROW_HEIGHT + ROW_GAP);
        UiRect viewport = getViewport();
        maxScroll = Math.max(0.0f, contentHeight - viewport.height());
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
        updateSmoothScroll(partialTick);

        UiTree tree = UiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            UiRect animatedBounds = new UiRect(bounds.x(), popupY, bounds.width(), bounds.height());
            UiRect inputBounds = getInputBounds(popupY);
            UiRect animatedViewport = getViewport(popupY);
            lastViewport = animatedViewport;
            scope.pushAbsolute(animatedBounds, popup -> {
                popup.popupCard(animatedBounds.atOrigin(), MD3Theme.CARD_RADIUS, MD3Theme.POPUP_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                        MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

                float titleY = centeredTextY(6.0f, TITLE_HEIGHT, 0.68f);
                float summaryScale = 0.52f;
                String summary = setting.getValue().size() + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName();
                popup.text(setting.getDisplayName(), PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY);
                popup.text(summary, animatedBounds.width() - PADDING - textRenderer.getWidth(summary, summaryScale),
                        centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED);

                String placeholder = input.isEmpty() ? EpsilonTranslations.Gui.LIST_TYPE_TO_ADD.getTranslatedName() : input;
                popup.input(inputBounds.relativeTo(animatedBounds), true, 1.0f, 8.0f,
                        placeholder, 0.54f,
                        input.isEmpty() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY,
                        input.length(), MD3Theme.PRIMARY, null, 0.0f, null);
                IMEFocusHelper.updateCursorPos(inputBounds.x() + 8.0f, inputBounds.y() + 4.0f);

                hoveredRemove = null;
                hoveredMoveUp = null;
                hoveredMoveDown = null;
                UiRect localViewport = animatedViewport.relativeTo(animatedBounds);
                popup.viewport(contentBuffer, localViewport, scroll, maxScroll, contentHeight, mouseX, mouseY, content -> {
                    buildEntryList(content, entries, animatedViewport.x(), animatedViewport.y() - scroll,
                            localViewport, mouseX, mouseY, animatedViewport);
                });
            });
        });
        renderBatch.render(tree);
    }

    @Override
    public void flush(UiRenderBatch renderBatch) {
        contentBuffer.flushAndClear();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
        UiRect viewport = lastViewport != null ? lastViewport : getViewport();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
            applyDraggedScroll(event.y(), viewport);
            return true;
        }
        if (hoveredMoveUp != null) {
            moveEntry(hoveredMoveUp, -1);
            return true;
        }
        if (hoveredMoveDown != null) {
            moveEntry(hoveredMoveDown, 1);
            return true;
        }
        if (hoveredRemove != null) {
            removeFn.accept(hoveredRemove);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scrollBarDrag.mouseReleased();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!scrollBarDrag.isDragging()) return false;
        applyDraggedScroll(event.y(), lastViewport != null ? lastViewport : getViewport());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER && !input.isBlank()) {
            addFn.accept(input.trim());
            input = "";
            resetScroll();
            return true;
        }
        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                input = "";
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (input.length() >= MAX_QUERY_LENGTH) return false;
        input += event.codepointAsString();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!getViewport(bounds.y()).contains(mouseX, mouseY) || maxScroll <= 0.0f) return false;
        scrollVelocity -= (float) scrollY * SCROLL_STEP;
        return true;
    }

    private List<String> entries() {
        return new ArrayList<>(setting.getValue());
    }

    private void buildEntryList(UiTree.Scope scope, List<String> entries, float startX, float startY,
                                UiRect localViewport, int mouseX, int mouseY, UiRect viewport) {
        UiRect origin = scope.bound();
        float width = localViewport.width() - (maxScroll > 0.0f ? SCROLLBAR_GUTTER : 0.0f);
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            float rowY = startY + i * (ROW_HEIGHT + ROW_GAP);
            if (rowY + ROW_HEIGHT < viewport.y() || rowY > viewport.bottom()) continue;

            UiRect rowBounds = new UiRect(startX, rowY, width, ROW_HEIGHT);
            boolean hovered = rowBounds.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            boolean canMoveUp = i > 0;
            boolean canMoveDown = i < entries.size() - 1;
            UiRect removeBounds = actionBounds(rowBounds, 0);
            UiRect downBounds = actionBounds(rowBounds, 1);
            UiRect upBounds = actionBounds(rowBounds, 2);
            boolean removeHovered = hovered && removeBounds.contains(mouseX, mouseY);
            boolean downHovered = hovered && canMoveDown && downBounds.contains(mouseX, mouseY);
            boolean upHovered = hovered && canMoveUp && upBounds.contains(mouseX, mouseY);
            if (removeHovered) hoveredRemove = entry;
            if (downHovered) hoveredMoveDown = entry;
            if (upHovered) hoveredMoveUp = entry;

            float bgHover = hovered ? 0.45f : 0.0f;
            float actionAreaWidth = ACTION_WIDTH * 3.0f + ACTION_GAP * 2.0f;
            final String display = trim(entry, 0.50f, rowBounds.width() - actionAreaWidth - 14.0f);
            UiRect localRowBounds = rowBounds.relativeTo(origin);
            scope.pushRelative(localRowBounds, row -> {
                row.roundRect(0.0f, 0.0f, rowBounds.width(), rowBounds.height(), MD3Theme.CONTROL_RADIUS,
                        MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, bgHover));
                row.text(display, 6.0f, centeredTextY(0.0f, rowBounds.height(), 0.50f), 0.50f, MD3Theme.ON_SECONDARY_CONTAINER);
                drawAction(row, upBounds.relativeTo(rowBounds), "↑", canMoveUp, upHovered);
                drawAction(row, downBounds.relativeTo(rowBounds), "↓", canMoveDown, downHovered);
                drawAction(row, removeBounds.relativeTo(rowBounds), "-", true, removeHovered);
            });
        }
    }

    private UiRect actionBounds(UiRect rowBounds, int indexFromRight) {
        float x = rowBounds.right() - 6.0f - ACTION_WIDTH - indexFromRight * (ACTION_WIDTH + ACTION_GAP);
        return new UiRect(x, rowBounds.y() + 2.0f, ACTION_WIDTH, rowBounds.height() - 4.0f);
    }

    private void drawAction(UiTree.Scope scope, UiRect bounds, String label, boolean enabled, boolean hovered) {
        float textScale = 0.50f;
        if (enabled && hovered) {
            scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4.0f,
                    MD3Theme.withAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 34));
        }
        scope.text(label,
                bounds.x() + (bounds.width() - textRenderer.getWidth(label, textScale)) / 2.0f,
                centeredTextY(bounds.y(), bounds.height(), textScale),
                textScale,
                enabled ? MD3Theme.ON_SECONDARY_CONTAINER : MD3Theme.withAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 80));
    }

    private void moveEntry(String entry, int offset) {
        List<String> next = new ArrayList<>(setting.getValue());
        int index = next.indexOf(entry);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= next.size()) {
            return;
        }
        String value = next.remove(index);
        next.add(target, value);
        setting.setValue(next);
    }

    private UiRect getInputBounds(float popupY) {
        return new UiRect(bounds.x() + PADDING, popupY + TITLE_HEIGHT + 10.0f, bounds.width() - PADDING * 2.0f, INPUT_HEIGHT);
    }

    private UiRect getViewport() {
        return getViewport(bounds.y());
    }

    private UiRect getViewport(float popupY) {
        float y = popupY + TITLE_HEIGHT + INPUT_HEIGHT + 20.0f;
        return new UiRect(bounds.x() + PADDING, y, bounds.width() - PADDING * 2.0f, bounds.bottom() - y - PADDING);
    }

    private void updateSmoothScroll(float partialTick) {
        if (maxScroll <= 0.0f) {
            resetScroll();
            return;
        }
        if (Math.abs(scrollVelocity) <= 0.01f || partialTick <= 0.0f) return;
        float nextScroll = Mth.clamp(scroll + scrollVelocity * partialTick, 0.0f, maxScroll);
        if (Float.compare(nextScroll, scroll) == 0) {
            scrollVelocity = 0.0f;
            return;
        }
        scroll = nextScroll;
        scrollVelocity *= SCROLL_DECAY;
        if (Math.abs(scrollVelocity) < MIN_SCROLL_VELOCITY) scrollVelocity = 0.0f;
    }

    private void resetScroll() {
        scroll = 0.0f;
        scrollVelocity = 0.0f;
    }

    private void applyDraggedScroll(double mouseY, UiRect viewport) {
        float newScroll = scrollBarDrag.mouseDragged(mouseY, viewport, maxScroll);
        if (newScroll >= 0.0f) {
            scroll = Mth.clamp(newScroll, 0.0f, maxScroll);
            scrollVelocity = 0.0f;
        }
    }

    private String trim(String value, float scale, float maxWidth) {
        if (value == null || value.isEmpty()) return "";
        int maxChars = Math.max(3, (int) (maxWidth / (5.0f * scale)));
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private float centeredTextY(float boxY, float boxHeight, float scale) {
        return boxY + (boxHeight - textRenderer.getHeight(scale)) * 0.5f;
    }

    @Override
    public void close() {
        contentBuffer.close();
    }
}
