package com.github.epsilon.managers.impl.sound;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import net.minecraft.resources.Identifier;

public enum SoundKey {

    ENABLE("enable"),
    DISABLE("disable"),
    SETTINGS_OPEN("settings_open"),
    SETTINGS_CLOSE("settings_close"),
    SHUTDOWN("shutdown"),
    REISA_WELCOME("reisa_welcome"),
    REISA_BYE("reisa_bye"),
    UWU("uwu"),
    NYA("nya"),
    MOAN1("moan1"),
    MOAN2("moan2"),
    MOAN3("moan3"),
    MOAN4("moan4");

    private final String path;

    SoundKey(String path) {
        this.path = path;
    }

    public Identifier id() {
        return ResourceLocationUtils.getIdentifier(path);
    }

}
