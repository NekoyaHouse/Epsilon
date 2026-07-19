package com.github.epsilon.neoforge;

import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.neoforge.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.neoforge.addon.NeoForgeSelfAddonRegistrar;
import net.neoforged.neoforge.common.NeoForge;

public class EpsilonNeoForge {

    private EpsilonNeoForge() {
        init();
    }

    public static EpsilonNeoForge newInstance() {
        return new EpsilonNeoForge();
    }

    private void init() {
        NeoForgeSelfAddonRegistrar.register();

        EpsilonAddonSetupEvent addonEvent = NeoForge.EVENT_BUS.post(new EpsilonAddonSetupEvent());
        AddonBootstrap.registerAddons(addonEvent.getAddons());

        EpsilonCommon.init();
    }

}
