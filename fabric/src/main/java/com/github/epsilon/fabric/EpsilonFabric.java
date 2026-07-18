package com.github.epsilon.fabric;

import com.github.epsilon.Constants;
import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.assets.i18n.LanguageReloadListener;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.fabric.addon.FabricEpsilonAddonEntrypoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.server.packs.PackType;

public class EpsilonFabric implements ClientModInitializer {

    public static final String ADDON_ENTRYPOINT_KEY = "epsilon:addon";

    @Override
    public void onInitializeClient() {
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

        EpsilonCommon.init();

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                ResourceLocationUtils.getIdentifier("objects/reload_listener"),
                new LanguageReloadListener()
        );
    }

}
