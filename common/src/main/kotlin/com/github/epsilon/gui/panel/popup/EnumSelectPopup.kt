package com.github.epsilon.gui.panel.popup

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.render.animation.Easing
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color

class EnumSelectPopup(bounds: UiRect, private val setting: EnumSetting<*>) : PanelPopupHost.Popup {

    private val bounds: UiRect
    private val scrollable: Boolean
    private val maxScroll: Float
    private var scroll by mutableStateOf(0.0f)

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val openAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 140L)

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
        this.maxScroll = maxOf(0.0f, fullContentHeight - viewportHeight)
    }

    fun getSetting(): EnumSetting<*> {
        return setting
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override val inputView = LuminView()

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        inputView.render(mouseX, mouseY) { renderContent(guiGraphics, renderBatch, mouseX, mouseY, partialTick) }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        contentBuffer.clear()
        // 开启动画是推式动画：目标在命令阶段写入，绘制块内只读取 value。
        val popupY = bounds.y - (1.0f - openAnimation.value) * 6.0f
        openAnimation.target = 1.0f

        val viewportHeight = bounds.height - CONTENT_PADDING * 2
        val fullContentHeight = setting.modes.size * ITEM_HEIGHT
        val itemAreaWidth = bounds.width - CONTENT_PADDING * 2 - (if (scrollable) 6.0f else 0.0f)
        val viewportBounds = UiRect(
            bounds.x + CONTENT_PADDING, popupY + CONTENT_PADDING,
            bounds.width - CONTENT_PADDING * 2, viewportHeight
        )
        val popupBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)

        renderBatch.LuminUIScene(popupBounds, key = this) {
            InputRegion(
                key = this@EnumSelectPopup to "input", onScroll = ::mouseScrolled,
                onBlur = {}, onCancel = {})

            val progress = openAnimation.value
            PopupCard(
                size,
                MD3Theme.CARD_RADIUS.toFloat(),
                MD3Theme.POPUP_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
            )

            val localViewportBounds = viewportBounds.relativeTo(popupBounds)
            Viewport(contentBuffer, localViewportBounds, scroll, maxScroll, fullContentHeight) {
                val modes = setting.modes
                val itemStartY = popupY + CONTENT_PADDING - scroll
                for (i in modes.indices) {
                    val itemY = itemStartY + i * ITEM_HEIGHT
                    val itemBounds = UiRect(bounds.x + CONTENT_PADDING, itemY, itemAreaWidth, ITEM_INNER_HEIGHT)
                    val visible = itemY + ITEM_INNER_HEIGHT > viewportBounds.y && itemY < viewportBounds.bottom()
                    val hovered = visible && itemBounds.contains(mouseX.toDouble(), mouseY.toDouble()) &&
                            mouseY >= viewportBounds.y && mouseY <= viewportBounds.bottom()
                    val selected = i == setting.modeIndex
                    val supported = setting.isModeSupportedUnchecked(modes[i])
                    // 逐项缓存：行节点按位置与状态复用，只有悬停/选中/滚动变化的行重新捕获。

                    LuminUIItem(modes[i]) {
                        val localItemBounds = UiRect(0.0f, i * ITEM_HEIGHT, itemAreaWidth, ITEM_INNER_HEIGHT)
                        Interaction(modes[i], localItemBounds) {
                            onClick {
                                val mode = modes[i]
                                if (!setting.isModeSupportedUnchecked(mode)) PlatformNoticeScreen.showOption(
                                    setting,
                                    mode
                                )
                                else setModeUnchecked(setting, mode)
                                true
                            }
                        }
                        val baseBackground = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 0)
                        val hoverBackground = MD3Theme.lerp(
                            MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.SURFACE_CONTAINER_HIGHEST, 0.55f
                        )
                        val selectedBackground = MD3Theme.SECONDARY_CONTAINER
                        val background: Color =
                            if (selected) selectedBackground else (if (hovered) hoverBackground else baseBackground)
                        val textColor: Color = if (!supported) {
                            MD3Theme.TEXT_MUTED
                        } else if (selected) {
                            MD3Theme.ON_SECONDARY_CONTAINER
                        } else if (hovered) {
                            MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 255)
                        } else {
                            MD3Theme.TEXT_SECONDARY
                        }
                        RoundRect(
                            localItemBounds.x, localItemBounds.y, localItemBounds.width, localItemBounds.height,
                            8.0f, background
                        )
                        val textScale = 0.62f
                        val textHeight = metrics.textHeight(textScale)
                        val textY = localItemBounds.y + (localItemBounds.height - textHeight) / 2.0f
                        if (selected) {
                            val iconScale = 0.72f
                            val iconHeight = metrics.textHeight(iconScale, StaticFontLoader.ICONS)
                            val iconY = localItemBounds.y + (localItemBounds.height - iconHeight) / 2.0f
                            // TODO: 换个更合适的 icon
                            Text(
                                IconChars.KEYBOARD_ARROW_DOWN, localItemBounds.x + 8.0f, iconY, iconScale,
                                MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS
                            )
                        }
                        Text(
                            setting.getTranslatedValueByIndex(i),
                            localItemBounds.x + (if (selected) 22.0f else 10.0f), textY, textScale, textColor
                        )
                        if (!supported) {
                            val badge = EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName()
                            val badgeScale = 0.52f
                            // assist chip 固定 8px 左内边距，左右各留 8px 才能让文字居中。
                            val badgeWidth = metrics.textWidth(badge, badgeScale) + 16.0f
                            val badgeHeight = 12.0f
                            val badgeX = localItemBounds.right() - badgeWidth - 8.0f
                            val badgeY = localItemBounds.y + (localItemBounds.height - badgeHeight) * 0.5f
                            Chip(
                                UiRect(badgeX, badgeY, badgeWidth, badgeHeight), badge, badgeScale,
                                MD3Theme.withAlpha(MD3Theme.TERTIARY_CONTAINER, 255),
                                MD3Theme.ON_TERTIARY_CONTAINER, null, 0.0f, null
                            )
                        }
                    }
                }
            }
        }
    }

    override fun flush(renderBatch: UiRenderBatch) {
        contentBuffer.flushAndClear()
    }


    private fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!scrollable || maxScroll <= 0.0f) {
            return false
        }
        val nextScroll = scroll - scrollY.toFloat() * 20.0f
        scroll = (nextScroll).coerceIn(0.0f, maxScroll)
        return true
    }

    override fun shouldCloseAfterClick(): Boolean {
        return true
    }

    override fun close() {
        inputView.close()

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
