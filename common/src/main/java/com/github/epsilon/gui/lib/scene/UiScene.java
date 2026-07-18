package com.github.epsilon.gui.lib.scene;

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.gui.lib.UiTheme;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;

import java.util.Objects;

/**
 * 单帧 GUI 渲染场景。
 * <p>
 * 一个 scene 拥有一个 {@link Render2DScheduler}。Panel、Dropdown、popup 只向 scene
 * 提交 UI 树，最后由 scene 统一 flush，从而减少 scheduler/buffer 的创建和帧内 draw 次数。
 */
public final class UiScene implements AutoCloseable {

    private final Render2DScheduler scheduler = new Render2DScheduler();
    private final UiLayerStack layers = new UiLayerStack();
    private final UiTheme theme;

    public UiScene(UiTheme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    public void beginFrame() {
        // 每个 Screen/HUD editor 帧从一个干净的命令流开始，具体 renderer 由 scheduler 池化复用。
        scheduler.clear();
    }

    public UiRenderBatch batch(UiLayer layer) {
        return new UiRenderBatch(scheduler, layers.resolve(layer), theme);
    }

    public UiRenderBatch batch(UiLayer layer, int relativeLayer) {
        // 返回同一个 scheduler 的局部视图，调用方只通过 UI 树写入自己的 base layer。
        return new UiRenderBatch(scheduler, layers.resolve(layer, relativeLayer), theme);
    }

    public void submit(UiLayer layer, UiTree tree) {
        batch(layer).render(tree);
    }

    public void submit(UiLayer layer, int relativeLayer, UiTree tree) {
        batch(layer, relativeLayer).render(tree);
    }

    public void flush() {
        scheduler.flush();
    }

    public void flushAndClear() {
        scheduler.flushAndClear();
    }

    public void clear() {
        scheduler.clear();
    }

    public void endFrame() {
        flush();
        scheduler.clear();
    }

    public Render2DScheduler scheduler() {
        return scheduler;
    }

    public UiLayerStack layers() {
        return layers;
    }

    @Override
    public void close() {
        scheduler.close();
    }
}
