package com.github.epsilon.managers;

import com.github.epsilon.graphics.LuminRenderSystem;

import java.util.ArrayList;
import java.util.List;

public class RenderTargetManager {

    public static final RenderTargetManager INSTANCE = new RenderTargetManager();

    private final List<LuminRenderSystem.LuminRenderTarget> targets = new ArrayList<>();

    private RenderTargetManager() {
    }

    public synchronized LuminRenderSystem.LuminRenderTarget register(LuminRenderSystem.LuminRenderTarget target) {
        targets.add(target);
        return target;
    }

    public synchronized void unregister(LuminRenderSystem.LuminRenderTarget target) {
        targets.remove(target);
    }

    public synchronized void destroyAll() {
        for (final var target : List.copyOf(targets)) {
            target.close();
        }
        targets.clear();
    }

}

