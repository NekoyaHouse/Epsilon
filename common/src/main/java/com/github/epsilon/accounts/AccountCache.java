package com.github.epsilon.accounts;

import com.github.epsilon.Constants;
import com.github.epsilon.graphics.PlayerHeadTexture;
import com.github.epsilon.managers.Managers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;

import static com.github.epsilon.Constants.mc;

public class AccountCache {

    public String username = "";
    public String uuid = "";
    private PlayerHeadTexture headTexture;
    private volatile boolean loadingHead;
    private volatile boolean released;

    public PlayerHeadTexture getHeadTexture() {
        return headTexture != null ? headTexture : Managers.SKIN.STEVE_HEAD;
    }

    public void releaseHead() {
        released = true;

        mc.execute(() -> {
            if (headTexture != null) {
                headTexture.close();
                headTexture = null;
            }
        });
    }

    public void loadHead() {
        loadHead(null);
    }

    public void loadHead(Runnable callback) {
        if (released || headTexture != null || uuid == null || uuid.isBlank()) {
            if (callback != null) mc.execute(callback);
            return;
        }

        if (loadingHead || Managers.EXECUTOR == null) {
            if (callback != null) mc.execute(callback);
            return;
        }

        loadingHead = true;

        Managers.EXECUTOR.execute(() -> {
            byte[] head;
            try {
                head = Managers.SKIN.fetchHead(UndashedUuid.fromStringLenient(uuid));
            } catch (IllegalArgumentException e) {
                Constants.LOGGER.warn("Invalid UUID in account cache: {}", uuid);
                head = null;
            }

            byte[] result = head;
            mc.execute(() -> {
                if (result != null && !released) headTexture = new PlayerHeadTexture(result);
                loadingHead = false;
                if (callback != null) callback.run();
            });
        });
    }

    void write(JsonObject obj) {
        obj.addProperty("username", username);
        obj.addProperty("uuid", uuid);
    }

    void read(JsonObject obj) {
        username = readString(obj, "username");
        uuid = readString(obj, "uuid");
        loadHead();
    }

    private static String readString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

}
