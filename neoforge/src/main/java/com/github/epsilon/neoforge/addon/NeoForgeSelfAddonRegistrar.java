package com.github.epsilon.neoforge.addon;

import com.github.epsilon.neoforge.NeoForgePlatformAddon;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Registers Epsilon's built-in NeoForge addon via NeoForge.EVENT_BUS.
 */
public class NeoForgeSelfAddonRegistrar {

    private static boolean registered;

    private NeoForgeSelfAddonRegistrar() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgeSelfAddonRegistrar::onAddonSetup);
    }

    private static void onAddonSetup(EpsilonAddonSetupEvent event) {
        event.registerAddon(NeoForgePlatformAddon.INSTANCE);
    }

}
