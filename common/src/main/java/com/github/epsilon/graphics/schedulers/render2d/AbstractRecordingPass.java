package com.github.epsilon.graphics.schedulers.render2d;

import com.mojang.renderpearl.api.commands.RenderPass;

/**
 * NeoForge 的 RenderPass 增加了 viewport 方法，Java 基类同时兼容两端接口。
 */
public abstract class AbstractRecordingPass implements RenderPass {
    public final void setViewport(int x, int y, int width, int height) {
        throw new UnsupportedOperationException("录制 pass 不接受 viewport 变更");
    }
}
