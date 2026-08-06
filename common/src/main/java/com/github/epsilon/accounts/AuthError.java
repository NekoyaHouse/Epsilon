package com.github.epsilon.accounts;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;

public enum AuthError {

    NETWORK(EpsilonTranslations.Gui.ACCOUNTS_ERROR_NETWORK),
    INVALID_TOKEN(EpsilonTranslations.Gui.ACCOUNTS_ERROR_INVALID_TOKEN),
    NO_GAME_OWNERSHIP(EpsilonTranslations.Gui.ACCOUNTS_ERROR_NO_GAME_OWNERSHIP),
    NO_PROFILE(EpsilonTranslations.Gui.ACCOUNTS_ERROR_NO_PROFILE),
    MICROSOFT_FAILED(EpsilonTranslations.Gui.ACCOUNTS_ERROR_MICROSOFT_FAILED),
    SERVER_START_FAILED(EpsilonTranslations.Gui.ACCOUNTS_ERROR_SERVER_START),
    INVALID_NAME(EpsilonTranslations.Gui.ACCOUNTS_ERROR_INVALID_NAME),
    DUPLICATE(EpsilonTranslations.Gui.ACCOUNTS_ERROR_DUPLICATE),
    NOT_FETCHED(EpsilonTranslations.Gui.ACCOUNTS_ERROR_NOT_FETCHED),
    BUSY(EpsilonTranslations.Gui.ACCOUNTS_ERROR_BUSY),
    SESSION_FAILED(EpsilonTranslations.Gui.ACCOUNTS_ERROR_SESSION_FAILED),
    UNKNOWN(EpsilonTranslations.Gui.ACCOUNTS_ERROR_UNKNOWN);

    private final TranslateComponent message;

    AuthError(TranslateComponent message) {
        this.message = message;
    }

    public String getMessage() {
        return message.getTranslatedName();
    }

}
