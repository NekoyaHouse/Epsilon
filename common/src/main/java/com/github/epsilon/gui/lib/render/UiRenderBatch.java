package com.github.epsilon.gui.lib.render;

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.gui.lib.UiTheme;
import com.github.epsilon.gui.lib.UiTree;

import java.util.*;

/**
 * UI 树到 Lumin 2D 调度器之间的批次视图。
 * <p>
 * 上层只提交 {@link UiTree}，本类负责把树写入共享的 layer/scheduler 管线，并维护局部视图触碰过的 layer。
 */
public final class UiRenderBatch implements AutoCloseable {

    private final Render2DScheduler scheduler;
    private final boolean ownsScheduler;
    private final int baseLayer;
    private final UiTheme theme;
    private final Set<Integer> touchedLayers = new HashSet<>();

    public UiRenderBatch(UiTheme theme) {
        this(new Render2DScheduler(), true, 0, theme);
    }

    public UiRenderBatch(Render2DScheduler scheduler, int baseLayer, UiTheme theme) {
        this(scheduler, false, baseLayer, theme);
    }

    private UiRenderBatch(Render2DScheduler scheduler, boolean ownsScheduler, int baseLayer, UiTheme theme) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
        this.baseLayer = baseLayer;
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    public UiRenderBatch view(int relativeBaseLayer) {
        return new UiRenderBatch(scheduler, false, baseLayer + relativeBaseLayer, theme);
    }

    public Render2DScheduler scheduler() {
        return scheduler;
    }

    public int baseLayer() {
        return baseLayer;
    }

    public UiTheme theme() {
        return theme;
    }

    public boolean ownsScheduler() {
        return ownsScheduler;
    }

    /**
     * 返回相对当前 batch 的 layer 句柄。
     * <p>
     * 该入口只用于底层编译器和少量需要设置 scissor 的容器，业务 UI 应优先提交 {@link UiTree}。
     */
    public Render2DScheduler.LayerHandle layerHandle(int relativeLayer) {
        return absoluteLayer(baseLayer + relativeLayer);
    }

    Render2DScheduler.LayerHandle absoluteLayer(int layer) {
        touchedLayers.add(layer);
        return scheduler.layer(layer);
    }

    public void render(UiTree tree) {
        render(tree, 0);
    }

    public void render(UiTree tree, int baseLayer) {
        LuminUiRenderer.renderIntoLayeredBatch(tree, this, this.baseLayer + baseLayer);
    }

    public void flush() {
        if (ownsScheduler) {
            scheduler.flush();
            return;
        }
        for (int layer : sortedTouchedLayers()) {
            scheduler.flushLayer(layer);
        }
    }

    public void clear() {
        if (ownsScheduler) {
            scheduler.clear();
            return;
        }
        for (int layer : sortedTouchedLayers()) {
            scheduler.clearLayer(layer);
        }
        touchedLayers.clear();
    }

    public void flushAndClear() {
        flush();
        clear();
    }

    @Override
    public void close() {
        if (ownsScheduler) {
            scheduler.close();
        }
    }

    private List<Integer> sortedTouchedLayers() {
        List<Integer> layers = new ArrayList<>(touchedLayers);
        Collections.sort(layers);
        return layers;
    }

}
