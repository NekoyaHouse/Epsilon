package com.github.epsilon.accounts;

import com.github.epsilon.graphics.PlayerHeadTexture;
import com.github.epsilon.managers.ExecutorManager;
import com.github.epsilon.managers.SkinManager;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;

import static com.github.epsilon.Constants.mc;

public class AccountCache {

    public String username = "";
    public String uuid = "";
    private PlayerHeadTexture headTexture;
    private volatile boolean loadingHead;

    public PlayerHeadTexture getHeadTexture() {
        return headTexture != null ? headTexture : SkinManager.INSTANCE.STEVE_HEAD;
    }

    public void loadHead() {
        loadHead(null);
    }

    public void loadHead(Runnable callback) {
        if (headTexture != null || uuid == null || uuid.isBlank()) {
            if (callback != null) mc.execute(callback);
            return;
        }

        if (loadingHead) return;

        loadingHead = true;

        ExecutorManager.INSTANCE.execute(() -> {
            byte[] head = SkinManager.INSTANCE.fetchHead(UndashedUuid.fromStringLenient(uuid));
            mc.execute(() -> {
                if (head != null) headTexture = new PlayerHeadTexture(head);
                loadingHead = false;
                if (callback != null) callback.run();
            });
        });
    }

    public void write(JsonObject obj) {
        obj.addProperty("username", username);
        obj.addProperty("uuid", uuid);
    }

    public void read(JsonObject obj) {
        username = obj.get("username").getAsString();
        uuid = obj.get("uuid").getAsString();
        loadHead();
    }

}
