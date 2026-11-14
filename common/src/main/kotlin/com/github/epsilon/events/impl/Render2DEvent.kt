package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import net.minecraft.client.gui.GuiGraphicsExtractor

open class Render2DEvent protected constructor(val guiGraphics: GuiGraphicsExtractor) {

    class Level(guiGraphics: GuiGraphicsExtractor) : Render2DEvent(guiGraphics), IEvent, IPosting by Companion {
        /** 每次读取分配后续图层；相关图元应复用返回的 canvas，仅在当前事件派发期间使用。 */
        val canvas: Render2DScheduler.Canvas
            get() = backend().canvas(nextLayer++)

        /** 共享字体度量；文字通过 canvas 提交。 */
        val textMetrics: TextRenderer
            get() = backend().textMetrics()

        /** 模糊、目标切换、原版或独立 renderer 绘制前提交已排队命令。 */
        fun flush() {
            checkActive()
            scheduler?.flush()
        }

        private fun checkActive() {
            check(activeEvent === this) { "Render2DEvent.Level requires its active event dispatch" }
        }

        private fun backend(): Render2DScheduler {
            checkActive()
            return scheduler ?: Render2DScheduler().also {
                scheduler = it
                it.beginFrame()
            }
        }

        companion object : EventBus() {
            private var scheduler: Render2DScheduler? = null
            private var activeEvent: Level? = null
            private var nextLayer = 0

            /** 渲染线程派发 Level 事件时管理共享后端，异常时也清理当帧记录并结束几何生命周期。 */
            override fun post(event: Any) {
                require(event is Level)
                check(activeEvent == null) { "Render2DEvent.Level dispatch is already active" }
                activeEvent = event
                nextLayer = 0
                try {
                    scheduler?.clear()
                    scheduler?.beginFrame()
                    super.post(event)
                    event.flush()
                } finally {
                    try {
                        scheduler?.clear()
                        scheduler?.endFrame()
                    } finally {
                        activeEvent = null
                    }
                }
            }
        }
    }

    class HUD(guiGraphics: GuiGraphicsExtractor) : Render2DEvent(guiGraphics), IEvent, IPosting by Companion {
        companion object : EventBus()
    }

}
