package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;

/**
 * 设置行内的小型标记绘制工具。
 */
final class LabelBadges {

    private static final float CHIP_TEXT_SCALE = 0.54f;
    private static final float CHIP_HEIGHT = 13.0f;
    private static final float CHIP_GAP = 6.0f;
    /**
     * assist chip 渲染器固定使用 8px 左内边距，宽度必须按左右各 8px 计算才能让文字居中。
     */
    private static final float CHIP_PADDING = 8.0f;

    private LabelBadges() {
    }

    /**
     * 在行尾控件左侧绘制 Unsupported 标记，用于标记当前平台不可用的设置。
     *
     * @param scope        行内 UI 作用域（坐标为行内局部坐标）
     * @param textRenderer 文本测量器
     * @param bounds       行区域
     * @param trailing     行尾控件区域（同样为行内局部坐标）
     */
    static void platformOnlyChip(UiTree.Scope scope, TextRenderer textRenderer, UiRect bounds, UiRect trailing) {
        String label = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName();
        float width = textRenderer.getWidth(label, CHIP_TEXT_SCALE) + CHIP_PADDING * 2.0f;
        float x = Math.max(MD3Theme.ROW_CONTENT_INSET, trailing.x() - CHIP_GAP - width);
        float y = trailing.y() + (trailing.height() - CHIP_HEIGHT) * 0.5f;
        scope.chip(new UiRect(x, y, width, CHIP_HEIGHT), label, CHIP_TEXT_SCALE,
                MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255),
                MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null);
    }

}
