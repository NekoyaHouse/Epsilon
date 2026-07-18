package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.component.Component;
import com.github.epsilon.settings.Setting;

public abstract class SettingWidget<S extends Setting<?>> extends Component {

    protected final S setting;

    protected SettingWidget(S setting) {
        this.setting = setting;
    }

    public S getSetting() {
        return setting;
    }

    public boolean isVisible() {
        return setting.isAvailable();
    }

}
