package com.github.epsilon.gui.panel.view.settings;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.lib.state.UiInvalidationState;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.adapter.SettingListController;
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GeneralClientSettingTab implements ClientSettingTabView {

    private static final String SETTING_OWNER_KEY = "client-settings:panel-general";

    private final PanelState state;
    private final TextRenderer textRenderer;
    private final SettingListController settingListController;
    private final UiContentBuffer contentBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final UiInvalidationState contentState = new UiInvalidationState();
    private final Map<Setting<?>, Animation> hoverAnimations = new HashMap<>();
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();

    private UiRect bounds;
    private float lastScroll = Float.NaN;
    private List<String> lastVisibleSettings = List.of();
    private String lastListeningKey = "";
    private long lastContentSignature = Long.MIN_VALUE;
    private float scrollVelocity = 0;

    public GeneralClientSettingTab(PanelState state, TextRenderer textRenderer, PanelPopupHost popupHost) {
        this.state = state;
        this.textRenderer = textRenderer;
        this.settingListController = new SettingListController(popupHost);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollClientSetting(scrollVelocity * partialTick);
            scrollVelocity *= 0.86f;
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0;
            }
            markDirty();
        }

        List<Setting<?>> settings = ClientSetting.INSTANCE.getSettings().stream()
                .filter(Setting::isAvailable)
                .toList();
        float contentHeight = settingListController.getContentHeight(SETTING_OWNER_KEY, settings);
        state.setMaxClientSettingScroll(contentHeight - bounds.height());
        float maxScroll = Math.max(0.0f, contentHeight - bounds.height());
        boolean hasScrollBar = maxScroll > 0.0f;
        float rowWidth = hasScrollBar ? bounds.width() - ScrollBarUtils.TOTAL_WIDTH : bounds.width();
        long contentSignature = buildContentSignature(settings);
        boolean popupConsumesHover = settingListController.isPopupHovered(mouseX, mouseY);
        int effectiveMouseX = popupConsumesHover ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = popupConsumesHover ? Integer.MIN_VALUE : mouseY;
        boolean rebuildContent = shouldRebuildContent(bounds, mouseX, mouseY, settings, guiGraphics.guiHeight(), contentSignature);

        if (rebuildContent) {
            contentBuffer.clear();
            contentState.beginRebuild();
        }

        UiTree tree = UiTree.build(scope -> scope.viewport(contentBuffer, bounds,
                state.getClientSettingScroll(), maxScroll, contentHeight, effectiveMouseX, effectiveMouseY, content -> {
                    if (!rebuildContent) {
                        return;
                    }
                    settingListController.layoutRows(SETTING_OWNER_KEY, settings, bounds, state.getClientSettingScroll(), rowWidth,
                            content, textRenderer, effectiveMouseX, effectiveMouseY, (setting, row, rowBounds) -> {
                                if (row instanceof KeybindSettingRow keybindRow) {
                                    keybindRow.setListening(state.getListeningKeybindSetting() == keybindRow.getSetting());
                                }
                                Animation hoverAnimation = hoverAnimations.computeIfAbsent(setting, ignored -> {
                                    Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
                                    animation.setStartValue(0.0f);
                                    return animation;
                                });
                                hoverAnimation.run(rowBounds.contains(effectiveMouseX, effectiveMouseY) ? 1.0f : 0.0f);
                                content.pushAbsolute(rowBounds, rowScope ->
                                        row.buildUi(rowScope, guiGraphics, textRenderer, rowBounds,
                                                hoverAnimation.getValue(), effectiveMouseX, effectiveMouseY, partialTick));
                                contentState.noteAnimation(!hoverAnimation.isFinished() || row.hasActiveAnimation());
                            });
                    contentState.noteAnimation(settingListController.hasActiveAnimations());
                }));
        renderBatch.render(tree);

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, settings, guiGraphics.guiHeight(), contentSignature);
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
        return contentState.hasActiveAnimations() || settingListController.hasActiveAnimations();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }

        scrollVelocity = 0;

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
            markDirty();
        }

        float maxScroll = state.getMaxClientSettingScroll();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), bounds, state.getClientSettingScroll(), maxScroll)) {
            float newScroll = scrollBarDrag.mouseDragged(event.y(), bounds, maxScroll);
            if (newScroll >= 0.0f) {
                state.setClientSettingScroll(newScroll);
            }
            markDirty();
            return true;
        }

        if (settingListController.mouseClicked(event, isDoubleClick, bounds, (row, rowBounds, clickEvent, doubleClick) -> {
            if (row instanceof KeybindSettingRow keybindRow && row.mouseClicked(rowBounds, clickEvent, doubleClick)) {
                state.setListeningKeybindSetting(keybindRow.getSetting());
                return true;
            }
            return false;
        })) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollBarDrag.mouseReleased()) {
            markDirty();
            return true;
        }
        if (settingListController.mouseReleased(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (scrollBarDrag.isDragging()) {
            float newScroll = scrollBarDrag.mouseDragged(event.y(), bounds, state.getMaxClientSettingScroll());
            if (newScroll >= 0.0f) {
                state.setClientSettingScroll(newScroll);
            }
            markDirty();
            return true;
        }
        if (settingListController.mouseDragged(event, mouseX, mouseY)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds != null && bounds.contains(mouseX, mouseY)) {
            scrollVelocity -= (float) scrollY * 24.0f;
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeybindSetting listening = state.getListeningKeybindSetting();
        if (listening != null) {
            if (event.key() == 256) {
                state.setListeningKeybindSetting(null);
                markDirty();
                return true;
            }
            if (event.key() == 259 || event.key() == 261) {
                listening.setValue(-1);
                state.setListeningKeybindSetting(null);
                markDirty();
                return true;
            }
            listening.setValue(event.key());
            state.setListeningKeybindSetting(null);
            markDirty();
            return true;
        }
        if (settingListController.keyPressed(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (settingListController.charTyped(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean consumesHover(int mouseX, int mouseY) {
        return settingListController.isPopupHovered(mouseX, mouseY);
    }

    @Override
    public void onDeactivated() {
        scrollBarDrag.reset();
        scrollVelocity = 0;
        settingListController.clearFocus();
        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
        }
        markDirty();
    }

    private boolean shouldRebuildContent(UiRect bounds, int mouseX, int mouseY, List<Setting<?>> settings, int currentGuiHeight, long contentSignature) {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true;
        }
        if (Float.compare(lastScroll, state.getClientSettingScroll()) != 0) {
            return true;
        }
        String listeningKey = state.getListeningKeybindSetting() == null ? "" : state.getListeningKeybindSetting().getName();
        if (!Objects.equals(lastListeningKey, listeningKey)) {
            return true;
        }
        List<String> visibleSettings = settings.stream().map(Setting::getName).toList();
        if (!Objects.equals(lastVisibleSettings, visibleSettings)) {
            return true;
        }
        return lastContentSignature != contentSignature;
    }

    private void rememberSnapshot(UiRect bounds, int mouseX, int mouseY, List<Setting<?>> settings, int currentGuiHeight, long contentSignature) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, currentGuiHeight, contentSignature);
        lastScroll = state.getClientSettingScroll();
        lastListeningKey = state.getListeningKeybindSetting() == null ? "" : state.getListeningKeybindSetting().getName();
        lastVisibleSettings = settings.stream().map(Setting::getName).toList();
        lastContentSignature = contentSignature;
    }

    private long buildContentSignature(List<Setting<?>> settings) {
        long signature = 17L;
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + Float.floatToIntBits(state.getClientSettingScroll());
        signature = signature * 31L + (state.getListeningKeybindSetting() == null ? 0 : state.getListeningKeybindSetting().getName().hashCode());
        for (Setting<?> setting : settings) {
            signature = signature * 31L + setting.getName().hashCode();
            signature = signature * 31L + (setting.isAvailable() ? 1 : 0);
        }
        signature = signature * 31L + SettingLayoutPlanner.signature(SETTING_OWNER_KEY, settings);
        return signature;
    }

    @Override
    public void close() {
        settingListController.close();
        contentBuffer.close();
        markDirty();
    }

}
