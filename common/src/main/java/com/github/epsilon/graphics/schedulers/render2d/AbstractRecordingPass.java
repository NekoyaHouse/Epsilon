package com.github.epsilon.graphics.schedulers.render2d;

import com.mojang.renderpearl.api.commands.RenderPass;

/**
 * 录制 pass 只记录绘制和绑定，viewport 由实际的渲染通道决定。
 */
public abstract class AbstractRecordingPass implements RenderPass {
    public final void setViewport(int x, int y, int width, int height) {
        throw new UnsupportedOperationException("录制 pass 不接受 viewport 变更");
    }
}
