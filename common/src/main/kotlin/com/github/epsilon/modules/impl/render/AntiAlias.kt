package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.AfterRender3DEvent
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.graphics.shaders.FXAAShader
import com.github.epsilon.graphics.shaders.OverlayAntiAlias
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

object AntiAlias : Module("Anti Alias", Category.RENDER) {

    /**
     * 抗锯齿模式：FXAA 对整屏主渲染目标做后处理；SSAA 只对 Render3DScheduler 批处理的覆盖层做超采样。
     */
    enum class Mode {
        FXAA,
        SSAA
    }

    enum class Samples(internal val scale: Int) {
        X4(2),
        X16(4)
    }

    private val mode by enumSetting("Mode", Mode.SSAA, onChanged = { newMode -> onModeChanged(newMode) })
    private val samples by enumSetting("Samples", Samples.X4, { mode == Mode.SSAA })

    /**
     * SSAA 叠加层入口：仅 SSAA 模式下重定向绘制目标，其余模式直接执行原绘制。
     */
    init {
        onDisable {
            mc.execute { closeOffscreenTargets() }
        }

        handler<AfterRender3DEvent> { onAfterRender3D(it) }
        handler<GameLeftEvent> { onGameLeft(it) }
    }

    fun render(drawAction: Runnable) {
        if (isEnabled && mode == Mode.SSAA) {
            OverlayAntiAlias.render(samples.scale, drawAction)
        } else {
            drawAction.run()
        }
    }

    /**
     * FXAA 在 3D 渲染完成后处理整屏颜色目标，因此也会平滑覆盖层内容。
     */
    private fun onAfterRender3D(event: AfterRender3DEvent) {
        if (mode == Mode.FXAA) {
            FXAAShader.renderMainTarget()
        }
    }

    private fun onGameLeft(event: GameLeftEvent) {
        mc.execute { closeOffscreenTargets() }
    }

    /**
     * 切换模式后释放另一种模式占用的离屏纹理；GPU 资源只在渲染线程释放，之后按需重建。
     */
    private fun onModeChanged(newMode: Mode) {
        mc.execute {
            if (newMode == Mode.SSAA) {
                FXAAShader.close()
            } else {
                OverlayAntiAlias.close()
            }
        }
    }

    private fun closeOffscreenTargets() {
        OverlayAntiAlias.close()
        FXAAShader.close()
    }

}
