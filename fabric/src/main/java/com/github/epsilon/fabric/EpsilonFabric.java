package com.github.epsilon.fabric;

import com.github.epsilon.Constants;
import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.assets.i18n.LanguageReloadListener;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.fabric.addon.FabricEpsilonAddonEntrypoint;
import com.github.epsilon.graphics.LuminRenderPipelines;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.minecraft.server.packs.PackType;

public class EpsilonFabric {

    public static final String ADDON_ENTRYPOINT_KEY = "epsilon:addon";

    public static void init() {
        EpsilonAddonSetupEvent addonEvent = new EpsilonAddonSetupEvent();
        for (EntrypointContainer<FabricEpsilonAddonEntrypoint> container : FabricLoader.getInstance().getEntrypointContainers(ADDON_ENTRYPOINT_KEY, FabricEpsilonAddonEntrypoint.class)) {
            String providerId = container.getProvider().getMetadata().getId();
            try {
                FabricEpsilonAddonEntrypoint entrypoint = container.getEntrypoint();
                entrypoint.registerAddon(addonEvent);
            } catch (Throwable t) {
                Constants.LOGGER.error("Failed to register addon entrypoint from mod: {}", providerId, t);
            }
        }
        AddonBootstrap.registerAddons(addonEvent);

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
