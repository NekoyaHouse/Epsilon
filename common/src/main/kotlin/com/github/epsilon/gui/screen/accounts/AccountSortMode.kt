package com.github.epsilon.gui.screen.accounts

import com.github.epsilon.accounts.Account
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import java.util.Comparator

enum class AccountSortMode(
    private val label: TranslateComponent,
    private val comparator: Comparator<Account<*>>?,
) {

    ADDED(EpsilonTranslations.Gui.ACCOUNTS_SORT_ADDED, null),
    NAME(EpsilonTranslations.Gui.ACCOUNTS_SORT_NAME, Comparator.comparing<Account<*>, String> { a -> a.username.lowercase() }),
    TYPE(
        EpsilonTranslations.Gui.ACCOUNTS_SORT_TYPE,
        Comparator.comparing<Account<*>, String> { a -> a.type.name }.thenComparing({ a: Account<*> -> a.username.lowercase() }),
    );

    fun getLabel(): String {
        return label.translatedName
    }

    fun getComparator(): Comparator<Account<*>>? {
        return comparator
    }

    fun next(): AccountSortMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

}
