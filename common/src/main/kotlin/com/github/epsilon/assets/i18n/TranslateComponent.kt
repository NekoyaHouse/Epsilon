package com.github.epsilon.assets.i18n

interface TranslateComponent {

    fun getFullKey(): String

    fun getTranslatedName(): String

    fun refresh()

    fun createChild(suffix: String): TranslateComponent

}
