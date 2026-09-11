package com.github.epsilon.managers;

import com.github.epsilon.graphics.video.VideoPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VideoManager {

    public static final VideoManager INSTANCE = new VideoManager();

    /**
     * 从 {@code ~/.epsilon/assets/video} 加载按需下载的主菜单背景视频。
     * <p>
     * 资源缺失时直接抛错，由调用方决定是提示下载还是回退到经典背景。
     */
    public void loadBackground() throws IOException {
        AssetManager assets = AssetManager.INSTANCE;
        Path background = assets.videoFile();
        if (Files.notExists(background)) {
            throw new IOException("Main menu video asset is missing: " + background);
        }
        assets.ensureFfmpegLoaded();
        VideoPlayer.init(background.toFile());
    }

}
