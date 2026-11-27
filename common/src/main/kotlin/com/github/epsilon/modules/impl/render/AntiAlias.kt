package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.AfterRender3DEvent
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.graphics.shaders.FXAAShader
import com.github.epsilon.graphics.shaders.OverlayAntiAlias
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

object AntiAlias : Module("Anti Alias", Category.RENDER) {

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

    init {
        onDisable {
            mc.execute { closeOffscreenTargets() }
        }

        handler<AfterRender3DEvent> {
            onAfterRender3D(it)
        }

        handler<GameLeftEvent> {
            onGameLeft(it)
        }
    }

    fun render(drawAction: Runnable) {
        if (isEnabled && mode == Mode.SSAA) {
            OverlayAntiAlias.render(samples.scale, drawAction)
        } else {
            drawAction.run()
        }
    }

    private fun onAfterRender3D(event: AfterRender3DEvent) {
        if (mode == Mode.FXAA) {
            FXAAShader.renderMainTarget()
        }
    }

    private fun onGameLeft(event: GameLeftEvent) {
        mc.execute { closeOffscreenTargets() }
    }

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
