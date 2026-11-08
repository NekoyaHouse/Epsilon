package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.render.animation.Easing
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import kotlin.math.ceil
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

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
        add(
            LabelSupplier { Category.COMBAT.name },
            Category.COMBAT.icon,
            "category:combat",
            togglePanel,
            panelVisibleResolver
        )
        add(
            LabelSupplier { Category.PLAYER.name },
            Category.PLAYER.icon,
            "category:player",
            togglePanel,
            panelVisibleResolver
        )
        add(
            LabelSupplier { Category.MOVEMENT.name },
            Category.MOVEMENT.icon,
            "category:movement",
            togglePanel,
            panelVisibleResolver
        )
        add(
            LabelSupplier { Category.RENDER.name },
            Category.RENDER.icon,
            "category:render",
            togglePanel,
            panelVisibleResolver
        )
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
        entries.add(
            Entry(
                labelSupplier,
                icon,
                panelId,
                togglePanel,
                BooleanSupplier { panelVisibleResolver.getAsBoolean(panelId) })
        )
    }

    context(scope: UiScope)
    override fun drawBackground() {
        super.drawBackground()

        val versionScale = 0.48f
        val left = x - scope.origin.x
        val top = y - scope.origin.y
        val panelWidth = width
        with(scope) {
            val titleWidth = metrics.textWidth(getTitle(), DropdownTheme.HEADER_TEXT_SCALE)
            val versionX = left + titleWidth + 12.0f
            val versionMaxWidth = left + panelWidth - 17.0f - versionX
            if (versionMaxWidth <= 2.0f) return@with

            val version = trimToWidth(Constants.VERSION, versionScale, versionMaxWidth, metrics)
            if (version.isNotEmpty()) {
                val nameY =
                    top + (DropdownTheme.PANEL_HEADER_HEIGHT - metrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f
                val versionY =
                    nameY + metrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE) - metrics.textHeight(versionScale)
                Text(version, versionX, versionY, versionScale, MD3Theme.TEXT_MUTED)
            }
        }
    }

    override fun computeContentHeight(): Float {
        val rows = iconRows
        return CONTENT_PADDING + rows * ICON_SIZE + maxOf(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING +
                settingsContent.computeContentHeight(getRenderFrameId())
    }

    override fun UiScope.drawPanelContent(
        mouseX: Int,
        mouseY: Int,
        visibleHeight: Float,
    ) {
        val contentTop = contentLocalTop(origin)
        var currentY = contentTop - scroll + CONTENT_PADDING
        Layer(ICON_SURFACE) {
            Rect(
                CONTENT_PADDING, contentTop, width - CONTENT_PADDING * 2.0f, 0.7f,
                MD3Theme.withAlpha(MD3Theme.OUTLINE, 55)
            )
        }

        for (index in entries.indices) {
            val entry = entries[index]
            val iconX = getIconX(index)
            val row = index / ICON_COLUMNS
            val iconY = currentY + row * (ICON_SIZE + ICON_GAP)
            val hovered = isHoveredAt(this, mouseX.toDouble(), mouseY.toDouble(), iconX, iconY, ICON_SIZE, ICON_SIZE)
            val active = entry.isActive()
            // 悬停动画在逐项缓存之外推进，否则缓存命中时动画会冻结。
            entry.hoverAnim.run(if (hovered) 1.0f else 0.0f)
            val hover = entry.hoverAnim.value
            LuminUIItem(entry) {
                Interaction(key = entry, bounds = UiRect(iconX, iconY, ICON_SIZE, ICON_SIZE)) {
                    onClick { entry.action.accept(entry.panelId); true }
                }
                Layer(ICON_SURFACE) {
                    RoundRect(
                        iconX, iconY, ICON_SIZE, ICON_SIZE, DropdownTheme.BUTTON_RADIUS,
                        MD3Theme.lerp(
                            if (active) MD3Theme.PRIMARY_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGH,
                            MD3Theme.PRIMARY_CONTAINER,
                            hover * 0.5f
                        )
                    )
                }
                val iconScale = ICON_SCALE
                val iconW = metrics.textWidth(entry.icon, iconScale, StaticFontLoader.ICONS)
                val iconH = metrics.textHeight(iconScale, StaticFontLoader.ICONS)
                Layer(ICON_CONTENT) {
                    Text(
                        entry.icon, iconX + (ICON_SIZE - iconW) * 0.5f, iconY + (ICON_SIZE - iconH) / 2.0f, iconScale,
                        if (active) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY, StaticFontLoader.ICONS
                    )
                }
                if (hovered) {
                    val label = entry.labelSupplier.get()
                    val labelScale = 0.42f
                    val labelW = metrics.textWidth(label, labelScale)
                    val labelX = (iconX + (ICON_SIZE - labelW) * 0.5f).coerceIn(2.0f, width - labelW - 2.0f)
                    Text(label, labelX, iconY + ICON_SIZE + 1.0f, labelScale, MD3Theme.TEXT_MUTED)
                }
            }
        }
        val rows = iconRows
        currentY += rows * ICON_SIZE + maxOf(0, rows - 1) * ICON_GAP + 4.0f + CONTENT_PADDING
        Rect(
            CONTENT_PADDING, currentY - 3.0f, width - CONTENT_PADDING * 2.0f, 0.7f,
            MD3Theme.withAlpha(MD3Theme.OUTLINE, 55)
        )
        settingsContent.draw(this, mouseX, mouseY, currentY, width, getRenderFrameId())
    }


    private fun getSettingsY(): Float {
        val rows = iconRows
        return y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING + rows * ICON_SIZE +
                maxOf(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING
    }

    private val iconRows: Int
        get() = ceil(entries.size / ICON_COLUMNS.toDouble()).toInt()

    /** 图标行的横向局部坐标；命中测试时由调用方补上 `x`。 */
    private fun getIconX(index: Int): Float {
        val rowStart = (index / ICON_COLUMNS) * ICON_COLUMNS
        val rowCount = minOf(ICON_COLUMNS, entries.size - rowStart)
        val rowWidth = rowCount * ICON_SIZE + maxOf(0, rowCount - 1) * ICON_GAP
        val rowX = (width - rowWidth) * 0.5f
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
        // 图标底板互不重叠，按局部阶段提交，避免每个缓存项切换形状/字体管线。
        // 设置区和悬停提示仍在原层，偏移不越过相邻面板预留的 10 层间隔。
        val ICON_SURFACE = LayerOffset(-2)
        val ICON_CONTENT = LayerOffset(-1)

        private const val ICON_SIZE = 30.0f
        private const val ICON_GAP = 7.0f
        private const val ICON_SCALE = 0.88f
        private const val CONTENT_PADDING = 7.0f
        private const val ICON_COLUMNS = 4

    }

}
