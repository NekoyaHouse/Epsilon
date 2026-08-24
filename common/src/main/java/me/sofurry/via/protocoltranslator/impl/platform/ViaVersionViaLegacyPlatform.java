package me.sofurry.via.protocoltranslator.impl.platform;

import com.github.epsilon.managers.ConfigManager;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.protocol.ProtocolManagerImpl;
import net.raphimc.vialegacy.platform.ViaLegacyPlatform;

import java.io.File;
import java.util.logging.Logger;

public class ViaVersionViaLegacyPlatform implements ViaLegacyPlatform {

    private final Logger logger;

    public ViaVersionViaLegacyPlatform() {
        logger = Via.getPlatform().createLogger("ViaLegacy");
        init(ConfigManager.INSTANCE.createViaLegacyRuntimeConfig(logger));
        Via.getManager().addPostEnableListener(() -> {
            Via.getManager().getProtocolManager().setMaxProtocolPathSize(Integer.MAX_VALUE);
            Via.getManager().getProtocolManager().setMaxPathDeltaIncrease(-1);
            ((ProtocolManagerImpl) Via.getManager().getProtocolManager()).refreshVersions();
        });
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
    public String getCpeAppName() {
        return Via.getPlatform().getPlatformName() + " " + Via.getPlatform().getPlatformVersion();
    }

}
