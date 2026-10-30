package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.render.ColorUtils
import com.github.epsilon.utils.render.animation.Easing
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import net.minecraft.util.Mth
import java.awt.Color
import java.util.function.Supplier

private const val ROW_HEIGHT = 16.0f

object Hotkey : HudModule("Hotkey", 96.0f, ROW_HEIGHT) {

    private enum class ColorMode {
        Single,
        Double,
    }

    private enum class ShadowColorMode {
        Black,
        Follow,
    }

    private val colorMode by enumSetting("Color Mode", ColorMode.Single)
    private val singleColor by colorSetting("Color", Color.WHITE, false, dependency = { colorMode == ColorMode.Single })
    private val doubleColor1 by colorSetting("Color 1", Color.WHITE, false, dependency = { colorMode == ColorMode.Double })
    private val doubleColor2 by colorSetting("Color 2", Color.RED, false, dependency = { colorMode == ColorMode.Double })
    private val shadowColorMode by enumSetting("Shadow Color", ShadowColorMode.Black)
    private val showSuffix by boolSetting("Show Suffix", true)

    private val moduleAnimations: MutableMap<Module, ToggleAnimation> = HashMap()
    private val textRendererSupplier: Supplier<TextRenderer> = Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    override fun render(deltaTracker: DeltaTracker) {
        val textRenderer = textRendererSupplier.get()
        val modules: List<Module> = ModuleManager.getModules()
        val liveModules: MutableSet<Module> = HashSet(modules)
        moduleAnimations.keys.removeAll { module -> !liveModules.contains(module) }

        val now = System.currentTimeMillis()
        for (module in modules) {
            if (!isVisible(module)) continue
            moduleAnimations.getOrPut(module) { ToggleAnimation(now) }.update(true, now)
        }

        val iterator = moduleAnimations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (isVisible(entry.key)) continue
            if (entry.value.update(false, now) <= 0.0f) {
                iterator.remove()
            }
        }

        val entries: MutableList<Entry> = ArrayList()
        for (animationEntry in moduleAnimations.entries) {
            val animation = animationEntry.value.progress()
            if (animation <= 0.001f) continue

            val module = animationEntry.key
            val category = module.category
            if (category == null || module.name.isEmpty()) continue

            val name = module.translatedName
            val info = if (showSuffix) normalizeInfo(module.getInfo()) else ""
            val nameWidth = textRenderer.getWidth(name, TEXT_SCALE)
            val suffixWidth = if (info.isEmpty()) 0.0f else textRenderer.getWidth(" $info", TEXT_SCALE)
            entries.add(
                Entry(
                    module, name, info, category.icon, nameWidth, nameWidth + suffixWidth, animation
                )
            )
        }
        entries.sortWith(compareByDescending<Entry> { it.textWidth })

        var maxRowWidth = 0.0f
        var contentHeight = 0.0f
        for (entry in entries) {
            maxRowWidth = Math.max(maxRowWidth, ICON_SIZE + ICON_TEXT_GAP + entry.textWidth + TEXT_PADDING * 2.0f)
            contentHeight += (ROW_HEIGHT + ROW_GAP) * entry.animation
        }
        if (!entries.isEmpty()) {
            contentHeight -= ROW_GAP * entries.last().animation
        }
        setBounds(Math.max(ICON_SIZE, maxRowWidth), Math.max(ROW_HEIGHT, contentHeight))
        if (entries.isEmpty()) return

        var rowY = this.y
        for (entry in entries) {
            val animation = entry.animation
            val rowWidth = ICON_SIZE + ICON_TEXT_GAP + entry.textWidth + TEXT_PADDING * 2.0f
            val rowX = this.x - rowWidth * (1.0f - animation)
            val animatedHeight = ROW_HEIGHT * animation
            val animatedRadius = Math.min(CORNER_RADIUS, animatedHeight * 0.5f)
            val textX = rowX + ICON_SIZE + ICON_TEXT_GAP + TEXT_PADDING
            val textBackgroundX = textX - TEXT_PADDING
            val textBackgroundWidth = entry.textWidth + TEXT_PADDING * 2.0f

            BlurShader.render(rowX, rowY, ICON_SIZE, animatedHeight, animatedRadius, BLUR_STRENGTH)
            BlurShader.render(textBackgroundX, rowY, textBackgroundWidth, animatedHeight, animatedRadius, BLUR_STRENGTH)
            rowY += (ROW_HEIGHT + ROW_GAP) * animation
        }

