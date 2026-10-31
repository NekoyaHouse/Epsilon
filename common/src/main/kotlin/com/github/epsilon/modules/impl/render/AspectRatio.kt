package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

object AspectRatio : Module("Aspect Ratio", Category.RENDER) {

    val ratio by doubleSetting("Ratio", 1.78, 0.1, 8.0, 0.1)

}
