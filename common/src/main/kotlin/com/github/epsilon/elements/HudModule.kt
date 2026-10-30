package com.github.epsilon.elements

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.gui.hudeditor.HudLayoutHelper
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.modules.Module
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth

/**
 * HUD 元素基类：在 [Module] 的启用/配置能力之上提供锚点、屏幕钳制与 [UiRenderBatch] 提交。
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

    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 0f
    var height: Float = 0f

    var anchorX: Float
        private set
    var anchorY: Float
        private set

    private val defaultX: Float
    private val defaultY: Float
    private val defaultAnchorX: Float
    private val defaultAnchorY: Float

    var horizontalAnchor: HorizontalAnchor = HorizontalAnchor.Left
        private set
    var verticalAnchor: VerticalAnchor = VerticalAnchor.Top
        private set
    private var currentRenderScope: UiTree.Scope? = null

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
        val clampedX = Mth.clamp(renderX, 0.0f, maxOf(0.0f, screenWidth - width))
        val clampedY = Mth.clamp(renderY, 0.0f, maxOf(0.0f, screenHeight - height))

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

    fun renderWithBatch(deltaTracker: DeltaTracker, renderBatch: UiRenderBatch) {
        val previous = currentRenderScope
        val scope = UiTree.Scope()
        currentRenderScope = scope
        render(deltaTracker)
        currentRenderScope = previous
        renderBatch.render(UiTree.from(scope))
    }

    protected fun renderScope(): UiTree.Scope =
        currentRenderScope ?: throw IllegalStateException("HUD elements must render through renderWithBatch.")

    abstract fun render(deltaTracker: DeltaTracker)

    open fun renderOverlay(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
    }

}
