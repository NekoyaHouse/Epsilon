package com.github.epsilon.managers;

import com.github.epsilon.Constants;
import com.github.epsilon.accounts.TexturesJson;
import com.github.epsilon.accounts.UuidToProfileResponse;
import com.github.epsilon.graphics.PlayerHeadTexture;
import com.github.epsilon.utils.network.Http;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class SkinManager {

    public static final SkinManager INSTANCE = new SkinManager();

    public PlayerHeadTexture STEVE_HEAD;

    private SkinManager() {
        STEVE_HEAD = new PlayerHeadTexture();
    }

    public byte[] fetchHead(UUID id) {
        if (id == null) return null;

        String url = getSkinUrl(id);
        if (url == null) return null;

        try {
            return PlayerHeadTexture.downloadHead(url);
        } catch (IOException e) {
            Constants.LOGGER.error("Could not fetch player head for {}.", id, e);
            return null;
        }
    }

    public String getSkinUrl(UUID id) {
        UuidToProfileResponse res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
                .exceptionHandler(e -> Constants.LOGGER.error("无法连接到 Mojang 会话服务器.", e))
                .sendJson(UuidToProfileResponse.class);
        if (res2 == null) return null;

        String base64Textures = res2.getPropertyValue("textures");
        if (base64Textures == null) return null;

        TexturesJson textures = new Gson().fromJson(new String(Base64.getDecoder().decode(base64Textures)), TexturesJson.class);
        if (textures.textures.SKIN == null) return null;

        return textures.textures.SKIN.url;
    }

}
