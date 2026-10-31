package com.github.epsilon.assets.ffmpeg

import com.github.epsilon.utils.client.ClientPlatform

/**
 * JavaCPP FFmpeg 原生库在受支持平台上的产物描述。
 *
 * 原生库不随 mod jar 分发，首次使用时由 `AssetManager` 按这里的 classifier 与下载地址取回并解压，
 * 因此 [nativeFiles] 必须与 `org.bytedeco:ffmpeg` 对应平台产物内的文件名一致。
 * 支持平台集合与 `PlatformRequirement.WINDOWS_X64_OR_MACOS_ARM64` 保持一致。
 */
enum class FFmpegNativePlatform(
    private val classifier: String,
    private val archiveBytes: Long,
    private val nativeFiles: List<String>,
) {

    WINDOWS_X64(
        "windows-x86_64", 24_461_014L,
        listOf(
            "avcodec-60.dll", "avdevice-60.dll", "avfilter-9.dll", "avformat-60.dll", "avutil-58.dll",
            "jniavcodec.dll", "jniavdevice.dll", "jniavfilter.dll", "jniavformat.dll", "jniavutil.dll",
            "jniswresample.dll", "jniswscale.dll", "swresample-4.dll", "swscale-7.dll",
        )
    ),

    MACOS_ARM64(
        "macosx-arm64", 18_483_172L,
        listOf(
            "libavcodec.60.dylib", "libavdevice.60.dylib", "libavfilter.9.dylib", "libavformat.60.dylib",
            "libavutil.58.dylib", "libjniavcodec.dylib", "libjniavdevice.dylib", "libjniavfilter.dylib",
            "libjniavformat.dylib", "libjniavutil.dylib", "libjniswresample.dylib", "libjniswscale.dylib",
            "libswresample.4.dylib", "libswscale.7.dylib",
        )
    );

    /**
     * JavaCPP 产物名，例如 `ffmpeg-6.1.1-1.5.10-macosx-arm64.jar`。
     */
    fun archiveName(): String {
        return "ffmpeg-" + VERSION + "-" + classifier + ".jar"
    }

    /**
     * 阿里云 Maven 镜像上的默认下载地址。
     */
    fun defaultUrl(): String {
        return MAVEN_MIRROR_BASE + archiveName()
    }

    /**
     * 解压后必须齐全的原生库文件名，用于就绪判定与完整性校验。
     */
    fun nativeFiles(): List<String> {
        return nativeFiles
    }

    /**
     * 产物体积，仅用于下载界面的体积展示与整体进度估算。
     */
    fun archiveBytes(): Long {
        return archiveBytes
    }

    companion object {

        private const val VERSION = "6.1.1-1.5.10"

        private const val MAVEN_MIRROR_BASE =
            "https://maven.aliyun.com/repository/public/org/bytedeco/ffmpeg/" + VERSION + "/"

        /**
         * 当前平台对应的 FFmpeg 原生库产物，平台不受支持时返回 `null`。
         */
        fun current(): FFmpegNativePlatform? {
            if (ClientPlatform.isWindowsX64()) {
                return WINDOWS_X64
            }
            if (ClientPlatform.isMacosArm64()) {
                return MACOS_ARM64
            }
            return null
        }

        /**
         * 当前平台的产物；平台不受支持时退回 Windows 产物，仅用于展示设置项的默认下载地址。
         */
        fun currentOrDefault(): FFmpegNativePlatform {
            return current() ?: WINDOWS_X64
        }

        /**
         * 判断自定义下载地址是否指向当前平台的产物。
         *
         * JavaCPP 的产物名里带平台 classifier，因此本平台的合法地址必然包含它；被截断的、属于其它平台的
         * 或与 FFmpeg 无关的地址都会被拒绝，由调用方回退到 [defaultUrl]。
         */
        fun isUrlForCurrentPlatform(url: String?): Boolean {
            if (url == null) {
                return false
            }
            val trimmed = url.trim()
            return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                trimmed.contains(currentOrDefault().classifier)
        }

    }

}
