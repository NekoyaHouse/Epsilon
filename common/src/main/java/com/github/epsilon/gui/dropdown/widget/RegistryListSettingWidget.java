package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.ListSettingPopupScreen;
import com.github.epsilon.gui.utils.RegistryListUi;
import com.github.epsilon.settings.impl.RegistryListSetting;

import static com.github.epsilon.Constants.mc;

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
        if (mc.gui.screen() instanceof ListSettingPopupScreen popupScreen) {
            popupScreen.openRegistryListSettingPopup(setting);
        }
    }

}
