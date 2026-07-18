package com.github.epsilon.gui.panel.view.settings;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.lib.state.UiInvalidationState;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class FriendClientSettingTab implements ClientSettingTabView {
    private static final float FRIEND_ROW_HEIGHT = 30.0f;
    private static final float FRIEND_INPUT_HEIGHT = 28.0f;
    private static final float FRIEND_INPUT_BOTTOM_MARGIN = 4.0f;
    private static final float FRIEND_INPUT_FIELD_SCALE = 0.8f;
    private static final int MAX_FRIEND_NAME_LENGTH = 32;

    private final PanelState state;
    private final TextRenderer textRenderer;
    private final UiContentBuffer contentBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final UiInvalidationState contentState = new UiInvalidationState();
    private final Map<String, Animation> rowHoverAnimations = new HashMap<>();
    private final Map<String, Animation> removeHoverAnimations = new HashMap<>();
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();
    private final List<FriendRowEntry> rowEntries = new ArrayList<>();
    private final ClientSettingTextField inputField = new ClientSettingTextField(MAX_FRIEND_NAME_LENGTH);

    private UiRect bounds;
    private float lastScroll = Float.NaN;
    private List<String> lastFriendList = List.of();
    private long lastContentSignature = Long.MIN_VALUE;
    private float scrollVelocity = 0;

    public FriendClientSettingTab(PanelState state, TextRenderer textRenderer) {
        this.state = state;
        this.textRenderer = textRenderer;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollFriend(scrollVelocity * partialTick);
            scrollVelocity *= 0.86f;
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0;
            }
            markDirty();
        }

        UiRect inputBounds = getInputBounds(bounds);
        UiRect listViewport = getListViewport(bounds);
        List<String> friends = Managers.FRIEND.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        float contentHeight = friends.size() * (FRIEND_ROW_HEIGHT + MD3Theme.ROW_GAP);
        state.setMaxFriendScroll(contentHeight - listViewport.height());
        float maxScroll = Math.max(0.0f, contentHeight - listViewport.height());
        boolean hasScrollBar = maxScroll > 0.0f;
        float rowWidth = hasScrollBar ? listViewport.width() - ScrollBarUtils.TOTAL_WIDTH : listViewport.width();
        long contentSignature = buildContentSignature(friends);
        boolean rebuildContent = shouldRebuild(listViewport, mouseX, mouseY, friends, guiGraphics.guiHeight(), contentSignature);

        if (rebuildContent) {
            contentBuffer.clear();
            contentState.beginRebuild();
            rowEntries.clear();
            rowHoverAnimations.keySet().removeIf(name -> !friends.contains(name));
            removeHoverAnimations.keySet().removeIf(name -> !friends.contains(name));
        }

        UiTree tree = UiTree.build(scope -> {
            inputField.buildUi(scope, inputBounds, mouseX, mouseY, textRenderer,
                    EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), FRIEND_INPUT_FIELD_SCALE, "↵");
            scope.viewport(contentBuffer, listViewport, state.getFriendScroll(), maxScroll, contentHeight, mouseX, mouseY, content -> {
                if (!rebuildContent) {
                    return;
                }
                float rowY = listViewport.y() - state.getFriendScroll();
                for (String friendName : friends) {
                    UiRect rowBounds = new UiRect(listViewport.x(), rowY, rowWidth, FRIEND_ROW_HEIGHT);
                    UiRect removeBounds = getRemoveButtonBounds(rowBounds);
                    rowEntries.add(new FriendRowEntry(friendName, rowBounds, removeBounds));

                    Animation hoverAnimation = rowHoverAnimations.computeIfAbsent(friendName, ignored -> createAnimation());
                    Animation removeAnimation = removeHoverAnimations.computeIfAbsent(friendName, ignored -> createAnimation());
                    hoverAnimation.run(rowBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    removeAnimation.run(removeBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    contentState.noteAnimation(!hoverAnimation.isFinished() || !removeAnimation.isFinished());

                    content.pushAbsolute(rowBounds, rowScope ->
                            buildFriendRow(rowScope, friendName, rowBounds, removeBounds,
                                    hoverAnimation.getValue(), removeAnimation.getValue()));
                    rowY += FRIEND_ROW_HEIGHT + MD3Theme.ROW_GAP;
                }

                if (friends.isEmpty()) {
                    float hintScale = 0.58f;
                    String hint = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName();
                    float hintWidth = textRenderer.getWidth(hint, hintScale);
                    float hintX = (listViewport.width() - hintWidth) / 2.0f;
                    float hintY = state.getFriendScroll() + listViewport.height() / 2.0f - textRenderer.getHeight(hintScale) / 2.0f;
                    content.text(hint, hintX, hintY, hintScale, MD3Theme.TEXT_MUTED);
                }
            });
        });
        renderBatch.render(tree);

        if (rebuildContent) {
            rememberSnapshot(listViewport, mouseX, mouseY, friends, guiGraphics.guiHeight(), contentSignature);
        }
    }

    @Override
    public void flushContent() {
        contentBuffer.flush();
    }

    @Override
    public void markDirty() {
        contentState.markDirty();
    }

    @Override
    public boolean hasActiveAnimations() {
        boolean hoveringRows = rowHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || removeHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
        return contentState.hasActiveAnimations() || hoveringRows || inputField.hasActiveAnimations();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }

        scrollVelocity = 0;

        UiRect listViewport = getListViewport(bounds);
        float maxScroll = state.getMaxFriendScroll();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), listViewport, state.getFriendScroll(), maxScroll)) {
            float newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, maxScroll);
            if (newScroll >= 0.0f) {
                state.setFriendScroll(newScroll);
            }
            markDirty();
            return true;
        }

        UiRect inputBounds = getInputBounds(bounds);
        if (inputField.focusIfContains(inputBounds, event.x(), event.y())) {
            markDirty();
            return true;
        }
        inputField.blur();

        for (FriendRowEntry entry : rowEntries) {
            if (entry.removeBounds().contains(event.x(), event.y())) {
                Managers.FRIEND.removeFriend(entry.name());
                markDirty();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollBarDrag.mouseReleased()) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!scrollBarDrag.isDragging()) {
            return false;
        }
        UiRect listViewport = getListViewport(bounds);
        float newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxFriendScroll());
        if (newScroll >= 0.0f) {
            state.setFriendScroll(newScroll);
        }
        markDirty();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds == null) {
            return false;
        }
        UiRect listViewport = getListViewport(bounds);
        if (listViewport.contains(mouseX, mouseY)) {
            scrollVelocity -= (float) scrollY * 24.0f;
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!inputField.isFocused()) {
            return false;
        }

        return switch (event.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                addFriendFromInput();
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                inputField.blur();
                markDirty();
                yield true;
            }
            default -> {
                if (inputField.keyPressed(event)) {
                    markDirty();
                    yield true;
                }
                yield false;
            }
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputField.charTyped(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void onDeactivated() {
        scrollBarDrag.reset();
        scrollVelocity = 0;
        inputField.blur();
        markDirty();
    }

    private void addFriendFromInput() {
        String name = inputField.getText().trim();
        if (!name.isEmpty() && !Managers.FRIEND.isFriend(name)) {
            Managers.FRIEND.addFriend(name);
        }
        inputField.clear();
        markDirty();
    }

    private void buildFriendRow(UiTree.Scope scope, String name, UiRect bounds, UiRect removeBounds, float hoverProgress, float removeHoverProgress) {
        PanelElements.buildRowSurface(scope, bounds, hoverProgress);

        float avatarSize = 20.0f;
        float avatarX = MD3Theme.ROW_CONTENT_INSET + 2.0f;
        float avatarY = (bounds.height() - avatarSize) / 2.0f;
        scope.roundRect(avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2.0f, MD3Theme.SECONDARY_CONTAINER);
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        float initialScale = 0.54f;
        float initialWidth = textRenderer.getWidth(initial, initialScale);
        float initialHeight = textRenderer.getHeight(initialScale);
        scope.text(initial,
                avatarX + (avatarSize - initialWidth) / 2.0f,
                avatarY + (avatarSize - initialHeight) / 2.0f,
                initialScale,
                MD3Theme.ON_SECONDARY_CONTAINER);

        float nameScale = 0.66f;
        float nameX = avatarX + avatarSize + 8.0f;
        float nameY = (bounds.height() - textRenderer.getHeight(nameScale)) / 2.0f;
        scope.text(name, nameX, nameY, nameScale, MD3Theme.TEXT_PRIMARY);

        PanelElements.buildIconButton(scope, textRenderer, removeBounds.relativeTo(bounds), "✕", 0.50f, MD3Theme.ERROR, removeHoverProgress);
    }

    private UiRect getListViewport(UiRect bounds) {
        return new UiRect(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height() - FRIEND_INPUT_HEIGHT - FRIEND_INPUT_BOTTOM_MARGIN * 2.0f
        );
    }

    private UiRect getInputBounds(UiRect bounds) {
        return new UiRect(
                bounds.x() + 2.0f,
                bounds.bottom() - FRIEND_INPUT_HEIGHT - FRIEND_INPUT_BOTTOM_MARGIN,
                bounds.width() - 4.0f,
                FRIEND_INPUT_HEIGHT
        );
    }

    private UiRect getRemoveButtonBounds(UiRect rowBounds) {
        float buttonSize = 20.0f;
        return new UiRect(
                rowBounds.right() - MD3Theme.ROW_TRAILING_INSET - buttonSize,
                rowBounds.y() + (rowBounds.height() - buttonSize) / 2.0f,
                buttonSize,
                buttonSize
        );
    }

    private boolean shouldRebuild(UiRect listViewport, int mouseX, int mouseY, List<String> friends, int guiHeight, long contentSignature) {
        if (contentState.needsRebuild(listViewport, mouseX, mouseY, guiHeight, contentSignature)) {
            return true;
        }
        if (Float.compare(lastScroll, state.getFriendScroll()) != 0) {
            return true;
        }
        if (!Objects.equals(lastFriendList, friends)) {
            return true;
        }
        return lastContentSignature != contentSignature;
    }

    private void rememberSnapshot(UiRect listViewport, int mouseX, int mouseY, List<String> friends, int guiHeight, long contentSignature) {
        contentState.rememberSnapshot(listViewport, mouseX, mouseY, guiHeight, contentSignature);
        lastScroll = state.getFriendScroll();
        lastFriendList = new ArrayList<>(friends);
        lastContentSignature = contentSignature;
    }

    private long buildContentSignature(List<String> friends) {
        long signature = 17L;
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + Float.floatToIntBits(state.getFriendScroll());
        for (String friend : friends) {
            signature = signature * 31L + friend.hashCode();
        }
        return signature;
    }

    private Animation createAnimation() {
        Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
        animation.setStartValue(0.0f);
        return animation;
    }

    @Override
    public void close() {
        contentBuffer.close();
        markDirty();
    }

    private record FriendRowEntry(String name, UiRect rowBounds, UiRect removeBounds) {
    }

}
