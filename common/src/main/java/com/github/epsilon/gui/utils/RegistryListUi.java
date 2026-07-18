package com.github.epsilon.gui.utils;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.settings.impl.RegistryListSetting;

public final class RegistryListUi {

    private RegistryListUi() {
    }

    public static String labelText(RegistryListSetting.Type type) {
        return switch (type) {
            case BLOCK -> EpsilonTranslations.Gui.LIST_BLOCKS.getTranslatedName();
            case ITEM -> EpsilonTranslations.Gui.LIST_ITEMS.getTranslatedName();
            case ENTITY_TYPE -> EpsilonTranslations.Gui.LIST_ENTITIES.getTranslatedName();
            case SOUND_EVENT -> EpsilonTranslations.Gui.LIST_SOUNDS.getTranslatedName();
            case ENCHANTMENT -> EpsilonTranslations.Gui.LIST_ENCHANTMENTS.getTranslatedName();
        };
    }
}
