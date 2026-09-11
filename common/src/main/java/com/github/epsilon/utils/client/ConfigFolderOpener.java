package com.github.epsilon.utils.client;

import com.github.epsilon.managers.ConfigManager;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigFolderOpener {

    private ConfigFolderOpener() {
    }

    /**
     * 创建并使用系统文件管理器打开 Epsilon 配置目录。
     *
     * @return 配置目录路径
     * @throws IOException 无法创建或打开配置目录时
     */
    public static Path openConfigFolder() throws IOException {
        Path configDir = ConfigManager.INSTANCE.getConfigDir();
        Files.createDirectories(configDir);
        Util.getPlatform().openPath(configDir);
        return configDir;
    }

}
