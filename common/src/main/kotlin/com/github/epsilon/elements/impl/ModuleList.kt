package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
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

object ModuleList : HudModule("Module List", 4.0f, 16.0f, 96.0f, 20.0f) {

    private enum class DisplayMode {
        Simple,
        Normal,
    }

    private enum class ColorMode {
        Single,
        Double,
        Rainbow,
        Fade,
    }

    private val mode by enumSetting("Mode", DisplayMode.Normal)
    private val lineSpacing by doubleSetting("Line Spacing", 3.0, 0.0, 12.0, 0.5, Setting.Dependency { mode == DisplayMode.Simple })
    private val horizontalPadding by doubleSetting("Horizontal Padding", 3.0, 0.0, 20.0, 0.5, Setting.Dependency { mode == DisplayMode.Normal })
    private val verticalPadding by doubleSetting("Vertical Padding", 2.0, 0.0, 20.0, 0.5, Setting.Dependency { mode == DisplayMode.Normal })
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.0, 5.0, 0.5, Setting.Dependency { mode == DisplayMode.Normal })
    private val showSuffix by boolSetting("Show Suffix", true)
    private val fontSize by doubleSetting("Font Size", 13.0, 8.0, 32.0, 1.0)
    private val textShadow by boolSetting("Text Shadow", true)
    private val textShadowDepth by doubleSetting("Text Shadow Depth", 1.0, 0.0, 5.0, 0.1, Setting.Dependency { textShadow })
    private val textGlow by boolSetting("Text Glow", true)
    private val glowRadius by doubleSetting("Glow Radius", 3.0, 0.1, 6.0, 0.1, Setting.Dependency { textGlow })
    private val glowIntensity by intSetting("Glow Intensity", 2, 1, 5, 1, Setting.Dependency { textGlow })
    private val sliderSpeed by doubleSetting("Slider Speed", 0.2, 0.01, 1.0, 0.01)
    private val background by boolSetting("Background", true, Setting.Dependency { mode == DisplayMode.Normal })
    private val backgroundColor by colorSetting(
        "Background Color", Color(0, 0, 0, 50),
        Setting.Dependency { mode == DisplayMode.Normal && background }
    )
    private val drawShadow by boolSetting("Drop Shadow", true, Setting.Dependency { mode == DisplayMode.Normal })
    private val shadowBlur by doubleSetting(
        "Shadow Blur", 9.0, 2.0, 32.0, 1.0,
        Setting.Dependency { mode == DisplayMode.Normal && drawShadow }
    )
    private val shadowColorMode by enumSetting(
        "Shadow Color Mode", ColorMode.Single,
        Setting.Dependency { mode == DisplayMode.Normal && drawShadow }
    )
    private val shadowGradientLength by doubleSetting(
        "Shadow Gradient Length", 100.0, 10.0, 1000.0, 10.0,
        Setting.Dependency {
            mode == DisplayMode.Normal && drawShadow &&
                    (shadowColorMode == ColorMode.Double || shadowColorMode == ColorMode.Fade)
        }
    )
    private val shadowColor by colorSetting(
        "Shadow Color", Color(255, 255, 255, 90),
        Setting.Dependency {
            mode == DisplayMode.Normal && drawShadow && !shadowColorMode == ColorMode.Rainbow
        }
    )
    private val shadowColor2 by colorSetting(
        "Shadow Color 2", Color(255, 0, 0, 90),
        Setting.Dependency {
            mode == DisplayMode.Normal && drawShadow && shadowColorMode == ColorMode.Double
        }
    )
    private val colorMode by enumSetting("Color Mode", ColorMode.Single)
    private val doubleGradientLength by doubleSetting(
        "Double Gradient Length", 100.0, 10.0, 1000.0, 10.0,
        Setting.Dependency { mode == DisplayMode.Normal && (colorMode == ColorMode.Double || colorMode == ColorMode.Fade) }
    )
    private val rainbowSpeed by doubleSetting("Rainbow Speed", 1.0, 0.1, 5.0, 0.1, Setting.Dependency { usesRainbow() })
    private val rainbowSpread by doubleSetting("Rainbow Spread", 8.0, 0.0, 60.0, 1.0, Setting.Dependency { usesRainbow() })
    private val rainbowHueOffset by doubleSetting("Rainbow Hue Offset", 0.0, 0.0, 360.0, 1.0, Setting.Dependency { usesRainbow() })
    private val rainbowSaturation by doubleSetting("Rainbow Saturation", 0.5, 0.0, 1.0, 0.05, Setting.Dependency { usesRainbow() })
    private val rainbowBrightness by doubleSetting("Rainbow Brightness", 1.0, 0.1, 1.0, 0.05, Setting.Dependency { usesRainbow() })
    private val rainbowAlpha by intSetting("Rainbow Alpha", 255, 0, 255, 1, Setting.Dependency { colorMode == ColorMode.Rainbow })
    private val shadowRainbowAlpha by intSetting(
        "Shadow Rainbow Alpha", 90, 0, 255, 1,
        Setting.Dependency {
            mode == DisplayMode.Normal && drawShadow && shadowColorMode == ColorMode.Rainbow
        }
    )
    private val blur by boolSetting("Blur", true, Setting.Dependency { mode == DisplayMode.Normal })
    private val blurStrength by intSetting(
        "Blur Strength", 8, 1, 20, 1,
        Setting.Dependency { mode == DisplayMode.Normal && blur }
    )
    private val themeColor1 by colorSetting(
        "Theme Color 1", Color.WHITE, false,
        Setting.Dependency { !colorMode == ColorMode.Rainbow }
    )
    private val themeColor2 by colorSetting(
        "Theme Color 2", Color.RED, false,
        Setting.Dependency { colorMode == ColorMode.Double }
    )

    private val moduleAnimations: MutableMap<Module, ToggleAnimation> = HashMap()
    private val moduleYPositions: MutableMap<Module, Float> = HashMap()
    private var lastSortedModules: MutableList<Module> = ArrayList()

    private val textRendererSupplier: Supplier<TextRenderer> = Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    override fun render(deltaTracker: DeltaTracker) {
        val textRenderer = textRendererSupplier.get()

        val modules: List<Module> = ModuleManager.INSTANCE.getModules()
        val liveModules: MutableSet<Module> = HashSet(modules)
        moduleAnimations.keys.removeAll { module -> !liveModules.contains(module) }
        moduleYPositions.keys.removeAll { module -> !liveModules.contains(module) }

        val now = System.currentTimeMillis()
        for (module in modules) {
            if (!isVisible(module)) continue
            moduleAnimations.getOrPut(module) { ToggleAnimation(now) }.update(true, now)
        }

        val iterator = moduleAnimations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (isVisible(entry.key)) continue

            val progress = entry.value.update(false, now)
            if (progress <= 0.0f) {
                iterator.remove()
                moduleYPositions.remove(entry.key)
            }
        }

        if (mode == DisplayMode.Simple) {
            renderSimple(textRenderer)
        } else {
            renderNormal(textRenderer)
        }
    }

    private fun renderSimple(textRenderer: TextRenderer) {
        val textScale = Math.max(0.1f, fontSize.toFloat() / 16.0f)
        val entries = collectRenderEntries(textRenderer, textScale, showSuffix)
        val layout = updateLayout(entries, textRenderer.getHeight(textScale) + lineSpacing.toFloat())

        val boundsWidth = Math.max(MIN_BOUNDS, maxDisplayWidth(entries))
        setBounds(boundsWidth, Math.max(MIN_BOUNDS, layout.height))
        if (entries.isEmpty()) return

        val scope = renderScope()
        val rightSide = x + width * 0.5f > LuminRenderSystem.getScaledWidth() * 0.5f

        for (i in entries.indices) {
            val entry = entries[i]
            val animation = entry.animation
            if (animation <= 0.01f) continue

            val slideOffset = entry.displayWidth * (1.0f - animation)
            val textX = if (rightSide) x + boundsWidth - entry.displayWidth + slideOffset else x - slideOffset
            val textY = y + layout.renderY[i]
            val color = withAlpha(resolveColor(i, 100L), animation)

            drawModuleText(scope, entry, textX, textY, textScale, color, animation)
        }
    }

    private fun renderNormal(textRenderer: TextRenderer) {
        val textScale = Math.max(0.1f, fontSize.toFloat() / 16.0f)
        val textHeight = textRenderer.getHeight(textScale)
        val horizontalPadding = this.horizontalPadding.toFloat()
        val lineHeight = textHeight + this.verticalPadding.toFloat() * 2.0f

        val entries = collectRenderEntries(textRenderer, textScale, showSuffix)
        val layout = updateLayout(entries, lineHeight)
        val maxTextWidth = maxDisplayWidth(entries)
        val boundsWidth = Math.max(MIN_BOUNDS, maxTextWidth + horizontalPadding * 2.0f)
        setBounds(boundsWidth, Math.max(MIN_BOUNDS, layout.height))
        if (entries.isEmpty()) return

        val rightSide = x + width * 0.5f > LuminRenderSystem.getScaledWidth() * 0.5f
        val baseTextX = x + horizontalPadding
        val gradientStep = doubleGradientLength.toLong()
        val rows: MutableList<NormalRow> = ArrayList()
        var colorIndex = 0

        for (i in entries.indices) {
            val entry = entries[i]
            val animation = entry.animation
            if (animation <= 0.01f) continue

            val backgroundWidth = entry.displayWidth + horizontalPadding * 2.0f
            val slideOffset = backgroundWidth * (1.0f - animation)
            val textX = if (rightSide) baseTextX + maxTextWidth - entry.displayWidth + slideOffset else baseTextX - slideOffset
            val backgroundX = textX - horizontalPadding
            val backgroundY = y + layout.renderY[i]
            val backgroundHeight = lineHeight * animation
            val startColor = if (rows.isEmpty()) resolveColor(colorIndex, gradientStep) else rows.last().endColor
            val endColor = resolveColor(++colorIndex, gradientStep)

            rows.add(
                NormalRow(
                    entry, textX, backgroundX, backgroundY, backgroundWidth, backgroundHeight, startColor, endColor
                )
            )
        }

        val segmentBatches = buildSegmentBatches(rows)
        if (blur) renderBlur(segmentBatches)

        val scope = renderScope()
        if (drawShadow) renderShadow(scope, segmentBatches)

        for (row in rows) {
            val animation = row.entry.animation
            if (background) {
                scope.rect(
                    row.backgroundX, row.backgroundY, row.backgroundWidth, row.backgroundHeight,
                    withAlpha(backgroundColor, animation)
                )
            }

            val textY = row.backgroundY + (row.backgroundHeight - textHeight) * 0.5f
            drawModuleText(
                scope, row.entry, row.textX, textY, textScale,
                withAlpha(row.startColor, animation), withAlpha(row.endColor, animation), animation
            )
        }

        val lineWidth = this.lineWidth.toFloat() * textScale
        for (i in rows.indices) {
            val row = rows[i]
            val lineX = if (rightSide) row.backgroundX + row.backgroundWidth else row.backgroundX - lineWidth
            var lineBottom = row.backgroundY + row.backgroundHeight
            if (i + 1 < rows.size) {
                lineBottom = Math.max(lineBottom, rows[i + 1].backgroundY)
            }
            val bottomAnimation = if (i + 1 < rows.size) rows[i + 1].entry.animation else row.entry.animation
            scope.rectVerticalGradient(
                lineX, row.backgroundY, lineWidth, Math.max(0.0f, lineBottom - row.backgroundY),
                withAlpha(row.startColor, row.entry.animation), withAlpha(row.endColor, bottomAnimation)
            )
        }
    }

    private fun buildSegmentBatches(rows: List<NormalRow>): List<SegmentBatch> {
        val batches: MutableList<SegmentBatch> = ArrayList((rows.size + MAX_SEGMENTS - 1) / MAX_SEGMENTS)
        val gradientStep = shadowGradientLength.toLong()
        var offset = 0
        while (offset < rows.size) {
            val count = Math.min(MAX_SEGMENTS, rows.size - offset)
            val segmentRects = FloatArray(count * 4)
            val segmentRadii = FloatArray(count)
            val segmentColors = arrayOfNulls<Color>(count)
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY

            for (i in 0 until count) {
                val row = rows[offset + i]
                val index = i * 4
                segmentRects[index] = row.backgroundX
                segmentRects[index + 1] = row.backgroundY
                segmentRects[index + 2] = row.backgroundWidth
                segmentRects[index + 3] = row.backgroundHeight
                segmentColors[i] = resolveShadowColor(offset + i, gradientStep)

                minX = Math.min(minX, row.backgroundX)
                minY = Math.min(minY, row.backgroundY)
                maxX = Math.max(maxX, row.backgroundX + row.backgroundWidth)
                maxY = Math.max(maxY, row.backgroundY + row.backgroundHeight)
            }

            if (maxX > minX && maxY > minY) {
                @Suppress("UNCHECKED_CAST")
                batches.add(
                    SegmentBatch(
                        minX, minY, maxX - minX, maxY - minY,
                        segmentRects, segmentRadii, segmentColors as Array<Color>, count
                    )
                )
            }
            offset += MAX_SEGMENTS
        }
        return batches
    }

    private fun renderBlur(batches: List<SegmentBatch>) {
        for (batch in batches) {
            BlurShader.render(
                batch.x, batch.y, batch.width, batch.height, 0.0f, blurStrength.toFloat(),
                batch.rects, batch.radii, batch.count
            )
        }
    }

    private fun renderShadow(scope: UiTree.Scope, batches: List<SegmentBatch>) {
        val blurRadius = shadowBlur.toFloat()
        var color = shadowColor
        if (shadowColorMode == ColorMode.Rainbow) {
            color = Color(color.red, color.green, color.blue, shadowRainbowAlpha)
        }
        for (batch in batches) {
            val segmentColors = FloatArray(batch.count * 3)
            for (i in 0 until batch.count) {
                val segmentColor = batch.colors[i]
                val offset = i * 3
                segmentColors[offset] = segmentColor.red / 255.0f
                segmentColors[offset + 1] = segmentColor.green / 255.0f
                segmentColors[offset + 2] = segmentColor.blue / 255.0f
            }
            scope.shadow(
                batch.x, batch.y, batch.width, batch.height, 0.0f, blurRadius, color,
                batch.rects, batch.radii, segmentColors, batch.count
            )
        }
    }

    private fun collectRenderEntries(textRenderer: TextRenderer, textScale: Float, showSuffix: Boolean): List<RenderEntry> {
        val entries: MutableList<RenderEntry> = ArrayList()
        for (animationEntry in moduleAnimations.entries) {
            val animation = animationEntry.value.progress()
            if (animation <= 0.001f) continue

            val module = animationEntry.key
            val name = module.translatedName
            val info = if (showSuffix) normalizeInfo(module.getInfo()) else ""
            val nameWidth = textRenderer.getWidth(name, textScale)
            val suffixWidth = if (info.isEmpty()) 0.0f else textRenderer.getWidth(" $info", textScale)
            entries.add(RenderEntry(module, name, info, nameWidth, nameWidth + suffixWidth, animation))
        }

        entries.sortWith(compareByDescending<RenderEntry> { it.displayWidth })

        val sortedModules: MutableList<Module> = ArrayList(entries.size)
        for (entry in entries) {
            sortedModules.add(entry.module)
        }
        if (sortedModules != lastSortedModules) {
            moduleYPositions.clear()
            lastSortedModules = sortedModules
        }
        return entries
    }

    private fun updateLayout(entries: List<RenderEntry>, lineHeight: Float): LayoutState {
        val renderY = FloatArray(entries.size)
        var accumulatedY = 0.0f
        val speed = sliderSpeed.toFloat()

        for (i in entries.indices) {
            val entry = entries[i]
            val targetY = accumulatedY
            var currentY = moduleYPositions[entry.module] ?: targetY
            val difference = targetY - currentY
            currentY = if (Math.abs(difference) > 0.1f) currentY + difference * speed else targetY
            moduleYPositions[entry.module] = currentY
            renderY[i] = currentY

            if (entry.animation > 0.01f) {
                accumulatedY += lineHeight * entry.animation
            }
        }

        return LayoutState(renderY, accumulatedY)
    }

    private fun isVisible(module: Module): Boolean {
        return module.isEnabled && !module.isHidden && module.name.isNotEmpty()
    }

    private fun drawModuleText(
        scope: UiTree.Scope,
        entry: RenderEntry,
        x: Float,
        y: Float,
        textScale: Float,
        nameColor: Color,
        animation: Float,
    ) {
        drawModuleText(scope, entry, x, y, textScale, nameColor, nameColor, animation)
    }

    private fun drawModuleText(
        scope: UiTree.Scope,
        entry: RenderEntry,
        x: Float,
        y: Float,
        textScale: Float,
        nameStartColor: Color,
        nameEndColor: Color,
        animation: Float,
    ) {
        val glowRadius = this.glowRadius.toFloat()
        val glowIntensity = this.glowIntensity
        val glowColor = ColorUtils.interpolateColor(nameStartColor, nameEndColor, 0.5f)
        val shadowOffset = textShadowDepth.toFloat() * Math.max(1.0f, textScale)
        if (textShadow) {
            scope.text(entry.name, x + shadowOffset, y + shadowOffset, textScale, darken(glowColor, 0.5f))
        }
        if (textGlow) {
            scope.blurredText(entry.name, x, y, textScale, glowRadius * textScale, glowIntensity, glowColor)
        }
        scope.gradientText(entry.name, x, y, textScale, nameStartColor, nameEndColor)
        if (entry.info.isNotEmpty()) {
            val text = " " + entry.info
            val textX = x + entry.nameWidth
            val color = withAlpha(Color(170, 170, 170), animation)
            if (textShadow) {
                scope.text(text, textX + shadowOffset, y + shadowOffset, textScale, darken(color, 0.5f))
            }
            if (textGlow) {
                scope.blurredText(text, textX, y, textScale, glowRadius * textScale, glowIntensity, color)
            }
            scope.text(text, textX, y, textScale, color)
        }
    }

    private fun resolveColor(index: Int, offsetStep: Long): Color {
        return when (colorMode) {
            ColorMode.Rainbow -> rainbowColor(index, rainbowAlpha)
            ColorMode.Double -> animateColor(themeColor1, themeColor2, index * offsetStep)
            ColorMode.Fade -> animateColor(themeColor1, darker(themeColor1), index * offsetStep)
            ColorMode.Single -> themeColor1
        }
    }

    private fun resolveShadowColor(index: Int, offsetStep: Long): Color {
        return when (shadowColorMode) {
            ColorMode.Rainbow -> rainbowColor(index, shadowRainbowAlpha)
            ColorMode.Double -> animateColor(shadowColor, shadowColor2, index * offsetStep)
            ColorMode.Fade -> animateColor(shadowColor, darker(shadowColor), index * offsetStep)
            ColorMode.Single -> shadowColor
        }
    }

    fun getThemeColor(index: Int, offsetStep: Long): Color {
        return resolveColor(index, offsetStep)
    }

    private fun usesRainbow(): Boolean {
        return colorMode == ColorMode.Rainbow ||
                mode == DisplayMode.Normal && drawShadow && shadowColorMode == ColorMode.Rainbow
    }

    private fun rainbowColor(index: Int, alpha: Int): Color {
        val hueDegrees = System.currentTimeMillis() * 0.1 * rainbowSpeed +
                rainbowHueOffset + index * rainbowSpread
        val rgb = Color.HSBtoRGB(
            ((hueDegrees % 360.0) / 360.0).toFloat(),
            rainbowSaturation.toFloat(),
            rainbowBrightness.toFloat()
        )
        return Color(rgb ushr 16 and 0xFF, rgb ushr 8 and 0xFF, rgb and 0xFF, Mth.clamp(alpha, 0, 255))
    }

    private fun animateColor(start: Color, end: Color, offset: Long): Color {
        var progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0
        }
        return ColorUtils.interpolateColor(start, end, progress.toFloat())
    }

    private fun darker(color: Color): Color {
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        val rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2] * 0.6f)
        return Color(rgb)
    }

    private fun darken(color: Color, factor: Float): Color {
        val red = Mth.clamp((color.red * factor).toInt(), 0, 255)
        val green = Mth.clamp((color.green * factor).toInt(), 0, 255)
        val blue = Mth.clamp((color.blue * factor).toInt(), 0, 255)
        return Color(red, green, blue, color.alpha)
    }

    private fun withAlpha(color: Color, multiplier: Float): Color {
        val alpha = Mth.clamp((color.alpha * Mth.clamp(multiplier, 0.0f, 1.0f)).toInt(), 0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

    private fun maxDisplayWidth(entries: List<RenderEntry>): Float {
        var width = 0.0f
        for (entry in entries) {
            width = Math.max(width, entry.displayWidth)
        }
        return width
    }

    private fun normalizeInfo(info: String?): String {
        return if (info == null || info.isBlank()) "" else info
    }

    private class ToggleAnimation(now: Long) {

        private var target: Boolean = true
        private var progressValue: Float = 0f
        private var startedAt: Long = now

        fun update(target: Boolean, now: Long): Float {
            if (this.target != target) {
                val elapsed = Mth.clamp(now - startedAt, 0L, MODULE_ANIMATION_DURATION_MS.toLong())
                this.target = target
                this.startedAt = now - (MODULE_ANIMATION_DURATION_MS - elapsed)
            }

            val elapsed = Mth.clamp((now - startedAt) / MODULE_ANIMATION_DURATION_MS.toFloat(), 0.0f, 1.0f)
            val eased = Easing.EASE_IN_OUT_QUAD.function.apply(elapsed)
            progressValue = if (target) eased else 1.0f - eased
            return progressValue
        }

        fun progress(): Float {
            return progressValue
        }

    }

    private data class RenderEntry(
        val module: Module,
        val name: String,
        val info: String,
        val nameWidth: Float,
        val displayWidth: Float,
        val animation: Float,
    )

    private data class LayoutState(val renderY: FloatArray, val height: Float)

    private data class NormalRow(
        val entry: RenderEntry,
        val textX: Float,
        val backgroundX: Float,
        val backgroundY: Float,
        val backgroundWidth: Float,
        val backgroundHeight: Float,
        val startColor: Color,
        val endColor: Color,
    )

    private data class SegmentBatch(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rects: FloatArray,
        val radii: FloatArray,
        val colors: Array<Color>,
        val count: Int,
    )


    private const val MAX_SEGMENTS = 64
    private const val MIN_BOUNDS = 20.0f
    private const val MODULE_ANIMATION_DURATION_MS = 200

}
