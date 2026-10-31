package com.github.epsilon.utils.client

/**
 * 功能对运行平台的最低要求。
 *
 * 设置项与枚举选项可以声明自己的平台要求，界面在要求不满足时会展示 Unsupported 标记
 * 并阻止交互，运行时则静默回退且不改写用户保存的配置值。
 */
enum class PlatformRequirement(private val displayName: String) {

    ANY(""),
    WINDOWS_X64("Windows x86_64"),

    /**
     * 视频原生库（JavaCPP FFmpeg）支持的平台集合，与 {@code FFmpegNativePlatform} 保持一致。
     */
    WINDOWS_X64_OR_MACOS_ARM64("Windows x86_64 / macOS arm64");

    fun isSatisfied(): Boolean {
        return when (this) {
            ANY -> true
            WINDOWS_X64 -> ClientPlatform.isWindowsX64()
            WINDOWS_X64_OR_MACOS_ARM64 -> ClientPlatform.isWindowsX64() || ClientPlatform.isMacosArm64()
        }
    }

    /**
     * 返回要求的可读描述，例如 {@code Windows x86_64}。
     */
    fun displayName(): String {
        return displayName
    }

}
