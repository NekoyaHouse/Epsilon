package com.github.epsilon.addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EpsilonAddonSetupEvent {

    private final ArrayList<EpsilonAddon> addons = new ArrayList<>();

    public void registerAddon(EpsilonAddon addon) {
        if (addon != null) {
            addons.add(addon);
        }
    }

    public List<EpsilonAddon> getAddons() {
        return Collections.unmodifiableList(addons);
    }

}
