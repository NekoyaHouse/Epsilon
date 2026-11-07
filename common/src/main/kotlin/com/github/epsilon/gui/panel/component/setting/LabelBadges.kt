package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.Chip
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.theme.MD3Theme

private const val CHIP_TEXT_SCALE = 0.54f
private const val CHIP_HEIGHT = 13.0f
private const val CHIP_GAP = 6.0f

/**
 * assist chip 渲染器固定使用 8px 左内边距，宽度必须按左右各 8px 计算才能让文字居中。
 */
private const val CHIP_PADDING = 8.0f

/**
 * 在行尾控件左侧绘制 Unsupported 标记，用于标记当前平台不可用的设置。
 *
 * @param trailing 行尾控件区域（行局部坐标）
 * @param label    标记文本
 */
fun UiScope.PlatformOnlyChip(
    trailing: UiRect,
    label: String = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName(),
) {
    val width = metrics.textWidth(label, CHIP_TEXT_SCALE) + CHIP_PADDING * 2.0f
    val x = maxOf(MD3Theme.ROW_CONTENT_INSET, trailing.x - CHIP_GAP - width)
    val y = trailing.y + (trailing.height - CHIP_HEIGHT) * 0.5f
    Chip(
        UiRect(x, y, width, CHIP_HEIGHT), label, CHIP_TEXT_SCALE,
        MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255),
        MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null
    )
}
