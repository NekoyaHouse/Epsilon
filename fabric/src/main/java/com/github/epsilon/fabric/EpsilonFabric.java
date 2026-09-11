package com.github.epsilon.fabric;

import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.assets.i18n.LanguageReloadListener;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderPipelines;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.minecraft.server.packs.PackType;

public class EpsilonFabric {

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            IrisApi iris = IrisApi.getInstance();
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_AA, IrisProgram.TEXTURED);
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_NO_AA, IrisProgram.TEXTURED);
        }

        EpsilonCommon.init();

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                ResourceLocationUtils.getIdentifier("objects/reload_listener"),
                new LanguageReloadListener()
        );
    }

}