        val scope: UiTree.Scope = renderScope()
        rowY = this.y
        var colorIndex = 0
        for (entry in entries) {
            val animation = entry.animation
            val rowWidth = ICON_SIZE + ICON_TEXT_GAP + entry.textWidth + TEXT_PADDING * 2.0f
            val rowX = this.x - rowWidth * (1.0f - animation)
            val animatedHeight = ROW_HEIGHT * animation
            val animatedRadius = Math.min(CORNER_RADIUS, animatedHeight * 0.5f)
            val textBackgroundX = rowX + ICON_SIZE + ICON_TEXT_GAP
            val textBackgroundWidth = entry.textWidth + TEXT_PADDING * 2.0f
            val rowColor = resolveTextColor(colorIndex++)
            val shadowColor = resolveShadowColor(rowColor, animation)
            scope.shadow(rowX, rowY, ICON_SIZE, animatedHeight, animatedRadius, SHADOW_BLUR, shadowColor)
            scope.shadow(textBackgroundX, rowY, textBackgroundWidth, animatedHeight, animatedRadius, SHADOW_BLUR, shadowColor)

            val iconHeight = textRenderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS)
            val iconWidth = textRenderer.getWidth(entry.icon, ICON_SCALE, StaticFontLoader.ICONS)
            val textColor = withAlpha(rowColor, animation)
            scope.text(
                entry.icon, rowX + (ICON_SIZE - iconWidth) * 0.5f, rowY + (animatedHeight - iconHeight) * 0.5f,
                ICON_SCALE, textColor, StaticFontLoader.ICONS
            )
            scope.text(
                entry.name, textBackgroundX + TEXT_PADDING,
                rowY + (animatedHeight - textRenderer.getHeight(TEXT_SCALE)) * 0.5f, TEXT_SCALE, textColor
            )
            if (entry.info.isNotEmpty()) {
                scope.text(
                    " " + entry.info, textBackgroundX + TEXT_PADDING + entry.nameWidth,
                    rowY + (animatedHeight - textRenderer.getHeight(TEXT_SCALE)) * 0.5f, TEXT_SCALE,
                    withAlpha(SUFFIX_COLOR, animation)
                )
            }
            rowY += (ROW_HEIGHT + ROW_GAP) * animation
        }
    }

    private fun resolveTextColor(index: Int): Color {
        if (colorMode == ColorMode.Single) {
            return singleColor
        }
        return animateColor(doubleColor1, doubleColor2, index * 100L)
    }

    private fun resolveShadowColor(textColor: Color, animation: Float): Color {
        val color = if (shadowColorMode == ShadowColorMode.Follow) {
            Color(textColor.red, textColor.green, textColor.blue, SHADOW_COLOR.alpha)
        } else {
            SHADOW_COLOR
        }
        return withAlpha(color, animation)
    }

    private fun animateColor(start: Color, end: Color, offset: Long): Color {
        var progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0
        }
        return ColorUtils.interpolateColor(start, end, progress.toFloat())
    }

    private fun isVisible(module: Module): Boolean {
        return module.isEnabled && !module.isHidden && module.category != null && module.name.isNotEmpty()
    }

    private fun normalizeInfo(info: String?): String {
        return if (info == null || info.isBlank()) "" else info
    }

    private fun withAlpha(color: Color, multiplier: Float): Color {
        val alpha = Mth.clamp((color.alpha * Mth.clamp(multiplier, 0.0f, 1.0f)).toInt(), 0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

    private class ToggleAnimation(now: Long) {

        private var target: Boolean = true
        private var progressValue: Float = 0f
        private var startedAt: Long = now

        fun update(target: Boolean, now: Long): Float {
            if (this.target != target) {
                val elapsed = Mth.clamp(now - startedAt, 0L, ANIMATION_DURATION_MS.toLong())
                this.target = target
                this.startedAt = now - (ANIMATION_DURATION_MS - elapsed)
            }

            val elapsed = Mth.clamp((now - startedAt) / ANIMATION_DURATION_MS.toFloat(), 0.0f, 1.0f)
            val eased = Easing.EASE_IN_OUT_QUAD.function.apply(elapsed)
            progressValue = if (target) eased else 1.0f - eased
            return progressValue
        }

        fun progress(): Float {
            return progressValue
        }

    }

    private data class Entry(
        val module: Module,
        val name: String,
        val info: String,
        val icon: String,
        val nameWidth: Float,
        val textWidth: Float,
        val animation: Float,
    )


    private const val ICON_SIZE = 16.0f
    private const val ROW_GAP = 2.0f
    private const val TEXT_PADDING = 4.0f
    private const val ICON_TEXT_GAP = 2.0f
    private const val TEXT_SCALE = 0.65f
    private const val ICON_SCALE = 0.72f
    private const val BLUR_STRENGTH = 8.0f
    private const val CORNER_RADIUS = 3.0f
    private const val SHADOW_BLUR = 10.0f
    private const val ANIMATION_DURATION_MS = 200
    private val SHADOW_COLOR: Color = Color(0, 0, 0, 110)
    private val SUFFIX_COLOR: Color = Color(170, 170, 170)

}
