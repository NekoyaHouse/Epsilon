package com.github.epsilon.gui.lib

/**
 * 与具体屏幕和渲染后端无关的矩形值对象。
 */
data class UiRect(val x: Float, val y: Float, val width: Float, val height: Float) {

    init {
        if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite()) {
            throw IllegalArgumentException("UI rectangle values must be finite")
        }
    }

    fun right(): Float {
        return x + width
    }

    fun bottom(): Float {
        return y + height
    }

    fun centerX(): Float {
        return x + width / 2.0f
    }

    fun centerY(): Float {
        return y + height / 2.0f
    }

    fun contains(px: Double, py: Double): Boolean {
        return px >= x && px <= right() && py >= y && py <= bottom()
    }

    fun inset(amount: Float): UiRect {
        return inset(amount, amount)
    }

    fun inset(horizontal: Float, vertical: Float): UiRect {
        return UiRect(
            x + horizontal, y + vertical,
            Math.max(0.0f, width - horizontal * 2.0f),
            Math.max(0.0f, height - vertical * 2.0f)
        )
    }

    fun atOrigin(): UiRect {
        return UiRect(0.0f, 0.0f, width, height)
    }

    fun relativeTo(origin: UiRect): UiRect {
        return UiRect(x - origin.x, y - origin.y, width, height)
    }

    fun translate(deltaX: Float, deltaY: Float): UiRect {
        return UiRect(x + deltaX, y + deltaY, width, height)
    }

    fun intersect(other: UiRect): UiRect? {
        val intersectionX = Math.max(x, other.x)
        val intersectionY = Math.max(y, other.y)
        val intersectionRight = Math.min(right(), other.right())
        val intersectionBottom = Math.min(bottom(), other.bottom())
        if (intersectionRight <= intersectionX || intersectionBottom <= intersectionY) {
            return null
        }
        return UiRect(
            intersectionX, intersectionY,
            intersectionRight - intersectionX, intersectionBottom - intersectionY
        )
    }
}
