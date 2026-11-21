package com.github.epsilon.kernel.client.platform

import java.io.File
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
    private val IS_ANDROID: Boolean = detectAndroid()

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

    fun isAndroid(): Boolean = IS_ANDROID

    fun androidAbi(): String? {
        if (!IS_ANDROID) return null
        return when (ARCHITECTURE) {
            "arm64" -> "arm64-v8a"
            "arm32" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> null
        }
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
            "arm", "armv7l", "armv7a", "aarch32" -> "arm32"
            "x86", "i386", "i686" -> "x86"
            else -> if (arch.isBlank()) "unknown" else arch
        }
    }

    private fun detectAndroid(): Boolean {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        if (osName.startsWith("windows") || osName.startsWith("mac") || osName.startsWith("darwin")) {
            return false
        }

        val osVersion = System.getProperty("os.version", "").lowercase(Locale.ROOT)
        if (osVersion.contains("android")) {
            return true
        }

        val vmName = System.getProperty("java.vm.name", "").lowercase(Locale.ROOT)
        val vmVendor = System.getProperty("java.vendor", "").lowercase(Locale.ROOT)
        if (vmName.contains("dalvik") || vmVendor.contains("android")) {
            return true
        }

        System.getenv().keys.forEach { key ->
            if (key.startsWith("POJAV", ignoreCase = true)) {
                return true
            }
        }

        if (File("/system/build.prop").isFile) {
            return true
        }

        return false
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
