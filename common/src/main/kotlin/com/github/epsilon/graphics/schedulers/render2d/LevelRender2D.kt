package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.renderers.TextRenderer

/**
 * 世界内二维标签共用的渲染后端，只在渲染线程的 Render2DEvent.Level 派发期间使用。
 * 每次 canvas() 分配后续图层，保留模块和标签的提交顺序；宿主在事件结束后统一提交。
 */
object LevelRender2D {
    private var scheduler: Render2DScheduler? = null
    private var inFrame = false
    private var nextLayer = 0

    /** 由 GUI 渲染入口包围整个 Level 事件；异常时也清除当帧记录并结束几何生命周期。 */
    @JvmStatic
    fun render(action: Runnable) {
        check(!inFrame) { "LevelRender2D frame is already active" }
        inFrame = true
        nextLayer = 0
        try {
            scheduler?.clear()
            scheduler?.beginFrame()
            action.run()
            flush()
        } finally {
            try {
                scheduler?.clear()
                scheduler?.endFrame()
            } finally {
                inFrame = false
            }
        }
    }

    fun canvas(): Render2DScheduler.Canvas = backend().canvas(nextLayer++)

    /** 共用字体度量；调用方只测量，文字由 canvas 提交。 */
    fun textMetrics(): TextRenderer = backend().textMetrics()

    /** 模糊、原版或独立 renderer 绘制前提交已排队命令，后续模块仍可继续追加。 */
    fun flush() {
        check(inFrame) { "LevelRender2D requires an active Level event" }
        scheduler?.flush()
    }

    private fun backend(): Render2DScheduler {
        check(inFrame) { "LevelRender2D requires an active Level event" }
        return scheduler ?: Render2DScheduler().also {
            scheduler = it
            it.beginFrame()
        }
    }
}
