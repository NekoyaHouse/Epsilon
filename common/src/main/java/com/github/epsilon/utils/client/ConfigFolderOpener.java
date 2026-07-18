package com.github.epsilon.utils.client;

import com.github.epsilon.holders.ConfigHolder;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigFolderOpener {

    private ConfigFolderOpener() {
    }

    public static Path openConfigFolder() throws IOException {
        Path configDir = ConfigHolder.INSTANCE.getConfigDir();
        Files.createDirectories(configDir);
        Util.getPlatform().openPath(configDir);
        return configDir;
    }

}
