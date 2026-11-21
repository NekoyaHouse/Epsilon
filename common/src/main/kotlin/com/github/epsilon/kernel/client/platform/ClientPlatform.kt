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

    /**
     * 是否运行在安卓环境（FCL / ZL2 / Pojav 等手机启动器）。
     *
     * 注意：手机启动器会把 [os.name] 覆盖成 `Linux`，因此**不能**用 Family==LINUX 判定安卓，
     * 必须依靠下列安卓专属信号交叉确认，避免把桌面 Linux 误判成安卓。
     */
    fun isAndroid(): Boolean = IS_ANDROID

    /**
     * 安卓环境对应的 ABI 目录名（与 FCL/ZL2 natives 目录命名一致）；非安卓返回 null。
     */
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

    /**
     * 交叉确认安卓环境。任一安卓专属信号命中且未命中桌面系统即判定为安卓；
     * 全部信号都只在安卓启动器/设备上出现，桌面 Linux 不会同时满足。
     */
    private fun detectAndroid(): Boolean {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        // 显式排除桌面系统，防止后续信号在特殊桌面环境下误判。
        if (osName.startsWith("windows") || osName.startsWith("mac") || osName.startsWith("darwin")) {
            return false
        }

        // 信号 1（主）：FCL / ZL2 / Pojav 统一以 -Dos.version=Android-<release> 覆盖版本号。
        val osVersion = System.getProperty("os.version", "").lowercase(Locale.ROOT)
        if (osVersion.contains("android")) {
            return true
        }

        // 信号 2：Dalvik 运行时或安卓 JDK 厂商标识（纯 ART/Dalvik 环境可见）。
        val vmName = System.getProperty("java.vm.name", "").lowercase(Locale.ROOT)
        val vmVendor = System.getProperty("java.vendor", "").lowercase(Locale.ROOT)
        if (vmName.contains("dalvik") || vmVendor.contains("android")) {
            return true
        }

        // 信号 3：启动器注入的 POJAV_* 环境变量。
        System.getenv().keys.forEach { key ->
            if (key.startsWith("POJAV", ignoreCase = true)) {
                return true
            }
        }

        // 信号 4：安卓设备文件系统特征（桌面 Linux 不存在 /system/build.prop）。
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
