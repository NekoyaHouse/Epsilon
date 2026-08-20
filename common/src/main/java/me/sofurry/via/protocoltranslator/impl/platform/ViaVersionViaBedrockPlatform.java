package me.sofurry.via.protocoltranslator.impl.platform;

import com.github.epsilon.managers.ConfigManager;
import com.viaversion.viaversion.api.Via;
import net.raphimc.viabedrock.platform.ViaBedrockPlatform;

import java.io.File;
import java.util.logging.Logger;

public final class ViaVersionViaBedrockPlatform implements ViaBedrockPlatform {

    private final Logger logger;

    public ViaVersionViaBedrockPlatform() {
        logger = Via.getPlatform().createLogger("ViaBedrock");
        init(ConfigManager.INSTANCE.createViaBedrockRuntimeConfig(logger));
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public File getDataFolder() {
        return Via.getPlatform().getDataFolder();
    }

}
