package com.github.epsilon.gui.lib

import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.state.luminBlink
import java.awt.Color

/*
 * UiScope 语义控件扩展。
 *
 * 与图元一样只做转发；`bounds` 一律使用相对 [UiScope.origin] 的局部坐标。
 */

fun UiScope.Button(
    x: Float, y: Float, width: Float, height: Float, radius: Float, background: Color,
    label: String, labelScale: Float, labelColor: Color,
) {
    tree.button(x, y, width, height, radius, background, label, labelScale, labelColor)
}

fun UiScope.Button(
    bounds: UiRect, radius: Float, background: Color, label: String, labelScale: Float, labelColor: Color,
) {
    tree.button(bounds, radius, background, label, labelScale, labelColor)
}

fun UiScope.Toggle(bounds: UiRect, toggleProgress: Float, hoverProgress: Float) {
    tree.toggle(bounds, toggleProgress, hoverProgress)
}

fun UiScope.FilledField(bounds: UiRect, focused: Boolean, hoverProgress: Float) {
    tree.filledField(bounds, focused, hoverProgress)
}

fun UiScope.Input(
    bounds: UiRect, focused: Boolean, hoverProgress: Float,
    focusRingProgress: Float, focusRingColor: Color, focusRingInset: Float,
    textInset: Float, text: String?, textScale: Float, textColor: Color,
    selection: UiTree.SelectionRange?, selectionColor: Color?,
    caretIndex: Int?, caretColor: Color?,
    trailingHint: String?, trailingHintScale: Float, trailingHintColor: Color?,
) {
    tree.input(
        bounds, focused, hoverProgress,
        focusRingProgress, focusRingColor, focusRingInset,
        textInset, text, textScale, textColor,
        selection, selectionColor,
        if (caretIndex != null && luminBlink("input-caret")) caretIndex else null, caretColor,
        trailingHint, trailingHintScale, trailingHintColor
    )
}

fun UiScope.Chip(
    bounds: UiRect, label: String, textScale: Float, background: Color, foreground: Color,
    trailingIcon: String?, trailingIconScale: Float, trailingIconFont: TtfFontLoader?,
) {
    tree.chip(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont)
}

fun UiScope.Segmented(
    bounds: UiRect, leadingLabel: String, trailingLabel: String, progress: Float, hoverProgress: Float,
) {
    tree.segmented(bounds, leadingLabel, trailingLabel, progress, hoverProgress)
}

fun UiScope.IconButton(bounds: UiRect, label: String, scale: Float, tone: Color, hoverProgress: Float) {
    tree.iconButton(bounds, label, scale, tone, hoverProgress)
}

fun UiScope.PopupCard(bounds: UiRect, radius: Float, blurRadius: Float, shadowColor: Color, surfaceColor: Color) {
    tree.popupCard(bounds, radius, blurRadius, shadowColor, surfaceColor)
}

fun UiScope.Slider(
    bounds: UiRect, progress: Float, trackRadius: Float,
    trackColor: Color, activeEndInset: Float, activeMinWidth: Float, activeColor: Color,
    handleWidth: Float, handleHeight: Float, handleRadius: Float, handleColor: Color,
) {
    tree.slider(
        bounds, progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor,
        handleWidth, handleHeight, handleRadius, handleColor
    )
}

/**
 * 可裁剪滚动视口。
 *
 * 内容在宿主批次的插入位置展开，继承属性和裁剪；旧 scroll 参数仍在构建时平移坐标。
 * 需要避免滚动重建时，使用延迟 Transform 表达位移，并保持 scroll 参数为零。
 */
fun UiScope.Viewport(
    buffer: UiContentBuffer, bounds: UiRect, scroll: Float, maxScroll: Float, contentHeight: Float,
    mouseX: Int = Int.MIN_VALUE, mouseY: Int = Int.MIN_VALUE, block: UiScope.() -> Unit,
) {
    val target = absolute(bounds.x, bounds.y, bounds.width, bounds.height)
    val contentOrigin = UiRect(target.x, target.y - scroll, bounds.width, contentHeight)
    tree.viewport(buffer, bounds, scroll, maxScroll, contentHeight, mouseX, mouseY) {
        block(derive(contentOrigin))
    }
}
