package com.github.epsilon.modules

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.graphics.text.IconChars

/**
 * 模块分类。
 *
 * [nameKey] 是 i18n 与 [toString] 使用的稳定 key，[getName] 返回当前语言的翻译文本。
 */
enum class Category(
    val icon: String,
    private val nameKey: String,
) {

    COMBAT(IconChars.SWORDS, "combat"),
    PLAYER(IconChars.PERSON, "player"),
    MOVEMENT(IconChars.DIRECTIONS_RUN, "movement"),
    RENDER(IconChars.BRUSH, "render");

    private val translateComponent: TranslateComponent =
        EpsilonTranslateComponent.create("categories", nameKey)

    fun getName(): String = translateComponent.getTranslatedName()

    override fun toString(): String = nameKey

}
