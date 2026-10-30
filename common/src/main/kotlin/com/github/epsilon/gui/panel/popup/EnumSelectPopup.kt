package com.github.epsilon.gui.panel.popup

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color

class EnumSelectPopup(bounds: UiRect, private val setting: EnumSetting<*>) : PanelPopupHost.Popup {

    private val bounds: UiRect
    private val scrollable: Boolean
    private val maxScroll: Float
    private var scroll = 0.0f

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val openAnimation = Animation(Easing.EASE_OUT_CUBIC, 140L)
    private var hoveredIndex = -1

    init {
        val optionCount = setting.modes.size
        this.scrollable = optionCount > MAX_VISIBLE_ITEMS
        if (scrollable) {
            val cappedHeight = MAX_VISIBLE_ITEMS * ITEM_HEIGHT + CONTENT_PADDING * 2
            this.bounds = UiRect(bounds.x, bounds.y, bounds.width, cappedHeight)
        } else {
            this.bounds = bounds
        }

        val fullContentHeight = optionCount * ITEM_HEIGHT
        val viewportHeight = this.bounds.height - CONTENT_PADDING * 2
        this.maxScroll = Math.max(0.0f, fullContentHeight - viewportHeight)

        this.openAnimation.startValue = 0.0f
    }

    fun getSetting(): EnumSetting<*> {
        return setting
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        contentBuffer.clear()
        val popupTree = UiTree.build { scope ->
            val progress = scope.animate(openAnimation, 1.0f)
            val popupY = bounds.y - (1.0f - progress) * 6.0f
            val viewportHeight = bounds.height - CONTENT_PADDING * 2
            val fullContentHeight = setting.modes.size * ITEM_HEIGHT
            val itemAreaWidth = bounds.width - CONTENT_PADDING * 2 - (if (scrollable) 6.0f else 0.0f)
            val viewportBounds = UiRect(
                bounds.x + CONTENT_PADDING, popupY + CONTENT_PADDING,
                bounds.width - CONTENT_PADDING * 2, viewportHeight
            )

            val popupBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
            scope.pushAbsolute(popupBounds) { popup ->
                popup.popupCard(
                    popupBounds.atOrigin(),
                    MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.POPUP_SHADOW_BLUR,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
                )

                hoveredIndex = -1
                val localViewportBounds = viewportBounds.relativeTo(popupBounds)
                popup.viewport(contentBuffer, localViewportBounds, scroll, maxScroll, fullContentHeight) { content ->
                    val modes = setting.modes
                    val itemStartY = popupY + CONTENT_PADDING - scroll
                    for (i in modes.indices) {
                        val itemY = itemStartY + i * ITEM_HEIGHT
                        val itemBounds = UiRect(bounds.x + CONTENT_PADDING, itemY, itemAreaWidth, ITEM_INNER_HEIGHT)
                        val visible = itemY + ITEM_INNER_HEIGHT > viewportBounds.y && itemY < viewportBounds.bottom()
                        val hovered = visible && itemBounds.contains(mouseX.toDouble(), mouseY.toDouble()) &&
                                mouseY >= viewportBounds.y && mouseY <= viewportBounds.bottom()
                        if (hovered) {
                            hoveredIndex = i
                        }
                        val selected = i == setting.modeIndex
                        val supported = setting.isModeSupportedUnchecked(modes[i])
                        val baseBackground = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 0)
                        val hoverBackground =
                            MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.SURFACE_CONTAINER_HIGHEST, 0.55f)
                        val selectedBackground = MD3Theme.SECONDARY_CONTAINER
                        val background: Color = if (selected) selectedBackground else (if (hovered) hoverBackground else baseBackground)
                        val textColor: Color = if (!supported) {
                            MD3Theme.TEXT_MUTED
                        } else if (selected) {
                            MD3Theme.ON_SECONDARY_CONTAINER
                        } else if (hovered) {
                            MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 255)
                        } else {
                            MD3Theme.TEXT_SECONDARY
                        }
                        val localItemBounds = UiRect(0.0f, i * ITEM_HEIGHT, itemAreaWidth, ITEM_INNER_HEIGHT)
                        content.roundRect(
                            localItemBounds.x, localItemBounds.y, localItemBounds.width, localItemBounds.height,
                            8.0f, background
                        )
                        val textScale = 0.62f
                        val textHeight = contentBuffer.textMetrics().getHeight(textScale)
                        val textY = localItemBounds.y + (localItemBounds.height - textHeight) / 2.0f
                        if (selected) {
                            val iconScale = 0.72f
                            val iconHeight = contentBuffer.textMetrics().getHeight(iconScale, StaticFontLoader.ICONS)
                            val iconY = localItemBounds.y + (localItemBounds.height - iconHeight) / 2.0f
                            // TODO: 换个更合适的 icon
                            content.text(
                                IconChars.KEYBOARD_ARROW_DOWN, localItemBounds.x + 8.0f, iconY, iconScale,
                                MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS
                            )
                        }
                        content.text(
                            setting.getTranslatedValueByIndex(i),
                            localItemBounds.x + (if (selected) 22.0f else 10.0f), textY, textScale, textColor
                        )
                        if (!supported) {
                            val badge = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName()
                            val badgeScale = 0.52f
                            // assist chip 固定 8px 左内边距，左右各留 8px 才能让文字居中。
                            val badgeWidth = contentBuffer.textMetrics().getWidth(badge, badgeScale) + 16.0f
                            val badgeHeight = 12.0f
                            val badgeX = localItemBounds.right() - badgeWidth - 8.0f
                            val badgeY = localItemBounds.y + (localItemBounds.height - badgeHeight) * 0.5f
                            content.chip(
                                UiRect(badgeX, badgeY, badgeWidth, badgeHeight), badge, badgeScale,
                                MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255),
                                MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null
                            )
                        }
                    }
                }
            }
        }
        renderBatch.render(popupTree)
    }

    override fun flush(renderBatch: UiRenderBatch) {
        contentBuffer.flushAndClear()
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x(), event.y()) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        val modes = setting.modes
        if (hoveredIndex < 0 || hoveredIndex >= modes.size) {
            return false
        }
        val mode = modes[hoveredIndex]
        if (!setting.isModeSupportedUnchecked(mode)) {
            PlatformNoticeScreen.showOption(setting, mode)
            return true
        }
        setModeUnchecked(setting, mode)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!scrollable || maxScroll <= 0.0f) {
            return false
        }
        val nextScroll = scroll - scrollY.toFloat() * 20.0f
        scroll = Math.clamp(nextScroll, 0.0f, maxScroll)
        return true
    }

    override fun shouldCloseAfterClick(): Boolean {
        return true
    }

    override fun close() {
        contentBuffer.close()
    }

    companion object {

        const val MAX_VISIBLE_ITEMS = 5
        private const val ITEM_HEIGHT = 24.0f
        private const val ITEM_INNER_HEIGHT = 22.0f
        private const val CONTENT_PADDING = 6.0f

        /**
         * EnumSetting 的泛型是 `E extends Enum<E>`，星投影下无法直接调用 `setMode`；
         * 通过无界 [Setting] 视图写入等价的值。
         */
        @Suppress("UNCHECKED_CAST")
        private fun setModeUnchecked(setting: EnumSetting<*>, mode: Enum<*>) {
            (setting as Setting<Any>).value = mode
        }

    }

}
