package com.github.epsilon.assets.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

final class I18NJson {

    static final String VALUE_PROPERTY = "_value";

    private I18NJson() {
    }

    static void read(JsonObject root, BiConsumer<String, JsonElement> output) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(output, "output");

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            readNode(entry.getKey(), entry.getValue(), output);
        }
    }

    static void addTranslation(JsonObject root, String key, String value) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        String[] segments = key.split("\\.", -1);
        JsonObject current = root;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || VALUE_PROPERTY.equals(segment)) {
                throw new IllegalArgumentException("Invalid i18n key: " + key);
            }

            boolean leaf = i == segments.length - 1;
            JsonElement existing = current.get(segment);
            if (leaf) {
                if (existing != null && existing.isJsonObject()) {
                    existing.getAsJsonObject().addProperty(VALUE_PROPERTY, value);
                } else {
                    current.addProperty(segment, value);
                }
                continue;
            }

            JsonObject child;
            if (existing == null) {
                child = new JsonObject();
                current.add(segment, child);
            } else if (existing.isJsonObject()) {
                child = existing.getAsJsonObject();
            } else if (existing.isJsonPrimitive()) {
                child = new JsonObject();
                child.add(VALUE_PROPERTY, existing);
                current.add(segment, child);
            } else {
                throw new IllegalArgumentException("I18n key conflicts with a non-value node: " + key);
            }
            current = child;
        }
    }

    private static void readNode(String key, JsonElement node, BiConsumer<String, JsonElement> output) {
        if (node != null && node.isJsonPrimitive()) {
            output.accept(key, node);
            return;
        }
        if (node == null || !node.isJsonObject()) {
            throw new JsonParseException("Expected an i18n string or object at " + key);
        }

        for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet()) {
            if (VALUE_PROPERTY.equals(entry.getKey())) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) {
                    throw new JsonParseException("Expected an i18n string at " + key + "." + VALUE_PROPERTY);
                }
                output.accept(key, value);
            } else {
                readNode(key + "." + entry.getKey(), entry.getValue(), output);
            }
        }
    }
}
