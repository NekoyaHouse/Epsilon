package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.StringSetting;
import me.sofurry.ClInitNative;

@ClInitNative
public class NameProtect extends Module {

    public static final NameProtect INSTANCE = new NameProtect();

    private NameProtect() {
        super("Name Protect", Category.PLAYER);
    }

    private final StringSetting name = stringSetting("Name", "跨圈皇帝萌萌刻");

    public String replaceName(String string) {
        if (string != null && isEnabled()) {
            return string.replace(mc.getUser().getName(), name.getValue());
        }
        return string;
    }

    public String getName(String original) {
        if (!name.getValue().isEmpty() && isEnabled()) {
            return name.getValue();
        }
        return original;
    }

}
