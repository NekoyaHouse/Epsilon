package com.github.epsilon.elements.impl.island.instance

import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.elements.impl.island.pattern.impl.CheckPattern
import com.github.epsilon.elements.impl.island.pattern.impl.DelayPattern
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.kernel.time.TimerUtils
import java.awt.Color

/**
 * 岛屿容器中的一屏内容。
 *
 * 每个实例只负责声明自己期望的目标尺寸与绘制内容，容器的形变动画由
 * [com.github.epsilon.elements.impl.island.LandRenderer] 统一插值。
 */
abstract class LandInstance(
    protected val pattern: LCPattern,
    val priority: Int,
) {

    private val timer = TimerUtils()

    var targetRadius: Float = 0f
    var targetWidth: Float = 0f
    var targetHeight: Float = 0f

    open val maxContainerRadius: Float
        get() = Float.POSITIVE_INFINITY

    private var contentAlpha: Float = 1.0f

    /**
     * 每帧刷新实例的数据与目标尺寸。
     */
    abstract fun update()

    /**
     * 绘制实例内容。
     *
     * @param scope  绘制作用域；原点就是容器左上角，因此坐标一律相对容器
     * @param width  容器当前宽度
     * @param height 容器当前高度
     */
    abstract fun draw(scope: UiScope, width: Float, height: Float)

    /**
     * 实例离开控制器时释放自身持有的临时资源。
     */
    open fun onRemoved() {
    }

    fun setContentAlpha(contentAlpha: Float) {
        this.contentAlpha = contentAlpha.coerceIn(0.0f, 1.0f)
    }

    protected fun fade(color: Color): Color {
        if (contentAlpha >= 1.0f) return color
        val alpha = (color.alpha * contentAlpha).toInt().coerceIn(0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

    open val isClosed: Boolean
        get() = checkPattern()

    open fun checkPattern(): Boolean {
        return when (val current = pattern) {
            is CheckPattern -> current.supplier.get()
            is DelayPattern -> timer.passedMillise(current.delay.toLong())
            else -> false
        }
    }

}
