package com.github.epsilon.utils.client;

import net.minecraft.client.ResourceLoadStateTracker;

import static com.github.epsilon.Constants.mc;

public class ClientUtils {

    public static boolean isLoading() {
        ResourceLoadStateTracker.ReloadState state = mc.reloadStateTracker.reloadState;
        return state == null || !state.finished;
    }

}
