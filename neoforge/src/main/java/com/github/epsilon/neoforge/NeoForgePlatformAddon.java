package com.github.epsilon.neoforge;

import com.github.epsilon.Constants;
import com.github.epsilon.addon.EpsilonAddon;

import java.util.List;

/**
 * Built-in NeoForge addon for NeoForge-only features.
 */
public class NeoForgePlatformAddon extends EpsilonAddon {

    public static final NeoForgePlatformAddon INSTANCE = new NeoForgePlatformAddon();

    private NeoForgePlatformAddon() {
        super("epsilon_neoforge");
    }

    @Override
    public void onSetup() {
        Constants.LOGGER.info("NeoForge platform addon initialized.");
    }

    @Override
    public String getDisplayName() {
        return "NeoForge Platform";
    }

    @Override
    public String getDescription() {
        return "Built-in addon for NeoForge-specific integrations.";
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
