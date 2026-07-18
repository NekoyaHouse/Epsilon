package com.github.epsilon.gui.panel.component;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.adapter.ModuleViewModel;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.client.KeybindUtils;

import java.awt.*;

/**
 * 模块列表中的单行展示组件。
 * <p>
 * 一行通常包含模块名称、来源 addon、副标题、快捷键提示以及启用开关。
 * 该类会把自身内容写入外部 DSL 作用域。
 */
public class ModuleRow {

    public static final float HEIGHT = 34.0f;

    public static final float KEYBIND_CLIP_WIDTH = 40.0f;

    public static final float KEYBIND_TOGGLE_GAP = 8.0f;

    private final ModuleViewModel module;
    private final UiRect bounds;
    private final UiRect toggleBounds;

    /**
     * 创建一个模块行实例。
     *
     * @param module 模块视图模型
     * @param bounds 行布局区域
     */
    public ModuleRow(ModuleViewModel module, UiRect bounds) {
        this.module = module;
        this.bounds = bounds;
        this.toggleBounds = PanelElements.switchBounds(bounds);
    }

    /**
     * 返回该行对应的模块视图模型。
     */
    public ModuleViewModel getModule() {
        return module;
    }

    /**
     * 返回整行的命中区域。
     */
    public UiRect getBounds() {
        return bounds;
    }

    /**
     * 返回行内开关控件的命中区域。
     */
    public UiRect getToggleBounds() {
        return toggleBounds;
    }

    /**
     * 将模块行内容写入外部 UI DSL 作用域。
     *
     * @param scope               目标 DSL 作用域
     * @param textRenderer        用于测量文本尺寸的 renderer
     * @param hoverProgress       行悬停进度
     * @param selectedProgress    行选中进度
     * @param toggleProgress      开关进度
     * @param toggleHoverProgress 开关悬停进度
     */
    public void buildUi(UiTree.Scope scope, TextRenderer textRenderer, float hoverProgress, float selectedProgress, float toggleProgress, float toggleHoverProgress) {
        float titleScale = 0.70f;
        float subScale = 0.60f;
        float keyScale = 0.6f;
        float titleHeight = textRenderer.getHeight(titleScale);
        float subHeight = textRenderer.getHeight(subScale);
        float keyHeight = textRenderer.getHeight(keyScale);
        float lineGap = 2.0f;
        float totalTextHeight = titleHeight + lineGap + subHeight;
        float titleY = (bounds.height() - totalTextHeight) / 2.0f;
        float subY = titleY + titleHeight + lineGap;
        float keyY = (bounds.height() - keyHeight) / 2.0f;
        Color titleColor = MD3Theme.lerp(MD3Theme.TEXT_PRIMARY, MD3Theme.ON_PRIMARY_CONTAINER, selectedProgress);
        Color subColor = MD3Theme.lerp(MD3Theme.TEXT_SECONDARY, MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 180), selectedProgress);
        Color keyColor = MD3Theme.isLightTheme() ? MD3Theme.TEXT_SECONDARY : MD3Theme.TEXT_MUTED;
        String keybindText = formatKeybind(module.module().getKeyBind());
        float keyWidth = textRenderer.getWidth(keybindText, keyScale);
        UiRect localToggleBounds = toggleBounds.relativeTo(bounds);
        float clipRight = localToggleBounds.x() - KEYBIND_TOGGLE_GAP;
        float clipWidth = Math.min(keyWidth, KEYBIND_CLIP_WIDTH);
        float clipX = clipRight - clipWidth;
        UiRect keybindClip = new UiRect(clipX, keyY, clipWidth, keyHeight);

        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        if (selectedProgress > 0.01f) {
            scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.stateLayer(MD3Theme.PRIMARY, selectedProgress, 42));
        }

        scope.text(module.displayName(), PanelElements.ROW_LABEL_INSET, titleY, titleScale, titleColor);
        scope.text(module.module().getAddonId() != null ? module.module().getAddonId() : "unknown", PanelElements.ROW_LABEL_INSET, subY, subScale, subColor);
        scope.toggle(localToggleBounds, toggleProgress, toggleHoverProgress);

        if (keyWidth <= KEYBIND_CLIP_WIDTH + 0.5f) {
            scope.text(keybindText, clipRight - keyWidth, keyY, keyScale, keyColor);
        } else {
            float overflow = keyWidth - clipWidth;
            float scrollOffset = -overflow * marqueePhase();
            scope.marqueeText(keybindText, clipX + scrollOffset, keyY, keyScale, keyColor, keybindClip);
        }
    }

    public boolean hasOverflowingKeybind(TextRenderer textRenderer) {
        float keyScale = 0.6f;
        float keyWidth = textRenderer.getWidth(formatKeybind(module.module().getKeyBind()), keyScale);
        return keyWidth > KEYBIND_CLIP_WIDTH + 0.5f;
    }

    private static float marqueePhase() {
        long period = 5000L;
        long pause = 800L;
        long t = System.currentTimeMillis() % period;
        long travel = (period - pause * 2L) / 2L;
        if (t < pause) {
            return 0.0f;
        }
        if (t < pause + travel) {
            return (t - pause) / (float) travel;
        }
        if (t < pause + travel + pause) {
            return 1.0f;
        }
        return 1.0f - (t - pause - travel - pause) / (float) travel;
    }

    private String formatKeybind(int keyCode) {
        return KeybindUtils.format(keyCode).toUpperCase();
    }

}
