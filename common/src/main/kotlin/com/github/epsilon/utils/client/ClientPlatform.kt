package com.github.epsilon.utils.client

import java.util.*

/**
 * 客户端运行平台的统一判定入口。
 *
 * SMTC、视频原生库等能力只在 Windows x86_64 上可用，界面层需要拿到可读的平台名称
 * 用于展示平台不支持提示，因此所有 os.name / os.arch 判断都收敛到这里。
 */
object ClientPlatform {

    enum class Family {
        WINDOWS,
        MACOS,
        LINUX,
        UNKNOWN
    }

    private val FAMILY: Family = detectFamily()
    private val ARCHITECTURE: String = normalizeArchitecture(System.getProperty("os.arch", ""))

    fun family(): Family {
        return FAMILY
    }

    fun architecture(): String {
        return ARCHITECTURE
    }

    fun isWindows(): Boolean {
        return FAMILY == Family.WINDOWS
    }

    fun isWindowsX64(): Boolean {
        return FAMILY == Family.WINDOWS && ARCHITECTURE == "x86_64"
    }

    fun isMacosArm64(): Boolean {
        return FAMILY == Family.MACOS && ARCHITECTURE == "arm64"
    }

    /**
     * 返回用于界面展示的平台名称，例如 {@code Windows x86_64} 或 {@code macOS arm64}。
     */
    fun displayName(): String {
        return familyName(FAMILY) + " " + ARCHITECTURE
    }

    private fun detectFamily(): Family {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        if (osName.contains("windows")) {
            return Family.WINDOWS
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Family.MACOS
        }
        if (osName.contains("linux")) {
            return Family.LINUX
        }
        return Family.UNKNOWN
    }

    private fun normalizeArchitecture(raw: String?): String {
        val arch = raw?.lowercase(Locale.ROOT) ?: ""
        return when (arch) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "arm64"
            "x86", "i386", "i686" -> "x86"
            else -> if (arch.isBlank()) "unknown" else arch
        }
    }

    private fun familyName(family: Family): String {
        return when (family) {
            Family.WINDOWS -> "Windows"
            Family.MACOS -> "macOS"
            Family.LINUX -> "Linux"
            Family.UNKNOWN -> "Unknown OS"
        }
    }

}
