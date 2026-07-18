package com.github.epsilon.fabric.addon;

import com.github.epsilon.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.fabric.FabricPlatformAddon;

/**
 * Registers Epsilon's built-in Fabric addon through Fabric custom entrypoint.
 */
public class FabricSelfAddonEntrypoint implements FabricEpsilonAddonEntrypoint {

    @Override
    public void registerAddon(EpsilonAddonSetupEvent event) {
        event.registerAddon(new FabricPlatformAddon());
    }

}

