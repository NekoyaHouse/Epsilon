package com.github.epsilon.graphics.schedulers.render2d

import kotlin.math.abs

/** 二维仿射变换；局部坐标先乘线性部分，再加平移。 */
data class Render2DTransform(
    val m00: Float = 1f, val m01: Float = 0f,
    val m10: Float = 0f, val m11: Float = 1f,
    val x: Float = 0f, val y: Float = 0f,
) {
    fun then(child: Render2DTransform) = Render2DTransform(
        m00 * child.m00 + m01 * child.m10, m00 * child.m01 + m01 * child.m11,
        m10 * child.m00 + m11 * child.m10, m10 * child.m01 + m11 * child.m11,
        m00 * child.x + m01 * child.y + x, m10 * child.x + m11 * child.y + y,
    )

    fun mapX(px: Float, py: Float) = m00 * px + m01 * py + x
    fun mapY(px: Float, py: Float) = m10 * px + m11 * py + y
    fun inverse(): Render2DTransform? {
        val det = m00 * m11 - m01 * m10
        if (!det.isFinite() || abs(det) < 1e-8f) return null
        return Render2DTransform(
            m11 / det, -m01 / det, -m10 / det, m00 / det,
            (m01 * y - m11 * x) / det, (m10 * x - m00 * y) / det
        )
    }

    companion object {
        val IDENTITY = Render2DTransform()
        fun translation(x: Float, y: Float) = Render2DTransform(x = x, y = y)
    }
}

data class Render2DProperties(
    val transform: Render2DTransform = Render2DTransform.IDENTITY,
    val red: Float = 1f, val green: Float = 1f, val blue: Float = 1f, val alpha: Float = 1f,
) {
    fun then(child: Render2DProperties) = Render2DProperties(
        transform.then(child.transform),
        red * child.red, green * child.green, blue * child.blue, alpha * child.alpha
    )

    companion object {
        val DEFAULT = Render2DProperties()
    }
}
