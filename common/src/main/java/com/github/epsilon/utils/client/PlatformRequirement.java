package com.github.epsilon.utils.client;

/**
 * 功能对运行平台的最低要求。
 * <p>
 * 设置项与枚举选项可以声明自己的平台要求，界面在要求不满足时会展示 Unsupported 标记
 * 并阻止交互，运行时则静默回退且不改写用户保存的配置值。
 */
public enum PlatformRequirement {

    ANY(""),
    WINDOWS_X64("Windows x86_64");

    private final String displayName;

    PlatformRequirement(String displayName) {
        this.displayName = displayName;
    }

    public boolean isSatisfied() {
        return switch (this) {
            case ANY -> true;
            case WINDOWS_X64 -> ClientPlatform.isWindowsX64();
        };
    }

    /**
     * 返回要求的可读描述，例如 {@code Windows x86_64}。
     */
    public String displayName() {
        return displayName;
    }

}
