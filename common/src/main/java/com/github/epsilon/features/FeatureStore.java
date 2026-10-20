package com.github.epsilon.features;

import com.github.epsilon.Constants;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.utils.player.ChatUtils;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Separate observed world data from portable module configuration. Main-thread access only. */
public final class FeatureStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private FeatureStore() {}
    private static Path path(String name) {
        if (!name.matches("[a-z-]+")) throw new IllegalArgumentException(name);
        return ConfigManager.INSTANCE.getConfigDir().resolve("observed-data").resolve(name + ".json");
    }
    public static JsonObject read(String name) {
        Path file = path(name);
        if (!Files.exists(file)) return new JsonObject();
        try {
            if (Files.size(file) > 32 * 1024 * 1024) throw new IllegalStateException("Data file too large");
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            Constants.LOGGER.error("Cannot read observed data {} (original retained)", name, e);
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
    public static boolean write(String name, JsonObject data) {
        Path file = path(name), temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception e) {
            Constants.LOGGER.error("Cannot save observed data {}", name, e);
            ChatUtils.addChatMessage(FeatureText.get("save_failed") + ": " + name);
            return false;
        }
    }
}
