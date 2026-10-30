package com.github.epsilon.graphics.schedulers.render2d

data class Render2DBounds(val x: Float, val y: Float, val width: Float, val height: Float) {

    fun right(): Float = x + width

    fun bottom(): Float = y + height

    fun centerX(): Float = x + width * 0.5f

    fun centerY(): Float = y + height * 0.5f

    fun intersects(other: Render2DBounds): Boolean {
        if (width <= 0.0f || height <= 0.0f || other.width <= 0.0f || other.height <= 0.0f) {
            return x >= other.x && x <= other.right() && y >= other.y && y <= other.bottom()
        }
        return right() > other.x && x < other.right() && bottom() > other.y && y < other.bottom()
    }

    fun contains(other: Render2DBounds): Boolean =
        other.x >= x && other.right() <= right() && other.y >= y && other.bottom() <= bottom()

    fun include(other: Render2DBounds): Render2DBounds {
        val nx = Math.min(x, other.x)
        val ny = Math.min(y, other.y)
        val nr = Math.max(right(), other.right())
        val nb = Math.max(bottom(), other.bottom())
        return Render2DBounds(nx, ny, Math.max(0.0f, nr - nx), Math.max(0.0f, nb - ny))
    }

    companion object {
        fun of(x: Float, y: Float, width: Float, height: Float): Render2DBounds =
            Render2DBounds(x, y, Math.max(0.0f, width), Math.max(0.0f, height))

        fun point(x: Float, y: Float): Render2DBounds = Render2DBounds(x, y, 0.0f, 0.0f)
    }

}
