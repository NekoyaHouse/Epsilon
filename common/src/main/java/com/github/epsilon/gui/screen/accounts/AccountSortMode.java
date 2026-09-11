package com.github.epsilon.gui.screen.accounts;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;

import java.util.Comparator;

public enum AccountSortMode {

    ADDED(EpsilonTranslations.Gui.ACCOUNTS_SORT_ADDED, null),
    NAME(EpsilonTranslations.Gui.ACCOUNTS_SORT_NAME, Comparator.comparing(a -> a.getUsername().toLowerCase())),
    TYPE(EpsilonTranslations.Gui.ACCOUNTS_SORT_TYPE, Comparator.<Account<?>, String>comparing(a -> a.getType().name()).thenComparing(a -> a.getUsername().toLowerCase()));

    private final TranslateComponent label;
    private final Comparator<Account<?>> comparator;

    AccountSortMode(TranslateComponent label, Comparator<Account<?>> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    public String getLabel() {
        return label.getTranslatedName();
    }

    public Comparator<Account<?>> getComparator() {
        return comparator;
    }

    public AccountSortMode next() {
        AccountSortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

}
