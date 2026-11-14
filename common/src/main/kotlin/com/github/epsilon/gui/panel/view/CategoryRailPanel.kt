package com.github.epsilon.gui.panel.view

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.Category
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color
import kotlin.math.abs
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class CategoryRailPanel(protected val state: PanelState) {

    private val expandAnimation = Animation(Easing.EASE_OUT_CUBIC, 240L)
    private val contentAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)
    private val menuHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val headerTitleAnimation = Animation(Easing.EASE_OUT_CUBIC, 220L)
    private val headerSubtitleAnimation = Animation(Easing.EASE_OUT_CUBIC, 260L)
    private val headerDividerAnimation = Animation(Easing.EASE_OUT_CUBIC, 220L)
    private val selectionYAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)
    private val selectionHeightAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)
    private val hoverYAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)
    private val hoverAlphaAnimation = Animation(Easing.EASE_OUT_CUBIC, 100L)
    private val settingsHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private var bounds: UiRect? = null

    init {
        this.expandAnimation.startValue = MD3Theme.RAIL_COLLAPSED_WIDTH
        this.contentAnimation.startValue = 0.0f
        this.menuHoverAnimation.startValue = 0.0f
        this.headerTitleAnimation.startValue = 0.0f
        this.headerSubtitleAnimation.startValue = 0.0f
        this.headerDividerAnimation.startValue = 0.0f
        this.selectionYAnimation.startValue = 0.0f
        this.selectionHeightAnimation.startValue = 32.0f
        this.hoverYAnimation.startValue = 0.0f
        this.hoverAlphaAnimation.startValue = 0.0f
        this.settingsHoverAnimation.startValue = 0.0f
    }

    fun getAnimatedWidth(): Float {
        expandAnimation.run(if (state.isSidebarExpanded()) MD3Theme.RAIL_EXPANDED_WIDTH else MD3Theme.RAIL_COLLAPSED_WIDTH)
        return expandAnimation.value
    }

    private val luminView = LuminView()

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        luminView.render(mouseX, mouseY) {
            renderContent(
                guiGraphics,
                renderBatch,
                bounds,
                mouseX,
                mouseY,
                partialTick
            )
        }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds
        val targetWidth = if (state.isSidebarExpanded()) MD3Theme.RAIL_EXPANDED_WIDTH else MD3Theme.RAIL_COLLAPSED_WIDTH
        val targetContent = if (state.isSidebarExpanded()) 1.0f else 0.0f
        val requiresScissor = abs(bounds.width - targetWidth) > 0.001f ||
                abs(contentAnimation.value - targetContent) > 0.001f
        renderBatch.LuminUIScene(bounds, key = this to "pass-1") {

            ClipIf(requiresScissor, 0.0f, 0.0f, width, height) {
                val contentProgress = animate(contentAnimation, state.isSidebarExpanded())
                val titleProgress = animate(headerTitleAnimation, contentProgress)
                val subtitleProgress = animate(headerSubtitleAnimation, contentProgress > 0.08f)
                val dividerProgress = animate(headerDividerAnimation, contentProgress > 0.12f)
                val titleScale = 0.78f
                val subtitleScale = 0.52f
                val itemIconScale = 1.02f
                val itemLabelScale = 0.62f
                val itemCountScale = 0.58f

                val menuButton = getMenuButtonBounds()
                val menuHover = animate(menuHoverAnimation, mouseOver(menuButton, mouseX, mouseY))
                Interaction("menu", menuButton.relativeTo(origin)) {
                    onClick {
                        state.toggleSidebarExpanded()
                        SoundManager.playInUi(if (state.isSidebarExpanded()) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE)
                        true
                    }
                }
                val localMenu = menuButton.relativeTo(origin)
                Positioned(localMenu.x, localMenu.y, localMenu.width, localMenu.height) {
                    menuGlyph(menuButton.width, menuButton.height, menuHover)
                }

                val categoryStartY = getCategoryStartY(bounds)
                if (titleProgress > 0.02f) {
                    val titleY = 7.0f
                    val titleHeight = metrics.textHeight(titleScale)
                    val pad = 3.0f
                    val subtitleY = titleY + titleHeight + pad
                    val titleOffset = (1.0f - titleProgress) * 8.0f
                    val subtitleOffset = (1.0f - subtitleProgress) * 10.0f
                    Text(
                        Constants.NAME, 38.0f + titleOffset, titleY, titleScale,
                        MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, (255 * titleProgress).toInt())
                    )
                    if (subtitleProgress > 0.02f) {
                        Text(
                            Constants.VERSION, 38.0f + subtitleOffset, subtitleY, subtitleScale,
                            MD3Theme.withAlpha(MD3Theme.TEXT_SECONDARY, (210 * subtitleProgress).toInt())
                        )
                    }
                    if (dividerProgress > 0.02f) {
                        val dividerY = subtitleY + metrics.textHeight(subtitleScale) + 4.0f
                        val dividerBaseX = 7.0f
                        val dividerTargetWidth = width - 14.0f
                        val dividerWidth = dividerTargetWidth * dividerProgress
                        val dividerX = dividerBaseX + (1.0f - dividerProgress) * 6.0f
                        Rect(
                            dividerX, dividerY, dividerWidth, 1.0f,
                            MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, (120 * dividerProgress).toInt())
                        )
                        Rect(
                            dividerX, dividerY, minOf(18.0f, dividerWidth), 1.0f,
                            MD3Theme.withAlpha(MD3Theme.TEXT_SECONDARY, (52 * dividerProgress).toInt())
                        )
                    }
                }

                var selectedItemY = categoryStartY
                if (state.isClientSettingMode()) {
                    selectedItemY = getSettingsButtonY()
                } else {
                    var lookupY = categoryStartY
                    for (category in Category.entries) {
                        if (state.getSelectedCategory() === category) {
                            selectedItemY = lookupY
                            break
                        }
                        lookupY += CATEGORY_ITEM_SPACING
                    }
                }

                var hoveredY = -1.0f
                var scanY = categoryStartY
                for (category in Category.entries) {
                    val scanRect = UiRect(bounds.x + 5.0f, scanY, bounds.width - 10.0f, CATEGORY_ITEM_HEIGHT)
                    if (scanRect.contains(mouseX.toDouble(), mouseY.toDouble())) {
                        hoveredY = scanY
                        break
                    }
                    scanY += CATEGORY_ITEM_SPACING
                }
                if (hoveredY < 0) {
                    val settingsScanRect = UiRect(
                        bounds.x + 5.0f, getSettingsButtonY(), bounds.width - 10.0f, CATEGORY_ITEM_HEIGHT
                    )
                    if (settingsScanRect.contains(mouseX.toDouble(), mouseY.toDouble())) {
                        hoveredY = getSettingsButtonY()
                    }
                }

                val hoverAlpha = animate(hoverAlphaAnimation, hoveredY >= 0)
                if (hoveredY >= 0) {
                    animate(hoverYAnimation, hoveredY)
                }
                if (hoverAlpha > 0.01f) {
                    Positioned(5.0f, hoverYAnimation.value - origin.y, width - 10.0f, CATEGORY_ITEM_HEIGHT) {
                        RoundRect(
                            0.0f, 0.0f, width, height, MD3Theme.CARD_RADIUS.toFloat(),
                            MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, (200 * hoverAlpha).toInt())
                        )
                    }
                }

                val animatedSelectionY = animate(selectionYAnimation, selectedItemY)
                val animatedSelectionHeight = animate(selectionHeightAnimation, CATEGORY_ITEM_HEIGHT)
                Positioned(5.0f, animatedSelectionY - origin.y, width - 10.0f, animatedSelectionHeight) {
                    RoundRect(
                        0.0f, 0.0f, width, height, MD3Theme.CARD_RADIUS.toFloat(),
                        MD3Theme.SECONDARY_CONTAINER
                    )
                }

                var itemY = categoryStartY
                for (category in Category.entries) {
                    val currentItemY = itemY
                    val itemRect = UiRect(bounds.x + 5.0f, currentItemY, bounds.width - 10.0f, CATEGORY_ITEM_HEIGHT)
                    val hovered = itemRect.contains(mouseX.toDouble(), mouseY.toDouble())
                    val selected = !state.isClientSettingMode() && state.getSelectedCategory() === category
                    val count = getCategoryCount(category)
                    Interaction(category, itemRect.relativeTo(origin)) {
                        onClick {
                            state.setClientSettingMode(false); state.setSelectedCategory(category); true
                        }
                    }
                    buildCategoryItem(
                        menuButton, itemRect, category, count, hovered, selected, contentProgress,
                        itemIconScale, itemLabelScale, itemCountScale
                    )
                    itemY += CATEGORY_ITEM_SPACING
                }

                val settingsBtnY = getSettingsButtonY()
                val settingsRect = UiRect(bounds.x + 5.0f, settingsBtnY, bounds.width - 10.0f, CATEGORY_ITEM_HEIGHT)
                val settingsHovered = settingsRect.contains(mouseX.toDouble(), mouseY.toDouble())
                val settingsSelected = state.isClientSettingMode()
                val settingsHover = animate(settingsHoverAnimation, settingsHovered)
                Interaction(
                    "settings",
                    settingsRect.relativeTo(origin)
                ) { onClick { state.setClientSettingMode(true); true } }
                buildSettingsItem(
                    menuButton, settingsRect, settingsHovered, settingsSelected, contentProgress,
                    settingsHover, itemIconScale, itemLabelScale
                )
            }
        }
    }


    private fun getMenuButtonBounds(): UiRect {
        val currentBounds = bounds!!
        return UiRect(
            currentBounds.x + 4.0f + RAIL_ICON_CENTER_X_OFFSET, currentBounds.y + 4.0f, 28.0f, 28.0f
        )
    }

    private fun mouseOver(rect: UiRect, mouseX: Int, mouseY: Int): Boolean {
        return rect.contains(mouseX.toDouble(), mouseY.toDouble())
    }

    private fun getCategoryStartY(bounds: UiRect): Float {
        // Keep category list vertically stable regardless of sidebar expansion progress.
        return bounds.y + CATEGORY_START_Y
    }

    private fun getSettingsButtonY(): Float {
        return bounds!!.bottom() - CATEGORY_ITEM_HEIGHT - 5.0f
    }

    private fun UiScope.menuGlyph(buttonWidth: Float, buttonHeight: Float, menuHover: Float) {
        RoundRect(
            0.0f, 0.0f, buttonWidth, buttonHeight, 12.0f,
            MD3Theme.lerp(
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 0),
                MD3Theme.SURFACE_CONTAINER_HIGH, menuHover
            )
        )
        val lineColor = MD3Theme.TEXT_PRIMARY
        val glyphHeight = 10.0f
        val x = buttonWidth / 2.0f - 6.0f
        val y = (buttonHeight - glyphHeight) / 2.0f
        Rect(x, y, 12.0f, 1.6f, lineColor)
        Rect(x, y + 4.0f, 12.0f, 1.6f, lineColor)
        Rect(x, y + 8.0f, 12.0f, 1.6f, lineColor)
    }

    private fun UiScope.buildCategoryItem(
        menuButton: UiRect, itemRect: UiRect, category: Category, count: Int,
        hovered: Boolean, selected: Boolean, contentProgress: Float,
        itemIconScale: Float, itemLabelScale: Float, itemCountScale: Float,
    ) {
        val background: Color = if (selected) {
            MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 0)
        } else if (hovered) {
            MD3Theme.SURFACE_CONTAINER
        } else {
            MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 0)
        }
        val iconColor: Color = if (selected) {
            MD3Theme.ON_SECONDARY_CONTAINER
        } else if (hovered) {
            MD3Theme.TEXT_PRIMARY
        } else {
            MD3Theme.TEXT_SECONDARY
        }
        val labelColor: Color = if (selected) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.TEXT_PRIMARY
        val countColor: Color = if (selected) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.TEXT_SECONDARY
        val iconHeight = metrics.textHeight(itemIconScale, StaticFontLoader.ICONS)
        val labelHeight = metrics.textHeight(itemLabelScale)
        val countHeight = metrics.textHeight(itemCountScale)
        val iconY = (itemRect.height - iconHeight) / 2.0f
        val labelY = (itemRect.height - labelHeight) / 2.0f
        val countY = (itemRect.height - countHeight) / 2.0f

        val localItem = itemRect.relativeTo(origin)
        Positioned(localItem.x, localItem.y, localItem.width, localItem.height) {
            RoundRect(0.0f, 0.0f, itemRect.width, itemRect.height, MD3Theme.CARD_RADIUS.toFloat(), background)
            val iconWidth = metrics.textWidth(category.icon, itemIconScale, StaticFontLoader.ICONS)
            val iconX = getRailIconCenterX(menuButton) - itemRect.x - iconWidth / 2.0f
            Text(category.icon, iconX, iconY, itemIconScale, iconColor, StaticFontLoader.ICONS)
            if (contentProgress > 0.02f) {
                val textOffset = (1.0f - contentProgress) * 5.0f
                val animatedLabel = MD3Theme.withAlpha(labelColor, (255 * contentProgress).toInt())
                val animatedCount = MD3Theme.withAlpha(countColor, (220 * contentProgress).toInt())
                Text(category.getName(), 30.0f + textOffset, labelY, itemLabelScale, animatedLabel)
                val countWidth = metrics.textWidth(count.toString(), itemCountScale)
                Text(
                    count.toString(), itemRect.width - 12.0f - countWidth, countY, itemCountScale, animatedCount
                )
            }
        }
    }

    private fun UiScope.buildSettingsItem(
        menuButton: UiRect, settingsRect: UiRect,
        settingsHovered: Boolean, settingsSelected: Boolean, contentProgress: Float, settingsHover: Float,
        itemIconScale: Float, itemLabelScale: Float,
    ) {
        val settingsBg: Color = if (settingsSelected) {
            MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 0)
        } else if (settingsHovered) {
            MD3Theme.SURFACE_CONTAINER
        } else {
            MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 0)
        }
        val settingsIconColor: Color = if (settingsSelected) {
            MD3Theme.ON_SECONDARY_CONTAINER
        } else if (settingsHovered) {
            MD3Theme.TEXT_PRIMARY
        } else {
            MD3Theme.TEXT_SECONDARY
        }
        val settingsLabelColor: Color = if (settingsSelected) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.TEXT_PRIMARY
        val localSettings = settingsRect.relativeTo(origin)
        Positioned(localSettings.x, localSettings.y, localSettings.width, localSettings.height) {
            RoundRect(
                0.0f, 0.0f, settingsRect.width, settingsRect.height, MD3Theme.CARD_RADIUS.toFloat(), settingsBg
            )
            val settingsIconWidth = metrics.textWidth(SETTINGS_ICON, itemIconScale, StaticFontLoader.ICONS)
            val settingsIconX = getRailIconCenterX(menuButton) - settingsRect.x - settingsIconWidth / 2.0f
            val settingsIconHeight = metrics.textHeight(itemIconScale, StaticFontLoader.ICONS)
            val settingsIconY = (settingsRect.height - settingsIconHeight) / 2.0f
            Text(
                SETTINGS_ICON, settingsIconX, settingsIconY, itemIconScale, settingsIconColor, StaticFontLoader.ICONS
            )
            if (contentProgress > 0.02f) {
                val textOffset = (1.0f - contentProgress) * 5.0f
                val animatedLabel = MD3Theme.withAlpha(settingsLabelColor, (255 * contentProgress).toInt())
                val settingsLabelHeight = metrics.textHeight(itemLabelScale)
                val settingsLabelY = (settingsRect.height - settingsLabelHeight) / 2.0f
                Text(
                    EpsilonTranslations.Gui.CLIENT_SETTINGS.getTranslatedName(), 30.0f + textOffset, settingsLabelY,
                    itemLabelScale, animatedLabel
                )
            }
        }
    }

    private fun getRailIconCenterX(railButton: UiRect): Float {
        return railButton.x + railButton.width / 2.0f
    }

    private fun getCategoryCount(category: Category): Int {
        return ModuleManager.getModules().count { module -> module.category === category }
    }

    private companion object {

        private const val CATEGORY_ITEM_HEIGHT = 34.0f
        private const val CATEGORY_ITEM_SPACING = 38.0f
        private const val CATEGORY_START_Y = 40.0f
        private const val RAIL_ICON_CENTER_X_OFFSET = 2.0f
        private const val SETTINGS_ICON = IconChars.SETTINGS

    }

}
