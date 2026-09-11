package com.github.epsilon.accounts.types;

import com.github.epsilon.Constants;
import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.TokenAccount;
import com.github.epsilon.utils.network.Http;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;

import java.util.Optional;

public class SessionAccount extends Account<SessionAccount> implements TokenAccount {

    private String accessToken;

    public SessionAccount(String label) {
        super(AccountType.Session, label);
        accessToken = label;
    }

    @Override
    public SessionAccount read(JsonObject obj) {
        super.read(obj);
        accessToken = obj.get("token").getAsString();
        return this;
    }

    @Override
    public void write(JsonObject obj) {
        super.write(obj);
        obj.addProperty("token", accessToken);
    }

    @Override
    public boolean fetchInfo() {
        if (accessToken == null || accessToken.isBlank()) return false;

        ProfileResponse profile;
        try {
            profile = Http.get("https://api.minecraftservices.com/minecraft/profile")
                    .bearer(accessToken)
                    .sendJson(ProfileResponse.class);
        } catch (IllegalArgumentException e) {
            Constants.LOGGER.error("Invalid session account token", e);
            return false;
        }

        if (profile == null || profile.id == null || profile.name == null) return false;

        cache.username = profile.name;
        cache.uuid = profile.id;

        return true;
    }

    @Override
    public boolean login() {
        if (accessToken == null || accessToken.isBlank()) return false;

        super.login();

        setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), accessToken, Optional.empty(), Optional.empty()));
        return true;
    }

    @Override
    public String getToken() {
        return accessToken;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SessionAccount account2)) return false;
        return account2.name.equals(this.name);
    }

    private static class ProfileResponse {
        @SerializedName("id")
        public String id;

        @SerializedName("name")
        public String name;
    }

}
