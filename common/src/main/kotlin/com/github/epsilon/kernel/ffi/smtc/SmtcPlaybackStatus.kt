package com.github.epsilon.kernel.ffi.smtc

enum class SmtcPlaybackStatus {

    Closed,
    Opened,
    Changing,
    Stopped,
    Playing,
    Paused,
    Unknown,
    ;

    companion object {

        /** 将 Windows SMTC 播放状态枚举值转换为本地枚举；未知值统一回退为 [Unknown]。 */
        @JvmStatic
        fun fromNative(value: Int): SmtcPlaybackStatus {
            if (value < 0 || value >= Unknown.ordinal) return Unknown
            return entries[value]
        }

    }

}
