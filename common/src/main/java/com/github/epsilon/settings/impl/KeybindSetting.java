package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;

public class KeybindSetting extends Setting<Integer> {

    public KeybindSetting(String name, int defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

}


