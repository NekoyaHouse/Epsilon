package com.github.epsilon.graphics.renderers

import com.mojang.renderpearl.api.commands.RenderPass

interface IRenderer {

    fun draw()

    /**
     * 在共享 RenderPass 打开前完成所有缓冲映射、uniform 写入和纹理准备。
     */
    fun prepareSharedDraw(): Boolean = false

    /**
     * 写入已经绑定好 pipeline 的 RenderPass，只允许绑定状态并提交 draw。
     */
    fun draw(pass: RenderPass) {
    }

    /**
     * 一帧内 在 clear() 之后 不能再进行 draw() / drawAndClear()
     */
    fun clear()

    /**
     * 一帧内 在 drawAndClear() 之后 不能再进行 draw() / drawAndClear()
     */
    fun drawAndClear() {
        draw()
        clear()
    }

    fun close()

}
