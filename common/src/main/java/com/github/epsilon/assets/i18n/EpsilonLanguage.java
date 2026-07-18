package com.github.epsilon.assets.i18n;

public enum EpsilonLanguage {
    English("en_us", "English"),
    ChineseSimplified("zh_cn", "Chinese Simplified"),
    Custom("", "Custom");

    private final String code;
    private final String settingName;

    EpsilonLanguage(String code, String settingName) {
        this.code = code;
        this.settingName = settingName;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return settingName;
    }

    public boolean isCustom() {
        return this == Custom;
    }
}
