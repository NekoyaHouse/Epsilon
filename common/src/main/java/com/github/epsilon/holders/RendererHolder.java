package com.github.epsilon.holders;

import com.github.epsilon.graphics.renderers.IRenderer;

import java.util.ArrayList;
import java.util.List;

public class RendererHolder {

    public static final RendererHolder INSTANCE = new RendererHolder();

    private final List<IRenderer> renderers = new ArrayList<>();

    private RendererHolder() {
    }

    public synchronized <T extends IRenderer> T register(T renderer) {
        renderers.add(renderer);
        return renderer;
    }

    public synchronized void unregister(IRenderer renderer) {
        renderers.remove(renderer);
    }

    public synchronized void destroyAll() {
        for (IRenderer renderer : List.copyOf(renderers)) {
            renderer.close();
        }
        renderers.clear();
    }

}
