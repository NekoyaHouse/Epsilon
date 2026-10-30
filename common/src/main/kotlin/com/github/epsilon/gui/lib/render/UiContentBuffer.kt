package com.github.epsilon.gui.lib.render

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.control.UiScrollBar
import java.awt.Color
import java.util.ArrayList
import java.util.Objects

/**
 * 可裁剪 UI 视口的内容缓冲。
 *
 * 内容先写入一个 scheduler-backed 批次，flush 时再统一附加 scissor 和滚动条。
 * 这样列表、设置页、popup 视口不再各自维护一组 GPU renderer，能和主 GUI 一起进入 Render2DScheduler 合批。
 */
class UiContentBuffer(theme: UiTheme) : AutoCloseable {

    private val theme: UiTheme = Objects.requireNonNull(theme, "theme")
    private val batch = UiRenderBatch(theme)
    private val scrollBarBatch = batch.view(8)
    private val marqueeBatch = batch.view(9)
    private val scrollBar = UiScrollBar(theme)
    private val marqueeDraws = ArrayList<MarqueeTextDraw>()

    private var pending = false
    private var pendingViewport: UiRect? = null

    fun theme(): UiTheme {
        return theme
    }

    fun contentLayer(): Render2DScheduler.LayerHandle {
        return batch.layerHandle(0)
    }

    fun textMetrics(): TextRenderer {
        return batch.scheduler().textMetrics()
    }

    fun clear() {
        clearContent()
        marqueeDraws.clear()
        pending = false
        pendingViewport = null
    }

    fun addMarqueeText(draw: MarqueeTextDraw) {
        marqueeDraws.add(draw)
    }

    private fun clearContent() {
        batch.clear()
    }

    /**
     * 记录本帧视口信息，并把已有内容层绑定到对应 framebuffer scissor。
     */
    fun beginViewport(viewport: UiRect) {
        val scissor = LuminRenderSystem.toFramebufferScissor(viewport.x, viewport.y, viewport.width, viewport.height)
        batch.layerHandle(0).setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
    }

    /**
     * 记录本帧视口信息，并为滚动条与跑马灯文本准备本帧附加层。
     */
    fun queueViewport(viewport: UiRect, scroll: Float, maxScroll: Float, contentHeight: Float) {
        queueViewport(viewport, scroll, maxScroll, contentHeight, Int.MIN_VALUE, Int.MIN_VALUE)
    }

    fun queueViewport(
        viewport: UiRect, scroll: Float, maxScroll: Float,
        contentHeight: Float, mouseX: Int, mouseY: Int
    ) {
        beginViewport(viewport)
        scrollBarBatch.clear()
        scrollBarBatch.render(
            UiTree.build { scope ->
                scrollBar.draw(scope, viewport, scroll, maxScroll, contentHeight, mouseX.toDouble(), mouseY.toDouble())
            }
        )
        pendingViewport = viewport
        pending = true
    }

    fun flush() {
        if (!pending) {
            return
        }
        flushMarqueeTexts()
        batch.flush()
        scrollBarBatch.clear()
        marqueeBatch.clear()
        pending = false
    }

    private fun flushMarqueeTexts() {
        marqueeBatch.clear()
        val viewport = pendingViewport
        if (marqueeDraws.isEmpty() || viewport == null) {
            marqueeDraws.clear()
            return
        }
        for (draw in marqueeDraws) {
            // 跑马灯文本有自己的窄裁剪区，需要与 viewport 求交后单独写入文本层。
            val clip = draw.clip.intersect(viewport) ?: continue
            val scissor = LuminRenderSystem.toFramebufferScissor(clip.x, clip.y, clip.width, clip.height)
            if (scissor.width <= 0 || scissor.height <= 0) {
                continue
            }
            marqueeBatch.layerHandle(0).setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
            val font = draw.font
            marqueeBatch.render(
                UiTree.build { scope ->
                    if (font != null) {
                        scope.text(draw.text, draw.x, draw.y, draw.scale, draw.color, font)
                    } else {
                        scope.text(draw.text, draw.x, draw.y, draw.scale, draw.color)
                    }
                }
            )
        }
        marqueeDraws.clear()
    }

    fun flushAndClear() {
        flush()
        clearContent()
    }

    override fun close() {
        clear()
        batch.close()
    }

    data class MarqueeTextDraw(
        val text: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val color: Color,
        val font: TtfFontLoader?,
        val clip: UiRect,
    )

}
