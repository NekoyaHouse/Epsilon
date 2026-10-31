package com.github.epsilon.assets.i18n

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.util.regex.Pattern

internal object I18NJson {

    const val VALUE_PROPERTY = "_value"

    private val KEY_SEPARATOR: Pattern = Pattern.compile("\\.")

    fun read(root: JsonObject, output: (String, JsonElement) -> Unit) {
        for ((key, value) in root.entrySet()) {
            readNode(key, value, output)
        }
    }

    fun addTranslation(root: JsonObject, key: String, value: String) {
        // 与 Java String.split(regex, -1) 语义一致：保留尾部空段。
        val segments = KEY_SEPARATOR.split(key, -1)
        var current = root

        for (i in segments.indices) {
            val segment = segments[i]
            if (segment.isEmpty() || VALUE_PROPERTY == segment) {
                throw IllegalArgumentException("Invalid i18n key: $key")
            }

            val leaf = i == segments.size - 1
            val existing = current.get(segment)
            if (leaf) {
                if (existing != null && existing.isJsonObject) {
                    existing.asJsonObject.addProperty(VALUE_PROPERTY, value)
                } else {
                    current.addProperty(segment, value)
                }
                continue
            }

            val child: JsonObject
            if (existing == null) {
                child = JsonObject()
                current.add(segment, child)
            } else if (existing.isJsonObject) {
                child = existing.asJsonObject
            } else if (existing.isJsonPrimitive) {
                child = JsonObject()
                child.add(VALUE_PROPERTY, existing)
                current.add(segment, child)
            } else {
                throw IllegalArgumentException("I18n key conflicts with a non-value node: $key")
            }
            current = child
        }
    }

    private fun readNode(key: String, node: JsonElement?, output: (String, JsonElement) -> Unit) {
        if (node != null && node.isJsonPrimitive) {
            output(key, node)
            return
        }
        if (node == null || !node.isJsonObject) {
            throw JsonParseException("Expected an i18n string or object at $key")
        }

        for ((childKey, childValue) in node.asJsonObject.entrySet()) {
            if (VALUE_PROPERTY == childKey) {
                if (!childValue.isJsonPrimitive) {
                    throw JsonParseException("Expected an i18n string at $key.$VALUE_PROPERTY")
                }
                output(key, childValue)
            } else {
                readNode("$key.$childKey", childValue, output)
            }
        }
    }

}
