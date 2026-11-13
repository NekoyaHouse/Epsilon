package com.github.epsilon.elements

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.gui.hudeditor.HudLayoutHelper
import com.github.epsilon.gui.lib.Content
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.modules.Module
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * HUD 元素基类：在 [Module] 的启用/配置能力之上提供锚点、屏幕钳制与 [UiRenderBatch] 提交。
 *
 * 绘制入口拆成两步，顺序不能颠倒：
 * 1. [measure] 只负责测量本帧尺寸并调用 [setBounds]，不产生任何绘制命令，也不推进绘制动画；
 * 2. [drawUi] 在 [UiScope] 上绘制，作用域原点就是元素矩形左上角，因此所有图元坐标都是相对坐标。
 *
 * 这样作用域矩形永远等于本帧最终的 [x] / [y] / [width] / [height]，Center/Right/Bottom 锚点
 * 在尺寸变化的当帧也不会出现「用上一帧矩形绘制」的偏移。
 *
 * 例外：`BlurShader` 等非 [UiScope] 的外部绘制需要绝对屏幕坐标，绘制阶段用 `origin.x + local`
 * 还原；命中测试、anchor 计算与持久化坐标保持绝对语义不变。
 */
abstract class HudModule : Module {

    enum class HorizontalAnchor {
        Left,
        Center,
        Right,
    }

    enum class VerticalAnchor {
        Top,
        Center,
        Bottom,
    }

    var x: Float by mutableStateOf(0f)
    var y: Float by mutableStateOf(0f)
    var width: Float by mutableStateOf(0f)
    var height: Float by mutableStateOf(0f)

    var anchorX: Float by mutableStateOf(0f)
        private set
    var anchorY: Float by mutableStateOf(0f)
        private set

    private val defaultX: Float
    private val defaultY: Float
    private val defaultAnchorX: Float
    private val defaultAnchorY: Float

    var horizontalAnchor: HorizontalAnchor by mutableStateOf(HorizontalAnchor.Left)
        private set
    var verticalAnchor: VerticalAnchor by mutableStateOf(VerticalAnchor.Top)
        private set

    constructor(name: String, width: Float, height: Float) : this(name, 0f, 0f, width, height)

    constructor(name: String, x: Float, y: Float, width: Float, height: Float) : super(name, null) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        this.anchorX = x
        this.anchorY = y
        this.defaultX = x
        this.defaultY = y
        this.defaultAnchorX = x
        this.defaultAnchorY = y
    }

    override fun reset() {
        super.reset()
        resetLayout()
    }

    private fun resetLayout() {
        horizontalAnchor = HorizontalAnchor.Left
        verticalAnchor = VerticalAnchor.Top
        anchorX = defaultAnchorX
        anchorY = defaultAnchorY
        applyRenderPosition(defaultX, defaultY, false)
    }

    /**
     * 按当前锚点与尺寸刷新渲染坐标。
     *
     * 只处理位置，不测量尺寸；尺寸由 [measure] 通过 [setBounds] 更新。
     */
    fun updateLayout() {
        applyRenderPosition(anchoredRenderX, anchoredRenderY, false)
    }

    protected fun setBounds(width: Float, height: Float) {
        val changed = this.width != width || this.height != height
        this.width = width
        this.height = height
        if (changed) {
            applyRenderPosition(anchoredRenderX, anchoredRenderY, false)
        }
    }

    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height

    fun moveTo(x: Float, y: Float) {
        applyRenderPosition(x, y, true)
    }

    fun moveBy(deltaX: Float, deltaY: Float) {
        moveTo(x + deltaX, y + deltaY)
    }

    fun loadLegacyPosition(renderX: Float, renderY: Float) {
        horizontalAnchor = HorizontalAnchor.Left
        verticalAnchor = VerticalAnchor.Top
        anchorX = renderX
        anchorY = renderY
        applyRenderPosition(renderX, renderY, false)
    }

    fun setAnchorState(
        horizontalAnchor: HorizontalAnchor?,
        verticalAnchor: VerticalAnchor?,
        anchorX: Float,
        anchorY: Float,
    ) {
        this.horizontalAnchor = horizontalAnchor ?: HorizontalAnchor.Left
        this.verticalAnchor = verticalAnchor ?: VerticalAnchor.Top
        this.anchorX = anchorX
        this.anchorY = anchorY
        applyRenderPosition(anchoredRenderX, anchoredRenderY, false)
    }

    private fun applyRenderPosition(renderX: Float, renderY: Float, updateAnchors: Boolean) {
        val screenWidth = screenWidth
        val screenHeight = screenHeight
        val clampedX = renderX.coerceIn(0.0f, maxOf(0.0f, screenWidth - width))
        val clampedY = renderY.coerceIn(0.0f, maxOf(0.0f, screenHeight - height))

        if (updateAnchors) {
            horizontalAnchor = HudLayoutHelper.resolveHorizontalAnchor(clampedX, width, screenWidth)
            verticalAnchor = HudLayoutHelper.resolveVerticalAnchor(clampedY, height, screenHeight)
        }

        this.x = clampedX
        this.y = clampedY
        if (updateAnchors) {
            anchorX = HudLayoutHelper.toAnchorX(horizontalAnchor, clampedX, width, screenWidth)
            anchorY = HudLayoutHelper.toAnchorY(verticalAnchor, clampedY, height, screenHeight)
        }
    }

    private val anchoredRenderX: Float
        get() = HudLayoutHelper.getRenderX(horizontalAnchor, anchorX, width, screenWidth)

    private val anchoredRenderY: Float
        get() = HudLayoutHelper.getRenderY(verticalAnchor, anchorY, height, screenHeight)

    private val screenWidth: Int
        get() = if (mc.window == null) 0 else LuminRenderSystem.getScaledWidthInt()

    private val screenHeight: Int
        get() = if (mc.window == null) 0 else LuminRenderSystem.getScaledHeightInt()

    /**
     * 布局阶段：测量本帧尺寸并调用 [setBounds]，不得绘制。
     *
     * 该阶段会在 [renderWithBatch] 建立作用域之前执行，保证作用域原点与本帧最终矩形一致。
     */
    protected abstract fun measure(deltaTracker: DeltaTracker)

    /**
     * 绘制阶段：作用域原点等于元素矩形左上角，坐标一律相对它。
     *
     * 非 [UiScope] 的外部绘制（如 `BlurShader`）用 `origin.x + local` 还原绝对坐标。
     */
    protected abstract fun UiScope.drawUi(deltaTracker: DeltaTracker)

    fun renderWithBatch(deltaTracker: DeltaTracker, renderBatch: UiRenderBatch) {
        // 先布局、后开作用域：作用域矩形必须等于本帧最终的 x/y/width/height。
        measure(deltaTracker)
        renderBatch.Content(UiRect(x, y, width, height)) {
            drawUi(deltaTracker)
        }
    }

    open fun renderOverlay(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
    }

}
