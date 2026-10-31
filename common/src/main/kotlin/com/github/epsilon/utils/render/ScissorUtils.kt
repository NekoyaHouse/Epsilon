package com.github.epsilon.utils.render

import com.github.epsilon.Constants.mc
import com.github.epsilon.graphics.LuminRenderSystem
import com.mojang.renderpearl.api.commands.RenderPass
import net.minecraft.client.renderer.state.WindowRenderState
import kotlin.math.roundToInt

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
        val sx = (x * scale).roundToInt()
        val sy = (framebufferHeight - (y + height) * scale).roundToInt()
        val sw = maxOf(0, (width * scale).roundToInt())
        val sh = maxOf(0, (height * scale).roundToInt())
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
    fun toFramebufferScissor(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        guiHeight: Float
    ): LuminRenderSystem.ScissorRect {
        val scale = LuminRenderSystem.getGuiScale()
        val sx = (x * scale).roundToInt()
        val sy = ((guiHeight - y - height) * scale).roundToInt()
        val sw = maxOf(0, (width * scale).roundToInt())
        val sh = maxOf(0, (height * scale).roundToInt())
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
    fun toFramebufferScissor(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        scale: Double,
        framebufferHeight: Int
    ): LuminRenderSystem.ScissorRect {
        val sx = (x * scale).roundToInt()
        val sy = (framebufferHeight - (y + height) * scale).roundToInt()
        val sw = maxOf(0, (width * scale).roundToInt())
        val sh = maxOf(0, (height * scale).roundToInt())
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
        val left = (x.toLong()).coerceIn(((0).toLong()).toLong(), ((areaWidth).toLong()).toLong()).toInt().toInt()
        val top = (y.toLong()).coerceIn(((0).toLong()).toLong(), ((areaHeight).toLong()).toLong()).toInt().toInt()
        val right =
            ((x + width).toLong()).coerceIn(((0).toLong()).toLong(), ((areaWidth).toLong()).toLong()).toInt().toInt()
        val bottom =
            ((y + height).toLong()).coerceIn(((0).toLong()).toLong(), ((areaHeight).toLong()).toLong()).toInt().toInt()
        return LuminRenderSystem.ScissorRect(left, top, maxOf(0, right - left), maxOf(0, bottom - top))
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

        val windowState: WindowRenderState = mc.gameRenderer.gameRenderState().windowRenderState
        return windowState.width
    }

    private fun getFramebufferHeight(): Int {
        val activeTarget = LuminRenderSystem.getActiveTarget()
        if (activeTarget != null) {
            return activeTarget.height()
        }

        val windowState: WindowRenderState = mc.gameRenderer.gameRenderState().windowRenderState
        return windowState.height
    }

}
