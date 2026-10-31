package com.github.epsilon.assets.i18n

enum class EpsilonLanguage(
    val code: String,
    private val settingName: String,
) {

    English("en_us", "English"),
    ChineseSimplified("zh_cn", "Chinese Simplified"),
    Custom("", "Custom");

    fun isCustom(): Boolean {
        return this == Custom
    }

    override fun toString(): String {
        return settingName
    }

}
