package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.google.common.base.Suppliers
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.roundToInt

object BetterScoreboard : Module("Better Scoreboard", Category.RENDER) {

    private val backgroundColor by colorSetting("Background Color", Color(16, 17, 20, 90))
    private var radius by doubleSetting("Radius", 4.5, 0.0..12.0, 0.5)
    private val xOffset by doubleSetting("X Offset", 0.0, -500.0..500.0, 1.0)
    private val yOffset by doubleSetting("Y Offset", 0.0, -500.0..500.0, 1.0)
    private val backgroundBlur by boolSetting("Background Blur", true)
    private val blurStrength by doubleSetting("Blur Strength", 8.0, 1.0..20.0, 0.5, { backgroundBlur })
    private val drawShadow by boolSetting("Draw Shadow", true)
    private val shadowBlur by doubleSetting("Shadow Blur", 10.0, 2.0..24.0, 1.0, { drawShadow })
    private val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 110), true, { drawShadow })

    private var pendingBounds: ScoreboardBounds? = null

    private val scheduler: Supplier<Render2DScheduler> = Suppliers.memoize { Render2DScheduler() }

    init {
        onEnable {
            pendingBounds = null
        }

        onDisable {
            pendingBounds = null
        }

        setDefaultEnabled(true)
    }


    init {
        handler<Render2DEvent.HUD> { onRender2D(it) }
    }

    fun beginScoreboardExtraction() {
        pendingBounds = null
    }

    fun captureVanillaBackground(x0: Int, y0: Int, x1: Int, y1: Int) {
        val left = minOf(x0, x1).toFloat()
        val top = minOf(y0, y1).toFloat()
        val right = maxOf(x0, x1).toFloat()
        val bottom = maxOf(y0, y1).toFloat()
        val bounds = ScoreboardBounds(left, top, right, bottom)
        val current = pendingBounds
        pendingBounds = if (current == null) bounds else current.include(bounds)
    }

    private fun onRender2D(event: Render2DEvent.HUD) {
        val bounds = pendingBounds ?: return
        pendingBounds = null

        val coordinateScale = (mc.window.guiScale / LuminRenderSystem.getGuiScale()).toFloat()
        val radius = this.radius.toFloat() * coordinateScale
        val x = (bounds.left + xOffset.toFloat()) * coordinateScale
        val y = (bounds.top + yOffset.toFloat()) * coordinateScale
        val width = bounds.width() * coordinateScale
        val height = bounds.height() * coordinateScale

        if (backgroundBlur) {
            BlurShader.render(x, y, width, height, radius, blurStrength.toFloat())
        }

        val renderScheduler = scheduler.get()
        renderScheduler.clear()
        val layer = renderScheduler.layer(0)

        if (drawShadow) {
            layer.addShadow(x, y, width, height, radius, shadowBlur.toFloat() * coordinateScale, shadowColor)
        }

        layer.addRoundRect(x, y, width, height, radius, backgroundColor)
        renderScheduler.flushAndClear()
    }

    fun getRoundedXOffset(): Int = (xOffset.toFloat()).roundToInt()

    fun getRoundedYOffset(): Int = (yOffset.toFloat()).roundToInt()

    private data class ScoreboardBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {

        fun width(): Float = right - left

        fun height(): Float = bottom - top

        fun include(other: ScoreboardBounds): ScoreboardBounds = ScoreboardBounds(
            minOf(left, other.left),
            minOf(top, other.top),
            maxOf(right, other.right),
            maxOf(bottom, other.bottom)
        )

    }

}
