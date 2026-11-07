package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.UiScope

interface DropdownPanel {

    val id: String

    fun startIntro()

    fun beginRenderFrame(frameId: Int) {
    }

    val introValue: Float

    /**
     * 绘制面板背景与标题栏。
     *
     * 作用域通过 context 参数传入：调用方在 `UiScene.LuminUIScene(...) { }` 等
     * [UiScope] 作用域内直接写 `panel.drawBackground()`，坐标相对作用域原点。
     */
    context(scope: UiScope)
    fun drawBackground()

    /**
     * 绘制面板内容区（含滚动条）。
     *
     * 命中测试使用屏幕绝对坐标，因此 [mouseX] / [mouseY] 保持绝对坐标不变。
     */
    context(scope: UiScope)
    fun drawContent(mouseX: Int, mouseY: Int)

    val contentClipY: Float

    val contentClipHeight: Float

    fun requiresContentScissor(): Boolean

    val panelHeight: Float

    fun setPosition(x: Float, y: Float)

    fun updateMaxPanelHeight(maxPanelHeight: Float)

    /** 展开/收起动画进度；宿主布局观察它以刷新几何。 */
    val expandProgress: Float

    /** 内容区当前的滚动偏移。 */
    val scrollOffset: Float

    /** 平滑滚动尚未到达的目标偏移。 */
    val targetScrollOffset: Float

    /** 当前最大滚动距离。 */
    val maxScrollOffset: Float

    /**
     * 内容层的 recompose 失效键。
     *
     * 覆盖滚动/展开等几何量、设置值等静止也会变化的状态，以及动画进行中的逐帧时间戳。
     */

    val x: Float

    val y: Float

    val width: Float

    var isOpened: Boolean

    var isVisible: Boolean

}
