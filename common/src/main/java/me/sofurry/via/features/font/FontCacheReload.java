package me.sofurry.via.features.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;

public final class FontCacheReload {

    public static void reload() {
        if (Minecraft.getInstance() == null) {
            return;
        }

        for (final FontSet storage : Minecraft.getInstance().fontManager.fontSets.values()) {
            storage.glyphCache.clear();
        }
    }

}
