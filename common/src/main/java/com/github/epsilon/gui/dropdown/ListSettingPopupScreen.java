package com.github.epsilon.gui.dropdown;

import com.github.epsilon.settings.impl.RegistryListSetting;
import com.github.epsilon.settings.impl.StringListSetting;

public interface ListSettingPopupScreen {

    void openRegistryListSettingPopup(RegistryListSetting<?> setting);

    void openStringListSettingPopup(StringListSetting setting);

}
