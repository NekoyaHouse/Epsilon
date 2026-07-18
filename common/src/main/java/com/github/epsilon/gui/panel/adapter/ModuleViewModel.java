package com.github.epsilon.gui.panel.adapter;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public record ModuleViewModel(Module module, String displayName, String realName, boolean enabled, Category category,
                              String searchText) {
    public static ModuleViewModel from(Module module) {
        String displayName = module.getTranslatedName();
        String description = module.getName();
        String categoryName = module.getCategory().getName();
        String searchText = (displayName + " " + description + " " + categoryName).toLowerCase();
        return new ModuleViewModel(module, displayName, description, module.isEnabled(), module.getCategory(), searchText);
    }
}
