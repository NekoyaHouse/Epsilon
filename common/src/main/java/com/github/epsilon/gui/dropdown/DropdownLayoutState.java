package com.github.epsilon.gui.dropdown;

import com.github.epsilon.Constants;
import com.github.epsilon.gui.dropdown.component.DropdownPanel;
import com.github.epsilon.managers.ConfigManager;
import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class DropdownLayoutState {

    private static final String FILE_NAME = "dropdown-layout.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DropdownLayoutState() {
    }

    public static void load(List<? extends DropdownPanel> panels) {
        Path file = getFile();
        if (!Files.exists(file)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            JsonObject panelStates = root.has("panels") && root.get("panels").isJsonObject() ? root.getAsJsonObject("panels") : new JsonObject();
            for (DropdownPanel panel : panels) {
                JsonElement element = panelStates.get(panel.getId());
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                float x = readFloat(object, "x", panel.getX());
                float y = readFloat(object, "y", panel.getY());
                panel.setPosition(x, y);
                if (!"main".equals(panel.getId())) {
                    panel.setVisible(readBoolean(object, "visible", panel.isVisible()));
                }
                panel.setOpened(readBoolean(object, "opened", panel.isOpened()));
            }
        } catch (Exception e) {
            Constants.LOGGER.error("读取 Dropdown GUI 布局失败", e);
        }
    }

    public static void save(List<? extends DropdownPanel> panels) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject panelStates = new JsonObject();
        for (DropdownPanel panel : panels) {
            JsonObject object = new JsonObject();
            object.addProperty("x", panel.getX());
            object.addProperty("y", panel.getY());
            object.addProperty("opened", panel.isOpened());
            object.addProperty("visible", panel.isVisible());
            panelStates.add(panel.getId(), object);
        }
        root.add("panels", panelStates);

        Path file = getFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            Constants.LOGGER.error("保存 Dropdown GUI 布局失败", e);
        }
    }

    private static Path getFile() {
        return ConfigManager.INSTANCE.getConfigDir().resolve(FILE_NAME);
    }

    private static float readFloat(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

}
