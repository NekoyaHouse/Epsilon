package com.github.epsilon.gui.panel.view;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.lib.state.UiInvalidationState;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.adapter.SettingListController;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ModuleDetailPanel implements AutoCloseable {

    protected final PanelState state;
    private final TextRenderer textRenderer;
    private final SettingListController settingListController;
    private final UiContentBuffer contentBuffer = new UiContentBuffer(EpsilonUiTheme.INSTANCE);
    private final UiInvalidationState contentState = new UiInvalidationState();
    private UiRect bounds;
    private int guiHeight;
    private UiRect headerBounds;
    private final Map<Setting<?>, Animation> hoverAnimations = new HashMap<>();
    private float lastDetailScroll = Float.NaN;
    private String lastModuleKey = "";
    private List<String> lastVisibleSettings = List.of();
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();
    private float scrollVelocity = 0;
    private final Animation bindModeAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180L);
    private final Animation bindModeHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation keybindHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation keybindFocusAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150L);
    private final Animation hiddenAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180L);
    private final Animation hiddenHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private long lastContentSignature = Long.MIN_VALUE;

    public ModuleDetailPanel(PanelState state, TextRenderer textRenderer, PanelPopupHost popupHost) {
        this.state = state;
        this.textRenderer = textRenderer;
        this.settingListController = new SettingListController(popupHost);
        this.bindModeAnimation.setStartValue(0.0f);
        this.bindModeHoverAnimation.setStartValue(0.0f);
        this.keybindHoverAnimation.setStartValue(0.0f);
        this.keybindFocusAnimation.setStartValue(0.0f);
        this.hiddenAnimation.setStartValue(0.0f);
        this.hiddenHoverAnimation.setStartValue(0.0f);
    }

    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;
        this.guiHeight = GuiGraphicsExtractor.guiHeight();

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollDetail(scrollVelocity * partialTick);
            scrollVelocity *= 0.86f;
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0;
            }
            markDirty();
        }

        boolean popupConsumesHover = settingListController.isPopupHovered(mouseX, mouseY);
        int effectiveMouseX = popupConsumesHover ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = popupConsumesHover ? Integer.MIN_VALUE : mouseY;

        Module module = state.getSelectedModule();
        String detailTitle = module == null ? EpsilonTranslations.Gui.NO_MODULE.getTranslatedName() : module.getTranslatedName();
        float titleScale = 0.78f;
        float titleHeight = textRenderer.getHeight(titleScale);
        float titleY = 10.0f + (MD3Theme.CONTROL_HEIGHT - titleHeight) / 2.0f;
        UiTree headerTree = UiTree.build(scope -> scope.pushAbsolute(bounds, panel ->
                panel.text(detailTitle, MD3Theme.PANEL_TITLE_INSET, titleY, titleScale, MD3Theme.TEXT_PRIMARY)));
        renderBatch.render(headerTree);

        if (module == null) {
            return;
        }

        headerBounds = new UiRect(bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, bounds.y() + 34.0f, bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f, 36.0f);
        UiTree controlTree = UiTree.build(scope -> {
            scope.pushAbsolute(headerBounds, header -> header.roundRect(0.0f, 0.0f, headerBounds.width(), headerBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.SURFACE_CONTAINER));
            buildKeybindControl(scope, module, mouseX, mouseY);
            buildBindModeControl(scope, module, mouseX, mouseY);
            buildHiddenControl(scope, module, mouseX, mouseY);
        });
        renderBatch.render(controlTree);

        UiRect viewport = getViewport();
        List<Setting<?>> settings = module.getSettings().stream().filter(Setting::isAvailable).toList();
        String settingOwnerKey = getSettingOwnerKey(module);
        float contentHeight = settingListController.getContentHeight(settingOwnerKey, settings);
        state.setMaxDetailScroll(contentHeight - viewport.height());
        float maxDetailScroll = Math.max(0, contentHeight - viewport.height());
        boolean hasScrollBar = maxDetailScroll > 0;
        float rowWidth = hasScrollBar ? viewport.width() - ScrollBarUtils.TOTAL_WIDTH : viewport.width();
        long contentSignature = buildContentSignature(module, settings, settingOwnerKey);
        boolean rebuildContent = shouldRebuildContent(bounds, mouseX, mouseY, module, settings, GuiGraphicsExtractor.guiHeight(), contentSignature);

        if (rebuildContent) {
            contentBuffer.clear();
            contentState.beginRebuild();
        }

        UiTree contentTree = UiTree.build(scope -> scope.viewport(contentBuffer, viewport,
                state.getDetailScroll(), maxDetailScroll, contentHeight, effectiveMouseX, effectiveMouseY, content -> {
                    if (!rebuildContent) {
                        return;
                    }
                    settingListController.layoutRows(settingOwnerKey, settings, viewport, state.getDetailScroll(), rowWidth,
                            content, textRenderer, effectiveMouseX, effectiveMouseY, (setting, row, rowBounds) -> {
                                if (row instanceof KeybindSettingRow keybindRow) {
                                    keybindRow.setListening(state.getListeningKeybindSetting() == keybindRow.getSetting());
                                }
                                Animation hoverAnimation = hoverAnimations.computeIfAbsent(setting, ignored -> new Animation(Easing.EASE_OUT_CUBIC, 120L));
                                hoverAnimation.run(rowBounds.contains(effectiveMouseX, effectiveMouseY) ? 1.0f : 0.0f);
                                content.pushAbsolute(rowBounds, rowScope ->
                                        row.buildUi(rowScope, GuiGraphicsExtractor, textRenderer, rowBounds,
                                                hoverAnimation.getValue(), effectiveMouseX, effectiveMouseY, partialTick));
                                contentState.noteAnimation(!hoverAnimation.isFinished() || row.hasActiveAnimation());
                            });
                    contentState.noteAnimation(settingListController.hasActiveAnimations());
                }));
        renderBatch.render(contentTree);

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, module, settings, GuiGraphicsExtractor.guiHeight(), contentSignature);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null) {
            return false;
        }
        scrollVelocity = 0;
        Module module = state.getSelectedModule();
        if (module == null || headerBounds == null) {
            return false;
        }

        if (state.getListeningKeyBindModule() == module) {
            UiRect keybindBounds = getKeybindBounds();
            if (keybindBounds.contains(event.x(), event.y())) {
                module.setKeyBind(KeybindUtils.encodeMouseButton(event.button()));
                state.setListeningKeyBindModule(null);
                markDirty();
                return true;
            }
        }

        KeybindSetting listeningKeybindSetting = state.getListeningKeybindSetting();
        if (listeningKeybindSetting != null) {
            listeningKeybindSetting.setValue(KeybindUtils.encodeMouseButton(event.button()));
            state.setListeningKeybindSetting(null);
            markDirty();
            return true;
        }

        if (event.button() != 0) {
            return false;
        }

        // Scrollbar drag
        UiRect viewport = getViewport();
        float maxScroll = state.getMaxDetailScroll();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, state.getDetailScroll(), maxScroll)) {
            float newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, maxScroll);
            if (newScroll >= 0) {
                state.setDetailScroll(newScroll);
            }
            markDirty();
            return true;
        }

        UiRect keybindBounds = getKeybindBounds();
        if (keybindBounds.contains(event.x(), event.y())) {
            state.setListeningKeyBindModule(module);
            markDirty();
            return true;
        } else if (state.getListeningKeyBindModule() == module) {
            state.setListeningKeyBindModule(null);
            markDirty();
        }

        UiRect bindModeBounds = getBindModeBounds();
        if (bindModeBounds.contains(event.x(), event.y())) {
            float midpoint = bindModeBounds.centerX();
            module.setBindMode(event.x() < midpoint ? Module.BindMode.Toggle : Module.BindMode.Hold);
            markDirty();
            return true;
        }

        UiRect hiddenBounds = getHiddenBounds();
        if (hiddenBounds.contains(event.x(), event.y())) {
            float midpoint = hiddenBounds.centerX();
            module.setHidden(event.x() >= midpoint);
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

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (scrollBarDrag.isDragging()) {
            UiRect viewport = getViewport();
            float newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, state.getMaxDetailScroll());
            if (newScroll >= 0) {
                state.setDetailScroll(newScroll);
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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect viewport = getViewport();
        if (bounds != null && viewport.contains(mouseX, mouseY)) {
            scrollVelocity -= (float) scrollY * 24f;
            markDirty();
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        KeybindSetting listeningSetting = state.getListeningKeybindSetting();
        if (listeningSetting != null) {
            if (event.key() == 256) {
                state.setListeningKeybindSetting(null);
                markDirty();
                return true;
            }
            if (event.key() == 259 || event.key() == 261) {
                listeningSetting.setValue(-1);
                state.setListeningKeybindSetting(null);
                markDirty();
                return true;
            }
            listeningSetting.setValue(event.key());
            state.setListeningKeybindSetting(null);
            markDirty();
            return true;
        }
        Module module = state.getSelectedModule();
        if (module != null && state.getListeningKeyBindModule() == module) {
            if (event.key() == 256) {
                state.setListeningKeyBindModule(null);
                markDirty();
                return true;
            }
            if (event.key() == 259 || event.key() == 261) {
                module.setKeyBind(-1);
                state.setListeningKeyBindModule(null);
                markDirty();
                return true;
            }
            module.setKeyBind(event.key());
            state.setListeningKeyBindModule(null);
            markDirty();
            return true;
        }
        if (settingListController.keyPressed(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (settingListController.charTyped(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    private UiRect getViewport() {
        if (bounds == null) {
            return new UiRect(0, 0, 0, 0);
        }
        if (headerBounds == null) {
            return new UiRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
        return new UiRect(bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, headerBounds.bottom() + 6.0f, bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f, bounds.bottom() - headerBounds.bottom() - 10.0f);
    }

    private UiRect getBindModeBounds() {
        return new UiRect(getHeaderControlGroupLeftX() + getKeybindControlSize() + getHeaderControlGap(), getHeaderControlsY(), getBindModeControlWidth(), getHeaderControlHeight());
    }

    private UiRect getKeybindBounds() {
        return new UiRect(getHeaderControlGroupLeftX(), getHeaderControlsY(), getKeybindControlSize(), getKeybindControlSize());
    }

    private float getHeaderControlGroupLeftX() {
        return headerBounds.x() + getHeaderContentInset();
    }

    private float getHeaderControlGroupRightX() {
        return headerBounds.right() - getHeaderContentInset() - getHeaderControlGroupWidth();
    }

    private float getHeaderControlGroupWidth() {
        return getKeybindControlSize() + getHeaderControlGap() + getBindModeControlWidth() + getHeaderControlGap() + getHiddenControlWidth();
    }

    private float getHeaderContentInset() {
        return MD3Theme.PANEL_TITLE_INSET + 2.0f;
    }

    private float getHeaderContentInsetX() {
        return headerBounds.x() + getHeaderContentInset();
    }

    private float getHeaderControlHeight() {
        return MD3Theme.CONTROL_HEIGHT;
    }

    private float getKeybindControlSize() {
        return getHeaderControlHeight();
    }

    private float getBindModeControlWidth() {
        return 72.0f;
    }

    private float getHiddenControlWidth() {
        return 72.0f;
    }

    private UiRect getHiddenBounds() {
        return new UiRect(getHeaderControlGroupRightX() + getKeybindControlSize() + getHeaderControlGap() + getBindModeControlWidth() + getHeaderControlGap(), getHeaderControlsY(), getHiddenControlWidth(), getHeaderControlHeight());
    }

    private float getHeaderControlGap() {
        return 6.0f;
    }

    private float getHeaderControlRadius() {
        return MD3Theme.CONTROL_RADIUS;
    }

    private float getKeybindControlRadius() {
        return 8.0f;
    }

    private float getHeaderControlsY() {
        return headerBounds.y() + (headerBounds.height() - getHeaderControlHeight()) / 2.0f;
    }

    private void buildBindModeControl(UiTree.Scope scope, Module module, int mouseX, int mouseY) {
        UiRect bindModeBounds = getBindModeBounds();
        float bindProgress = scope.animate(bindModeAnimation, module.getBindMode() == Module.BindMode.Hold);
        float hoverProgress = scope.animate(bindModeHoverAnimation, bindModeBounds.contains(mouseX, mouseY));
        PanelElements.buildSegmentedControl(scope, textRenderer, bindModeBounds,
                EpsilonTranslations.Keybind.TOGGLE.getTranslatedName(), EpsilonTranslations.Keybind.HOLD.getTranslatedName(),
                bindProgress, hoverProgress);
    }

    private void buildKeybindControl(UiTree.Scope scope, Module module, int mouseX, int mouseY) {
        UiRect keybindBounds = getKeybindBounds();
        boolean listening = state.getListeningKeyBindModule() == module;
        float hoverProgress = scope.animate(keybindHoverAnimation, keybindBounds.contains(mouseX, mouseY));
        float focusProgress = scope.animate(keybindFocusAnimation, listening);
        float radius = getKeybindControlRadius();
        scope.pushAbsolute(keybindBounds, keybind -> {
            float haloInset = 1.5f * focusProgress;
            if (haloInset > 0.01f) {
                keybind.roundRect(-haloInset, -haloInset, keybindBounds.width() + haloInset * 2.0f, keybindBounds.height() + haloInset * 2.0f,
                        radius + haloInset, MD3Theme.withAlpha(MD3Theme.PRIMARY, (int) (28 * focusProgress)));
            }

            Color background = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, focusProgress);
            Color foreground = MD3Theme.lerp(MD3Theme.ON_SECONDARY_CONTAINER, MD3Theme.ON_PRIMARY_CONTAINER, focusProgress);
            keybind.roundRect(0.0f, 0.0f, keybindBounds.width(), keybindBounds.height(), radius, background);
            if (hoverProgress > 0.01f) {
                keybind.roundRect(0.0f, 0.0f, keybindBounds.width(), keybindBounds.height(), radius,
                        MD3Theme.stateLayer(foreground, hoverProgress, listening ? 18 : 12));
            }

            String label = listening ? "..." : formatCompactKeybind(module.getKeyBind());
            float scale = label.length() >= 3 ? 0.42f : 0.5f;
            float textWidth = textRenderer.getWidth(label, scale);
            float textHeight = textRenderer.getHeight(scale);
            float textX = (keybindBounds.width() - textWidth) / 2.0f;
            float textY = (keybindBounds.height() - textHeight) / 2.0f;
            keybind.text(label, textX, textY, scale, foreground);
        });
    }

    private void buildHiddenControl(UiTree.Scope scope, Module module, int mouseX, int mouseY) {
        UiRect hiddenBounds = getHiddenBounds();
        float hiddenProgress = scope.animate(hiddenAnimation, module.isHidden());
        float hoverProgress = scope.animate(hiddenHoverAnimation, hiddenBounds.contains(mouseX, mouseY));
        PanelElements.buildSegmentedControl(scope, textRenderer, hiddenBounds,
                EpsilonTranslations.Module.VISIBLE.getTranslatedName(), EpsilonTranslations.Module.HIDDEN.getTranslatedName(),
                hiddenProgress, hoverProgress);
    }

    private String formatCompactKeybind(int keyCode) {
        if (keyCode == KeybindUtils.NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName();
        }
        if (KeybindUtils.isMouseButton(keyCode)) {
            return "M" + (KeybindUtils.decodeMouseButton(keyCode) + 1);
        }
        String label = formatKeybind(keyCode).trim();
        if (label.isEmpty()) {
            return "?";
        }

        String[] parts = label.split("[^A-Za-z0-9]+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 3) {
                break;
            }
        }
        if (initials.length() >= 2) {
            return initials.toString();
        }

        String compact = label.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!compact.isEmpty()) {
            return compact.length() > 3 ? compact.substring(0, 3) : compact;
        }
        // Symbol-only keys (e.g. ";", "[", "/"): fall back to the raw label so we never show "?".
        return label.length() > 3 ? label.substring(0, 3) : label;
    }

    private String formatKeybind(int keyCode) {
        return KeybindUtils.format(keyCode);
    }

    public void flushContent() {
        contentBuffer.flush();
    }

    public void markDirty() {
        contentState.markDirty();
    }

    public boolean hasActiveAnimations() {
        return contentState.hasActiveAnimations()
                || settingListController.hasActiveAnimations()
                || !keybindHoverAnimation.isFinished()
                || !keybindFocusAnimation.isFinished()
                || !bindModeAnimation.isFinished()
                || !bindModeHoverAnimation.isFinished()
                || !hiddenAnimation.isFinished()
                || !hiddenHoverAnimation.isFinished();
    }

    public void resetTransientState() {
        scrollBarDrag.reset();
        scrollVelocity = 0;
        settingListController.resetTransientState();
        if (state.getListeningKeyBindModule() != null) {
            state.setListeningKeyBindModule(null);
        }
        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
        }
        markDirty();
    }

    private boolean shouldRebuildContent(UiRect bounds, int mouseX, int mouseY, Module module, List<Setting<?>> settings, int currentGuiHeight, long contentSignature) {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true;
        }
        if (Float.compare(lastDetailScroll, state.getDetailScroll()) != 0) {
            return true;
        }
        if (!Objects.equals(lastModuleKey, module.getName() + ":" + module.getBindMode() + ":" + module.getKeyBind() + ":" + module.isHidden())) {
            return true;
        }
        List<String> visibleSettings = settings.stream().map(Setting::getName).toList();
        if (!Objects.equals(lastVisibleSettings, visibleSettings)) {
            return true;
        }
        return lastContentSignature != contentSignature;
    }

    private void rememberSnapshot(UiRect bounds, int mouseX, int mouseY, Module module, List<Setting<?>> settings, int currentGuiHeight, long contentSignature) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, currentGuiHeight, contentSignature);
        lastDetailScroll = state.getDetailScroll();
        lastModuleKey = module.getName() + ":" + module.getBindMode() + ":" + module.getKeyBind() + ":" + module.isHidden();
        lastVisibleSettings = settings.stream().map(Setting::getName).toList();
        lastContentSignature = contentSignature;
    }

    private String getSettingOwnerKey(Module module) {
        return "panel-module:" + module.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private long buildContentSignature(Module module, List<Setting<?>> settings, String settingOwnerKey) {
        long signature = 17L;
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + module.getName().hashCode();
        signature = signature * 31L + module.getBindMode().ordinal();
        signature = signature * 31L + module.getKeyBind();
        signature = signature * 31L + (module.isHidden() ? 1 : 0);
        signature = signature * 31L + Float.floatToIntBits(state.getDetailScroll());
        KeybindSetting listening = state.getListeningKeybindSetting();
        signature = signature * 31L + (listening == null ? 0 : listening.getName().hashCode());
        for (Setting<?> setting : settings) {
            signature = signature * 31L + setting.getName().hashCode();
            signature = signature * 31L + (setting.isAvailable() ? 1 : 0);
        }
        signature = signature * 31L + SettingLayoutPlanner.signature(settingOwnerKey, settings);
        return signature;
    }

    @Override
    public void close() {
        settingListController.close();
        contentBuffer.close();
        markDirty();
    }

}
