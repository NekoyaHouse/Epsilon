package com.github.epsilon.graphics.schedulers.render2d

import java.util.Objects

@JvmRecord
data class Render2DScissor(val x: Int, val y: Int, val width: Int, val height: Int) {

    fun visible(): Boolean = width > 0 && height > 0

    // LuminUiRenderer 以 scissor 作为缓存键，保持与原 Java record 相同的 equals/hashCode 语义。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Render2DScissor) return false
        return x == other.x && y == other.y && width == other.width && height == other.height
    }

    override fun hashCode(): Int = Objects.hash(x, y, width, height)

}
