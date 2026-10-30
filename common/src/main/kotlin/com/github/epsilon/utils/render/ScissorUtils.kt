package com.github.epsilon.utils.render

import com.github.epsilon.Constants
import com.github.epsilon.graphics.LuminRenderSystem
import com.mojang.renderpearl.api.commands.RenderPass
import net.minecraft.client.renderer.state.WindowRenderState

object ScissorUtils {

    /**
     * 将 GUI 坐标矩形转换为帧缓冲裁剪矩形。
     *
     * @param x      X 坐标
     * @param y      Y 坐标
     * @param width  宽度
     * @param height 高度
     * @return 操作结果
     */
    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float): LuminRenderSystem.ScissorRect {
        val scale = LuminRenderSystem.getGuiScale()
        val framebufferHeight = getFramebufferHeight()
        val sx = Math.round(x * scale).toInt()
        val sy = Math.round(framebufferHeight - (y + height) * scale).toInt()
        val sw = Math.max(0, Math.round(width * scale).toInt())
        val sh = Math.max(0, Math.round(height * scale).toInt())
        return clampFramebufferScissor(sx, sy, sw, sh)
    }

    /**
     * 将 GUI 坐标矩形转换为帧缓冲裁剪矩形。
     *
     * @param x         X 坐标
     * @param y         Y 坐标
     * @param width     宽度
     * @param height    高度
     * @param guiHeight GUI 坐标系高度
     * @return 操作结果
     */
    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float, guiHeight: Float): LuminRenderSystem.ScissorRect {
        val scale = LuminRenderSystem.getGuiScale()
        val sx = Math.round(x * scale).toInt()
        val sy = Math.round((guiHeight - y - height) * scale).toInt()
        val sw = Math.max(0, Math.round(width * scale).toInt())
        val sh = Math.max(0, Math.round(height * scale).toInt())
        return clampFramebufferScissor(sx, sy, sw, sh)
    }

    /**
     * 将 GUI 坐标矩形转换为帧缓冲裁剪矩形。
     *
     * @param x                 X 坐标
     * @param y                 Y 坐标
     * @param width             宽度
     * @param height            高度
     * @param scale             缩放值或 GUI 到帧缓冲的比例
     * @param framebufferHeight 帧缓冲高度
     * @return 操作结果
     */
    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float, scale: Double, framebufferHeight: Int): LuminRenderSystem.ScissorRect {
        val sx = Math.round(x * scale).toInt()
        val sy = Math.round(framebufferHeight - (y + height) * scale).toInt()
        val sw = Math.max(0, Math.round(width * scale).toInt())
        val sh = Math.max(0, Math.round(height * scale).toInt())
        return clampFramebufferScissor(sx, sy, sw, sh)
    }

    /**
     * 将裁剪矩形限制在当前帧缓冲范围内。
     *
     * @param x      X 坐标
     * @param y      Y 坐标
     * @param width  宽度
     * @param height 高度
     * @return 操作结果
     */
    fun clampFramebufferScissor(x: Int, y: Int, width: Int, height: Int): LuminRenderSystem.ScissorRect {
        val areaWidth = getFramebufferWidth()
        val areaHeight = getFramebufferHeight()
        val left = Math.clamp(x.toLong(), 0, areaWidth)
        val top = Math.clamp(y.toLong(), 0, areaHeight)
        val right = Math.clamp((x + width).toLong(), 0, areaWidth)
        val bottom = Math.clamp((y + height).toLong(), 0, areaHeight)
        return LuminRenderSystem.ScissorRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top))
    }

    /**
     * 判断裁剪区域是否具有可见面积。
     *
     * @param scissor 帧缓冲裁剪矩形
     * @return 判断结果
     */
    fun isVisible(scissor: LuminRenderSystem.ScissorRect): Boolean {
        return isVisible(scissor.width, scissor.height)
    }

    /**
     * 判断裁剪区域是否具有可见面积。
     *
     * @param width  宽度
     * @param height 高度
     * @return 判断结果
     */
    fun isVisible(width: Int, height: Int): Boolean {
        return width > 0 && height > 0
    }

    /**
     * 在渲染通道中启用非空裁剪区域。
     *
     * @param pass   渲染通道
     * @param x      X 坐标
     * @param y      Y 坐标
     * @param width  宽度
     * @param height 高度
     * @return 判断结果
     */
    fun enableScissor(pass: RenderPass, x: Int, y: Int, width: Int, height: Int): Boolean {
        if (!isVisible(width, height)) {
            return false
        }

        pass.enableScissor(x, y, width, height)
        return true
    }

    /**
     * 在渲染通道中启用非空裁剪区域。
     *
     * @param pass    渲染通道
     * @param scissor 帧缓冲裁剪矩形
     * @return 判断结果
     */
    fun enableScissor(pass: RenderPass, scissor: LuminRenderSystem.ScissorRect): Boolean {
        return enableScissor(pass, scissor.x, scissor.y, scissor.width, scissor.height)
    }

    private fun getFramebufferWidth(): Int {
        val activeTarget = LuminRenderSystem.getActiveTarget()
        if (activeTarget != null) {
            return activeTarget.width()
        }

        val windowState: WindowRenderState = Constants.mc.gameRenderer.gameRenderState().windowRenderState
        return windowState.width
    }

    private fun getFramebufferHeight(): Int {
        val activeTarget = LuminRenderSystem.getActiveTarget()
        if (activeTarget != null) {
            return activeTarget.height()
        }

        val windowState: WindowRenderState = Constants.mc.gameRenderer.gameRenderState().windowRenderState
        return windowState.height
    }

}
