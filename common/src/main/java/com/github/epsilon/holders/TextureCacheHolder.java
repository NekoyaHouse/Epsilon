package com.github.epsilon.holders;

import com.github.epsilon.graphics.LuminTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class TextureCacheHolder {

    public static final TextureCacheHolder INSTANCE = new TextureCacheHolder();

    private TextureCacheHolder() {
    }

    public final Map<Identifier, LuminTexture> textureCache = new HashMap<>();

    public void clearCache() {
        for (LuminTexture texture : textureCache.values()) {
            texture.close();
        }
        textureCache.clear();
    }

}
