package me.sofurry.via.protocoltranslator.impl.platform;

import com.github.epsilon.managers.ConfigManager;
import com.viaversion.viabackwards.api.ViaBackwardsPlatform;
import com.viaversion.viaversion.api.Via;

import java.io.File;
import java.util.logging.Logger;

public class ViaVersionViaBackwardsPlatform implements ViaBackwardsPlatform {

    private final Logger logger;

    public ViaVersionViaBackwardsPlatform() {
        logger = Via.getPlatform().createLogger("ViaBackwards");
        init(ConfigManager.INSTANCE.createViaBackwardsRuntimeConfig(logger));
        enable();
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public File getDataFolder() {
        return Via.getPlatform().getDataFolder();
    }

    @Override
    public boolean isOutdated() {
        return false;
    }

    @Override
    public void disable() {
    }

}
