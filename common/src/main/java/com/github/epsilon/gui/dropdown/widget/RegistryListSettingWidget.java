package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.utils.RegistryListUi;
import com.github.epsilon.settings.impl.RegistryListSetting;

public class RegistryListSettingWidget extends AbstractSetSettingWidget<RegistryListSetting<?>> {

    public RegistryListSettingWidget(RegistryListSetting<?> setting) {
        super(setting);
    }

    @Override
    protected int elementCount() {
        return setting.size();
    }

    @Override
    protected String labelText() {
        return RegistryListUi.labelText(setting.getRegistryType());
    }

    @Override
    protected void openPopup() {
        DropdownScreen.INSTANCE.openRegistryListSettingPopup(setting);
    }

}
