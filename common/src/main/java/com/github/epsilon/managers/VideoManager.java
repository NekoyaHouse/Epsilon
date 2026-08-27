package com.github.epsilon.managers;

import com.github.epsilon.Constants;
import com.github.epsilon.graphics.video.VideoPlayer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static com.github.epsilon.Constants.mc;

public class VideoManager {

    private static final File directory = new File(mc.gameDirectory, "Epsilon-Video/Background");
    private static final File backgroundFile = new File(directory, "columbia.mp4");

    public static final VideoManager INSTANCE = new VideoManager();

    private void unpackFile(File file, String name) throws IOException {
        try (InputStream input = Constants.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing bundled video resource: " + name);
            }
            Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void ensureBackgroundFile() throws IOException {
        Files.createDirectories(directory.toPath());
        if (!backgroundFile.exists()) {
            unpackFile(backgroundFile, "assets/epsilon/video/columbia.mp4");
        }
    }

    public void loadBackground() throws IOException {
        if (!backgroundFile.exists()) {
            Constants.LOGGER.error("Background file not found, this should not happen! Reload files.");
            ensureBackgroundFile();
        }
        VideoPlayer.init(backgroundFile);
    }

}
