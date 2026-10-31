package com.github.epsilon.neoforge

import com.github.epsilon.EpsilonCommon
import com.github.epsilon.graphics.LuminRenderPipelines
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.api.v0.IrisProgram
import net.neoforged.fml.ModList

object EpsilonNeoForge {

    fun init() {
        if (ModList.get().isLoaded("iris")) {
            val iris = IrisApi.getInstance()
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_AA, IrisProgram.TEXTURED)
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_NO_AA, IrisProgram.TEXTURED)
        }

        EpsilonCommon.init()
    }

}
