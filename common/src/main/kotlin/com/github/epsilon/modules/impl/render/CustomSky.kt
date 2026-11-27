package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import java.awt.Color

object CustomSky : Module("Custom Sky", Category.RENDER) {

    val shader by enumSetting("Shader", ShaderMode.Cine)
    val speed by doubleSetting("Speed", 4.0, 0.0..20.0, 0.1)
    val color by colorSetting("Color", Color(255, 255, 255, 200))
    val backgroundColor by colorSetting(
        "Background Color (Local)",
        Color(0, 0, 0, 255), true, { shader == ShaderMode.Local })

    enum class ShaderMode {
        Cine,
        Euabe,
        Local
    }

}
