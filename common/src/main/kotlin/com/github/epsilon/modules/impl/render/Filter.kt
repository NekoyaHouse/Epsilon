package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.settings.getValue
import com.github.epsilon.events.impl.AfterRender3DEvent
import com.github.epsilon.graphics.shaders.FilterShader
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.EnumSetting
import java.awt.Color

object Filter : Module("Filter", Category.RENDER) {

    init {
        handler<AfterRender3DEvent>(EventPriority.LOW) { onAfterRender3D(it) }
    }

    private enum class Mode {
        Shader,
        LightMap
    }

    private val mode by enumSetting("Mode", Mode.Shader)
    private val baseColor by colorSetting("Base Color", Color(70, 70, 150, 50), true)

    fun isLightMapMode(): Boolean = isEnabled && mode == Mode.LightMap

    fun getLightMapColor(): Color = Color(baseColor.red, baseColor.green, baseColor.blue, 255)

    private fun onAfterRender3D(event: AfterRender3DEvent) {
        if (mode == Mode.Shader) {
            val color = baseColor
            if (color.alpha <= 0) return
            FilterShader.renderToMainTarget(color)
        }
    }

}
