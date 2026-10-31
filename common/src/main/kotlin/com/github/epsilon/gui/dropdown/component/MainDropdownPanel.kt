package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.ceil
import net.minecraft.util.Mth
import java.util.ArrayList
import java.util.function.BooleanSupplier
import java.util.function.Consumer

class MainDropdownPanel(
    panelIndex: Int,
    togglePanel: Consumer<String>,
    anySubPanelVisible: BooleanSupplier,
    panelVisibleResolver: PanelVisibleResolver,
) : AbstractDropdownPanel("main", Constants.NAME, "", panelIndex) {

    private val entries: MutableList<Entry> = ArrayList()
    private val settingsContent: SettingsContent

    init {
        width = 160.0f
        settingsContent = SettingsContent("client-settings:dropdown-main", ClientSetting.settings)
        isVisible = true
        isOpened = true
        add(LabelSupplier { Category.COMBAT.name }, Category.COMBAT.icon, "category:combat", togglePanel, panelVisibleResolver)
        add(LabelSupplier { Category.PLAYER.name }, Category.PLAYER.icon, "category:player", togglePanel, panelVisibleResolver)
        add(LabelSupplier { Category.MOVEMENT.name }, Category.MOVEMENT.icon, "category:movement", togglePanel, panelVisibleResolver)
        add(LabelSupplier { Category.RENDER.name }, Category.RENDER.icon, "category:render", togglePanel, panelVisibleResolver)
        add(
            LabelSupplier { EpsilonTranslations.Gui.TAB_FRIEND.getTranslatedName() },
            IconChars.PEOPLE, "friend", togglePanel, panelVisibleResolver
        )
        add(
            LabelSupplier { EpsilonTranslations.Gui.TAB_CONFIG.getTranslatedName() },
            IconChars.SETTINGS, "config", togglePanel, panelVisibleResolver
        )
        entries.add(
            Entry(
                LabelSupplier { EpsilonTranslations.Gui.DROPDOWN_COLLAPSE_ALL.getTranslatedName() },
                IconChars.CLOSE, "__collapse_all__", togglePanel, anySubPanelVisible
            )
        )
    }

    private fun add(
        labelSupplier: LabelSupplier,
        icon: String,
        panelId: String,
        togglePanel: Consumer<String>,
        panelVisibleResolver: PanelVisibleResolver,
    ) {
        entries.add(Entry(labelSupplier, icon, panelId, togglePanel, BooleanSupplier { panelVisibleResolver.getAsBoolean(panelId) }))
    }

    override fun drawBackground(scope: UiTree.Scope, textMetrics: UiTextMetrics) {
        super.drawBackground(scope, textMetrics)

        val versionScale = 0.48f
        val versionX = x + textMetrics.textWidth(getTitle(), DropdownTheme.HEADER_TEXT_SCALE) + 12.0f
        val versionMaxWidth = x + width - 17.0f - versionX
        if (versionMaxWidth <= 2.0f) return

        val version = trimToWidth(Constants.VERSION, versionScale, versionMaxWidth, textMetrics)
        if (version.isNotEmpty()) {
            val nameY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f
            val versionY = nameY + textMetrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE) - textMetrics.textHeight(versionScale)
            scope.text(version, versionX, versionY, versionScale, MD3Theme.TEXT_MUTED)
        }
    }

    override fun computeContentHeight(): Float {
        val rows = iconRows
        return CONTENT_PADDING + rows * ICON_SIZE + maxOf(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING +
                settingsContent.computeContentHeight(getRenderFrameId())
    }

    override fun drawPanelContent(
        scope: UiTree.Scope,
        textMetrics: UiTextMetrics,
        mouseX: Int,
        mouseY: Int,
        visibleHeight: Float,
    ) {
        var currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING
        scope.rect(
            x + CONTENT_PADDING, y + DropdownTheme.PANEL_HEADER_HEIGHT, width - CONTENT_PADDING * 2.0f, 0.7f,
            MD3Theme.withAlpha(MD3Theme.OUTLINE, 55)
        )

        for (index in entries.indices) {
            val entry = entries[index]
            val iconX = getIconX(index)
            val row = index / ICON_COLUMNS
            val iconY = currentY + row * (ICON_SIZE + ICON_GAP)
            val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), iconX, iconY, ICON_SIZE, ICON_SIZE)
            val active = entry.isActive()
            entry.hoverAnim.run(if (hovered) 1.0f else 0.0f)
            val hover = entry.hoverAnim.value
            scope.roundRect(
                iconX, iconY, ICON_SIZE, ICON_SIZE, DropdownTheme.BUTTON_RADIUS,
                MD3Theme.lerp(
                    if (active) MD3Theme.PRIMARY_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGH,
                    MD3Theme.PRIMARY_CONTAINER,
                    hover * 0.5f
                )
            )
            val iconScale = ICON_SCALE
            val iconW = textMetrics.textWidth(entry.icon, iconScale, StaticFontLoader.ICONS)
            val iconH = textMetrics.textHeight(iconScale, StaticFontLoader.ICONS)
            scope.text(
                entry.icon, iconX + (ICON_SIZE - iconW) * 0.5f, iconY + (ICON_SIZE - iconH) / 2.0f, iconScale,
                if (active) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY, StaticFontLoader.ICONS
            )
            if (hovered) {
                val label = entry.labelSupplier.get()
                val labelScale = 0.42f
                val labelW = textMetrics.textWidth(label, labelScale)
                val labelX = Mth.clamp(iconX + (ICON_SIZE - labelW) * 0.5f, x + 2.0f, x + width - labelW - 2.0f)
                scope.text(label, labelX, iconY + ICON_SIZE + 1.0f, labelScale, MD3Theme.TEXT_MUTED)
            }
        }
        val rows = iconRows
        currentY += rows * ICON_SIZE + maxOf(0, rows - 1) * ICON_GAP + 4.0f + CONTENT_PADDING
        scope.rect(
            x + CONTENT_PADDING, currentY - 3.0f, width - CONTENT_PADDING * 2.0f, 0.7f,
            MD3Theme.withAlpha(MD3Theme.OUTLINE, 55)
        )
        settingsContent.draw(scope, textMetrics, mouseX, mouseY, x, currentY, width, getRenderFrameId())
    }

    override fun mouseClickedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            for (index in entries.indices) {
                val entry = entries[index]
                val iconX = getIconX(index)
                val iconY = currentY + (index / ICON_COLUMNS) * (ICON_SIZE + ICON_GAP)
                if (isHovered(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE)) {
                    entry.action.accept(entry.panelId)
                    return true
                }
            }
        }
        val rows = iconRows
        val settingsY = currentY + rows * ICON_SIZE + maxOf(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING
        return settingsContent.mouseClicked(mouseX, mouseY, button, x, settingsY, width)
    }

    override fun mouseReleasedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val settingsY = getSettingsY()
        return settingsContent.mouseReleased(mouseX, mouseY, button, x, settingsY, width)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return settingsContent.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(typedText: String): Boolean {
        return settingsContent.charTyped(typedText)
    }

    override fun hasActiveInput(): Boolean {
        return settingsContent.hasActiveInput()
    }

    private fun getSettingsY(): Float {
        val rows = iconRows
        return y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING + rows * ICON_SIZE +
                maxOf(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING
    }

    private val iconRows: Int
        get() = ceil(entries.size / ICON_COLUMNS.toDouble()).toInt()

    private fun getIconX(index: Int): Float {
        val rowStart = (index / ICON_COLUMNS) * ICON_COLUMNS
        val rowCount = minOf(ICON_COLUMNS, entries.size - rowStart)
        val rowWidth = rowCount * ICON_SIZE + maxOf(0, rowCount - 1) * ICON_GAP
        val rowX = x + (width - rowWidth) * 0.5f
        return rowX + (index - rowStart) * (ICON_SIZE + ICON_GAP)
    }

    fun interface PanelVisibleResolver {
        fun getAsBoolean(panelId: String): Boolean
    }

    private class Entry(
        val labelSupplier: LabelSupplier,
        val icon: String,
        val panelId: String,
        val action: Consumer<String>,
        private val activeSupplier: BooleanSupplier?,
    ) {

        val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)

        fun isActive(): Boolean {
            return activeSupplier != null && activeSupplier.asBoolean
        }
    }

    private fun interface LabelSupplier {
        fun get(): String
    }

    private companion object {

        private const val ICON_SIZE = 30.0f
        private const val ICON_GAP = 7.0f
        private const val ICON_SCALE = 0.88f
        private const val CONTENT_PADDING = 7.0f
        private const val ICON_COLUMNS = 4

    }

}
