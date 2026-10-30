package com.github.epsilon.assets.i18n

/**
 * Static factory that creates [TranslateComponent] instances
 * with the "epsilon" prefix. Used for Epsilon's own i18n keys.
 */
object EpsilonTranslateComponent {

    private const val PREFIX = "epsilon"

    fun create(prefix: String, suffix: String): TranslateComponent {
        return DefaultTranslateComponent.create(PREFIX + "." + prefix + "." + suffix)
    }

}
