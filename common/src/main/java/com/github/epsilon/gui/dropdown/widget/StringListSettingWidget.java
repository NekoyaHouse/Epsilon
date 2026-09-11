package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen;
import com.github.epsilon.settings.impl.StringListSetting;

import static com.github.epsilon.Constants.mc;

public class StringListSettingWidget extends AbstractSetSettingWidget<StringListSetting> {

    public StringListSettingWidget(StringListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() {
        return setting.size();
    }

    @Override
    protected String labelText() {
        return EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName();
    }

    @Override
    protected void openPopup() {
        if (mc.gui.screen() instanceof ListSettingPopupScreen popupScreen) {
            popupScreen.openStringListSettingPopup(setting);
        }
    }

}
