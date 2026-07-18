package com.github.epsilon.gui.lib.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTheme;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.UiScrollBar;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 可裁剪 UI 视口的内容缓冲。
 * <p>
 * 内容先写入一个 scheduler-backed 批次，flush 时再统一附加 scissor 和滚动条。
 * 这样列表、设置页、popup 视口不再各自维护一组 GPU renderer，能和主 GUI 一起进入 Render2DScheduler 合批。
 */
public final class UiContentBuffer implements AutoCloseable {

    private final UiTheme theme;
    private final UiRenderBatch batch;
    private final UiRenderBatch scrollBarBatch;
    private final UiRenderBatch marqueeBatch;
    private final UiScrollBar scrollBar;
    private final List<MarqueeTextDraw> marqueeDraws = new ArrayList<>();

    private boolean pending;
    private UiRect pendingViewport;

    public UiContentBuffer(UiTheme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.batch = new UiRenderBatch(theme);
        this.scrollBarBatch = batch.view(8);
        this.marqueeBatch = batch.view(9);
        this.scrollBar = new UiScrollBar(theme);
    }

    public UiTheme theme() {
        return theme;
    }

    public Render2DScheduler.LayerHandle contentLayer() {
        return batch.layerHandle(0);
    }

    public TextRenderer textMetrics() {
        return batch.scheduler().textMetrics();
    }

    public void clear() {
        clearContent();
        marqueeDraws.clear();
        pending = false;
        pendingViewport = null;
    }

    public void addMarqueeText(MarqueeTextDraw draw) {
        marqueeDraws.add(draw);
    }

    private void clearContent() {
        batch.clear();
    }

    /**
     * 记录本帧视口信息，并把已有内容层绑定到对应 framebuffer scissor。
     */
    public void beginViewport(UiRect viewport) {
        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        batch.layerHandle(0).setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
    }

    /**
     * 记录本帧视口信息，并为滚动条与跑马灯文本准备本帧附加层。
     */
    public void queueViewport(UiRect viewport, float scroll, float maxScroll, float contentHeight) {
        queueViewport(viewport, scroll, maxScroll, contentHeight, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public void queueViewport(UiRect viewport, float scroll, float maxScroll,
                              float contentHeight, int mouseX, int mouseY) {
        beginViewport(viewport);
        scrollBarBatch.clear();
        scrollBarBatch.render(UiTree.build(scope -> scrollBar.draw(scope, viewport, scroll, maxScroll, contentHeight, mouseX, mouseY)));
        pendingViewport = viewport;
        pending = true;
    }

    public void flush() {
        if (!pending) {
            return;
        }
        flushMarqueeTexts();
        batch.flush();
        scrollBarBatch.clear();
        marqueeBatch.clear();
        pending = false;
    }

    private void flushMarqueeTexts() {
        marqueeBatch.clear();
        if (marqueeDraws.isEmpty() || pendingViewport == null) {
            marqueeDraws.clear();
            return;
        }
        for (MarqueeTextDraw draw : marqueeDraws) {
            // 跑马灯文本有自己的窄裁剪区，需要与 viewport 求交后单独写入文本层。
            UiRect clip = draw.clip().intersect(pendingViewport);
            if (clip == null) {
                continue;
            }
            LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(clip.x(), clip.y(), clip.width(), clip.height());
            if (scissor.width() <= 0 || scissor.height() <= 0) {
                continue;
            }
            marqueeBatch.layerHandle(0).setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            marqueeBatch.render(UiTree.build(scope -> {
                if (draw.font() != null) {
                    scope.text(draw.text(), draw.x(), draw.y(), draw.scale(), draw.color(), draw.font());
                } else {
                    scope.text(draw.text(), draw.x(), draw.y(), draw.scale(), draw.color());
                }
            }));
        }
        marqueeDraws.clear();
    }

    public record MarqueeTextDraw(String text, float x, float y, float scale, Color color,
                                  TtfFontLoader font, UiRect clip) {
    }

    public void flushAndClear() {
        flush();
        clearContent();
    }

    @Override
    public void close() {
        clear();
        batch.close();
    }

}
