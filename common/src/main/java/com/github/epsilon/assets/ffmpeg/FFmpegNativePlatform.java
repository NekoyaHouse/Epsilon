package com.github.epsilon.assets.ffmpeg;

import com.github.epsilon.utils.client.ClientPlatform;

import java.util.List;

/**
 * JavaCPP FFmpeg 原生库在受支持平台上的产物描述。
 * <p>
 * 原生库不随 mod jar 分发，首次使用时由 {@code AssetManager} 按这里的 classifier 与下载地址取回并解压，
 * 因此 {@link #nativeFiles()} 必须与 {@code org.bytedeco:ffmpeg} 对应平台产物内的文件名一致。
 * 支持平台集合与 {@code PlatformRequirement.WINDOWS_X64_OR_MACOS_ARM64} 保持一致。
 */
public enum FFmpegNativePlatform {

    WINDOWS_X64("windows-x86_64", 24_461_014L, List.of(
            "avcodec-60.dll", "avdevice-60.dll", "avfilter-9.dll", "avformat-60.dll", "avutil-58.dll",
            "jniavcodec.dll", "jniavdevice.dll", "jniavfilter.dll", "jniavformat.dll", "jniavutil.dll",
            "jniswresample.dll", "jniswscale.dll", "swresample-4.dll", "swscale-7.dll"
    )),

    MACOS_ARM64("macosx-arm64", 18_483_172L, List.of(
            "libavcodec.60.dylib", "libavdevice.60.dylib", "libavfilter.9.dylib", "libavformat.60.dylib",
            "libavutil.58.dylib", "libjniavcodec.dylib", "libjniavdevice.dylib", "libjniavfilter.dylib",
            "libjniavformat.dylib", "libjniavutil.dylib", "libjniswresample.dylib", "libjniswscale.dylib",
            "libswresample.4.dylib", "libswscale.7.dylib"
    ));

    private static final String VERSION = "6.1.1-1.5.10";

    private static final String MAVEN_MIRROR_BASE =
            "https://maven.aliyun.com/repository/public/org/bytedeco/ffmpeg/" + VERSION + "/";

    private final String classifier;
    private final long archiveBytes;
    private final List<String> nativeFiles;

    FFmpegNativePlatform(String classifier, long archiveBytes, List<String> nativeFiles) {
        this.classifier = classifier;
        this.archiveBytes = archiveBytes;
        this.nativeFiles = nativeFiles;
    }

    /**
     * 当前平台对应的 FFmpeg 原生库产物，平台不受支持时返回 {@code null}。
     */
    public static FFmpegNativePlatform current() {
        if (ClientPlatform.isWindowsX64()) {
            return WINDOWS_X64;
        }
        if (ClientPlatform.isMacosArm64()) {
            return MACOS_ARM64;
        }
        return null;
    }

    /**
     * 当前平台的产物；平台不受支持时退回 Windows 产物，仅用于展示设置项的默认下载地址。
     */
    public static FFmpegNativePlatform currentOrDefault() {
        FFmpegNativePlatform current = current();
        return current == null ? WINDOWS_X64 : current;
    }

    public String classifier() {
        return classifier;
    }

    /**
     * JavaCPP 产物名，例如 {@code ffmpeg-6.1.1-1.5.10-macosx-arm64.jar}。
     */
    public String archiveName() {
        return "ffmpeg-" + VERSION + "-" + classifier + ".jar";
    }

    /**
     * 判断自定义下载地址是否指向当前平台的产物。
     * <p>
     * JavaCPP 的产物名里带平台 classifier，因此本平台的合法地址必然包含它；被截断的、属于其它平台的
     * 或与 FFmpeg 无关的地址都会被拒绝，由调用方回退到 {@link #defaultUrl()}。
     */
    public static boolean isUrlForCurrentPlatform(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                && trimmed.contains(currentOrDefault().classifier());
    }

    /**
     * 阿里云 Maven 镜像上的默认下载地址。
     */
    public String defaultUrl() {
        return MAVEN_MIRROR_BASE + archiveName();
    }

    /**
     * 解压后必须齐全的原生库文件名，用于就绪判定与完整性校验。
     */
    public List<String> nativeFiles() {
        return nativeFiles;
    }

    /**
     * 产物体积，仅用于下载界面的体积展示与整体进度估算。
     */
    public long archiveBytes() {
        return archiveBytes;
    }

}
