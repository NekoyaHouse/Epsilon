package com.github.epsilon.gui.utils

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.settings.impl.RegistryListSetting

object RegistryListUi {

    fun labelText(type: RegistryListSetting.Type): String {
        return when (type) {
            RegistryListSetting.Type.BLOCK -> EpsilonTranslations.Gui.LIST_BLOCKS.getTranslatedName()
            RegistryListSetting.Type.ITEM -> EpsilonTranslations.Gui.LIST_ITEMS.getTranslatedName()
            RegistryListSetting.Type.ENTITY_TYPE -> EpsilonTranslations.Gui.LIST_ENTITIES.getTranslatedName()
            RegistryListSetting.Type.SOUND_EVENT -> EpsilonTranslations.Gui.LIST_SOUNDS.getTranslatedName()
            RegistryListSetting.Type.ENCHANTMENT -> EpsilonTranslations.Gui.LIST_ENCHANTMENTS.getTranslatedName()
        }
    }

}
