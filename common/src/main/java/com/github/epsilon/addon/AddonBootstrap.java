package com.github.epsilon.addon;

import com.github.epsilon.holders.AddonHolder;

/**
 * Shared addon bootstrap utility used by multiple loaders.
 */
public class AddonBootstrap {

    private AddonBootstrap() {
    }

    public static void registerAddons(EpsilonAddonSetupEvent addonEvent) {
        if (addonEvent != null) {
            registerAddons(addonEvent.getAddons());
        }
    }

    public static void registerAddons(Iterable<EpsilonAddon> addons) {
        AddonHolder.INSTANCE.registerAddons(addons);
    }

    public static void setupAddons(EpsilonAddonSetupEvent addonEvent) {
        registerAddons(addonEvent);
        AddonHolder.INSTANCE.setupAddons();
    }

    public static void setupAddons(Iterable<EpsilonAddon> addons) {
        registerAddons(addons);
        AddonHolder.INSTANCE.setupAddons();
    }

}
