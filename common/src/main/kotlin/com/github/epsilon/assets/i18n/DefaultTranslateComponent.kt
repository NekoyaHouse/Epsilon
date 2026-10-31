package com.github.epsilon.assets.i18n

import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.modules.impl.ClientSetting

class DefaultTranslateComponent private constructor(
    private val fullKey: String,
) : TranslateComponent {

    private var cachedName: String? = null

    override fun getFullKey(): String {
        return fullKey
    }

    override fun getTranslatedName(): String {
        var cached = cachedName
        if (cached == null) {
            cached = resolveTranslation(fullKey)
            cachedName = cached
        }
        return cached
    }

    override fun refresh() {
        cachedName = resolveTranslation(fullKey)
    }

    override fun createChild(suffix: String): TranslateComponent {
        return create(fullKey + "." + suffix)
    }

    companion object {

        fun create(fullKey: String): DefaultTranslateComponent {
            val component = DefaultTranslateComponent(fullKey)
            TranslationManager.registerTranslateComponent(component)
            return component
        }

        private fun resolveTranslation(key: String): String {
            if (EpsilonLanguageManager.has(key)) {
                return EpsilonLanguageManager.getOrDefault(key)
            }
            return if (ClientSetting.i18nFallback) formatKey(key) else key
        }

        private fun formatKey(key: String?): String {
            if (key == null || key.isEmpty()) {
                return ""
            }

            var lastPart = key
            val lastDotIndex = key.lastIndexOf('.')
            if (lastDotIndex != -1) {
                lastPart = key.substring(lastDotIndex + 1)
            }

            val result = StringBuilder()
            val words = lastPart.split(" ")

            for (i in words.indices) {
                val word = words[i]
                if (word.isNotEmpty()) {
                    result.append(Character.toUpperCase(word[0]))
                        .append(word.substring(1))

                    if (i < words.size - 1) {
                        result.append(" ")
                    }
                }
            }

            return result.toString()
        }

    }

}
