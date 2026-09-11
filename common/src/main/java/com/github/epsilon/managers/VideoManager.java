package com.github.epsilon.managers;

import com.github.epsilon.Constants;
import com.github.epsilon.graphics.video.VideoPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.github.epsilon.Constants.mc;

public class VideoManager {

    public static final VideoManager INSTANCE = new VideoManager();

    private static final Path BACKGROUND_PATH = new File(mc.gameDirectory, "epsilon-video/columbina.mp4").toPath();

    public void loadBackground() throws IOException {
        if (Files.notExists(BACKGROUND_PATH)) {
            Files.createDirectories(BACKGROUND_PATH.getParent());
            Files.copy(Constants.class.getClassLoader().getResourceAsStream("assets/epsilon/video/columbina.mp4"), BACKGROUND_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
        VideoPlayer.init(BACKGROUND_PATH.toFile());
    }

}
