package com.github.epsilon.gui.dropdown

import com.github.epsilon.Constants
import com.github.epsilon.gui.dropdown.component.DropdownPanel
import com.github.epsilon.managers.ConfigManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object DropdownLayoutState {

    private const val FILE_NAME = "dropdown-layout.json"

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun load(panels: List<DropdownPanel>) {
        val file = getFile()
        if (!Files.exists(file)) return
        try {
            val parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
            if (parsed == null || !parsed.isJsonObject) return
            val root = parsed.asJsonObject
            val panelStates: JsonObject = if (root.has("panels") && root.get("panels").isJsonObject) root.getAsJsonObject("panels") else JsonObject()
            for (panel in panels) {
                val element: JsonElement = panelStates.get(panel.id) ?: continue
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val x = readFloat(obj, "x", panel.x)
                val y = readFloat(obj, "y", panel.y)
                panel.setPosition(x, y)
                if ("main" != panel.id) {
                    panel.isVisible = readBoolean(obj, "visible", panel.isVisible)
                }
                panel.isOpened = readBoolean(obj, "opened", panel.isOpened)
            }
        } catch (e: Exception) {
            Constants.LOGGER.error("读取 Dropdown GUI 布局失败", e)
        }
    }

    fun save(panels: List<DropdownPanel>) {
        val root = JsonObject()
        root.addProperty("version", 1)
        val panelStates = JsonObject()
        for (panel in panels) {
            val obj = JsonObject()
            obj.addProperty("x", panel.x)
            obj.addProperty("y", panel.y)
            obj.addProperty("opened", panel.isOpened)
            obj.addProperty("visible", panel.isVisible)
            panelStates.add(panel.id, obj)
        }
        root.add("panels", panelStates)

        val file = getFile()
        try {
            Files.createDirectories(file.parent)
            Files.writeString(
                file, GSON.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (e: IOException) {
            Constants.LOGGER.error("保存 Dropdown GUI 布局失败", e)
        }
    }

    private fun getFile(): Path {
        return ConfigManager.INSTANCE.configDir.resolve(FILE_NAME)
    }

    private fun readFloat(obj: JsonObject, key: String, fallback: Float): Float {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive) return fallback
        try {
            return value.asFloat
        } catch (ignored: Exception) {
            return fallback
        }
    }

    private fun readBoolean(obj: JsonObject, key: String, fallback: Boolean): Boolean {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive) return fallback
        try {
            return value.asBoolean
        } catch (ignored: Exception) {
            return fallback
        }
    }

}
