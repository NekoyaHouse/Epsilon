package com.github.epsilon.gui.panel.adapter

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

data class ModuleViewModel(
    val module: Module,
    val displayName: String,
    val realName: String,
    val enabled: Boolean,
    val category: Category,
    val searchText: String,
) {

    companion object {

        fun from(module: Module): ModuleViewModel {
            val displayName = module.translatedName
            val description = module.name
            val categoryName = module.category!!.getName()
            val searchText = (displayName + " " + description + " " + categoryName).lowercase()
            return ModuleViewModel(module, displayName, description, module.isEnabled, module.category, searchText)
        }

    }

}
