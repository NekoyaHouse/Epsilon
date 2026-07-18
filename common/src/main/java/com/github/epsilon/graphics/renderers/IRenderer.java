package com.github.epsilon.graphics.renderers;

import com.mojang.blaze3d.systems.RenderPass;

public interface IRenderer {

    void draw();

    /**
     * 在共享 RenderPass 打开前完成所有缓冲映射、uniform 写入和纹理准备。
     */
    default boolean prepareSharedDraw() {
        return false;
    }

    /**
     * 写入已经绑定好 pipeline 的 RenderPass，只允许绑定状态并提交 draw。
     */
    default void draw(RenderPass pass) {
    }

    /**
     * 一帧内 在 clear() 之后 不能再进行 draw() / drawAndClear()
     */
    void clear();

    /**
     * 一帧内 在 drawAndClear() 之后 不能再进行 draw() / drawAndClear()
     */
    default void drawAndClear() {
        draw();
        clear();
    }

    void close();

}
