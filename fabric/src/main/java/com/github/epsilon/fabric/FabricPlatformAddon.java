package com.github.epsilon.fabric;

import com.github.epsilon.Constants;
import com.github.epsilon.addon.EpsilonAddon;

import java.util.List;

/**
 * Built-in Fabric addon for Fabric-only features.
 */
public class FabricPlatformAddon extends EpsilonAddon {

    public FabricPlatformAddon() {
        super("epsilon_fabric");
    }

    @Override
    public void onSetup() {
        Constants.LOGGER.info("Fabric platform addon initialized.");
    }

    @Override
    public String getDisplayName() {
        return "Fabric Platform";
    }

    @Override
    public String getDescription() {
        return "Built-in addon for Fabric-specific integrations.";
    }

    @Override
    public String getVersion() {
        return Constants.VERSION;
    }

    @Override
    public List<String> getAuthors() {
        return List.of("slmpc", "06789");
    }

}
