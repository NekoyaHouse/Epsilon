package com.github.epsilon.neoforge;

import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.graphics.LuminRenderPipelines;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.neoforged.fml.ModList;

public class EpsilonNeoForge {

    public static void init() {
        if (ModList.get().isLoaded("iris")) {
            IrisApi iris = IrisApi.getInstance();
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_AA, IrisProgram.TEXTURED);
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_NO_AA, IrisProgram.TEXTURED);
        }

        EpsilonCommon.init();
    }

}
