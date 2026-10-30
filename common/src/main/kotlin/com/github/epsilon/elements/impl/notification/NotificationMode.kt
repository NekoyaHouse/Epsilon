package com.github.epsilon.elements.impl.notification

import java.awt.Color

enum class NotificationMode(private val red: Int, private val green: Int, private val blue: Int) {

    Success(33, 207, 178),

    Info(255, 255, 255),

    Warning(255, 204, 0),

    Error(236, 67, 48);

    fun getColor(): Color {
        return getColor(255)
    }

    fun getColor(alpha: Int): Color {
        return Color(red, green, blue, alpha)
    }

    companion object {

        fun fromEnabled(enabled: Boolean): NotificationMode {
            return if (enabled) Success else Error
        }

    }

}
