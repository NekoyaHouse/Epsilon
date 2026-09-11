package com.github.epsilon.neoforge;

import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.neoforge.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.neoforge.addon.NeoForgeSelfAddonRegistrar;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

public class EpsilonNeoForge {

    public static void init() {
        NeoForgeSelfAddonRegistrar.register();

        EpsilonAddonSetupEvent addonEvent = NeoForge.EVENT_BUS.post(new EpsilonAddonSetupEvent());
        AddonBootstrap.registerAddons(addonEvent.getAddons());

        if (ModList.get().isLoaded("iris")) {
            IrisApi iris = IrisApi.getInstance();
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_AA, IrisProgram.TEXTURED);
            iris.assignPipeline(LuminRenderPipelines.TTF_FONT_NO_AA, IrisProgram.TEXTURED);
        }

        EpsilonCommon.init();
    }

}
