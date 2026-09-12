package com.github.epsilon.utils.client;

import java.util.Locale;

/**
 * 客户端运行平台的统一判定入口。
 * <p>
 * SMTC、视频原生库等能力只在 Windows x86_64 上可用，界面层需要拿到可读的平台名称
 * 用于展示平台不支持提示，因此所有 os.name / os.arch 判断都收敛到这里。
 */
public final class ClientPlatform {

    public enum Family {
        WINDOWS,
        MACOS,
        LINUX,
        UNKNOWN
    }

    private static final Family FAMILY = detectFamily();
    private static final String ARCHITECTURE = normalizeArchitecture(System.getProperty("os.arch", ""));

    private ClientPlatform() {
    }

    public static Family family() {
        return FAMILY;
    }

    public static String architecture() {
        return ARCHITECTURE;
    }

    public static boolean isWindows() {
        return FAMILY == Family.WINDOWS;
    }

    public static boolean isWindowsX64() {
        return FAMILY == Family.WINDOWS && ARCHITECTURE.equals("x86_64");
    }

    public static boolean isMacosArm64() {
        return FAMILY == Family.MACOS && ARCHITECTURE.equals("arm64");
    }

    /**
     * 返回用于界面展示的平台名称，例如 {@code Windows x86_64} 或 {@code macOS arm64}。
     */
    public static String displayName() {
        return familyName(FAMILY) + " " + ARCHITECTURE;
    }

    private static Family detectFamily() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("windows")) {
            return Family.WINDOWS;
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Family.MACOS;
        }
        if (osName.contains("linux")) {
            return Family.LINUX;
        }
        return Family.UNKNOWN;
    }

    private static String normalizeArchitecture(String raw) {
        String arch = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i686" -> "x86";
            default -> arch.isBlank() ? "unknown" : arch;
        };
    }

    private static String familyName(Family family) {
        return switch (family) {
            case WINDOWS -> "Windows";
            case MACOS -> "macOS";
            case LINUX -> "Linux";
            case UNKNOWN -> "Unknown OS";
        };
    }

}
