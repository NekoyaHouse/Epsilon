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
     * 清理尚未绘制的内容；独立 renderer 同帧多次重建可能分配额外缓冲。
     * 2D scheduler 仅丢弃待提交记录，持久缓存通过 resetCache 独立释放。
     */
    fun clear()

    /**
     * 绘制并清理；2D scheduler 的 draw 本身已消费当前记录，可同帧继续提交。
     */
    fun drawAndClear() {
        draw()
        clear()
    }

    fun close()

}
