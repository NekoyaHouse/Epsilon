package com.github.epsilon.fabric.addon;

import com.github.epsilon.addon.EpsilonAddonSetupEvent;

/**
 * Custom Fabric entrypoint contract for Epsilon addons.
 */
public interface FabricEpsilonAddonEntrypoint {

    void registerAddon(EpsilonAddonSetupEvent event);

}

