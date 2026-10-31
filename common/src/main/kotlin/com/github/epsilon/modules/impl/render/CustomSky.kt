package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import java.awt.Color

/**
 * 在世界渲染的天空阶段结束后应用指定的天空着色器。
 */
object CustomSky : Module("CustomSky", Category.RENDER) {

    val shader by enumSetting("Shader", ShaderMode.Cine)
    val speed by doubleSetting("Speed", 4.0, 0.0, 20.0, 0.1)
    val color by colorSetting("Color", Color(255, 255, 255, 200))
    val backgroundColor by colorSetting(
        "Background Color (Local)",
        Color(0, 0, 0, 255),
        dependency = { shader == ShaderMode.Local })

    enum class ShaderMode {
        Cine,
        Euabe,
        Local
    }

}
