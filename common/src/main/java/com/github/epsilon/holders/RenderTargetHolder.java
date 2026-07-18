package com.github.epsilon.holders;

import com.github.epsilon.graphics.LuminRenderSystem;

import java.util.ArrayList;
import java.util.List;

public class RenderTargetHolder {

    public static final RenderTargetHolder INSTANCE = new RenderTargetHolder();

    private final List<LuminRenderSystem.LuminRenderTarget> targets = new ArrayList<>();

    private RenderTargetHolder() {
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

