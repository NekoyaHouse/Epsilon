package com.github.epsilon.accounts;

import com.github.epsilon.graphics.PlayerHeadTexture;
import com.github.epsilon.managers.Managers;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;

import static com.github.epsilon.Constants.mc;

public class AccountCache {

    public String username = "";
    public String uuid = "";
    private PlayerHeadTexture headTexture;
    private volatile boolean loadingHead;

    public PlayerHeadTexture getHeadTexture() {
        return headTexture != null ? headTexture : Managers.SKIN.STEVE_HEAD;
    }

    public void loadHead() {
        loadHead(null);
    }

    public void loadHead(Runnable callback) {
        if (headTexture != null || uuid == null || uuid.isBlank()) {
            if (callback != null) mc.execute(callback);
            return;
        }

        if (loadingHead || Managers.EXECUTOR == null) {
            if (callback != null) mc.execute(callback);
            return;
        }

        loadingHead = true;

        Managers.EXECUTOR.execute(() -> {
            byte[] head = Managers.SKIN.fetchHead(UndashedUuid.fromStringLenient(uuid));
            mc.execute(() -> {
                if (head != null) headTexture = new PlayerHeadTexture(head);
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
        username = obj.get("username").getAsString();
        uuid = obj.get("uuid").getAsString();
        loadHead();
    }

}
