package com.github.epsilon.accounts.types;

import com.github.epsilon.Constants;
import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.AuthError;
import com.github.epsilon.accounts.AuthResult;
import com.github.epsilon.accounts.TokenAccount;
import com.github.epsilon.utils.network.Http;
import com.google.gson.JsonObject;
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
        accessToken = readString(obj, "token", "");
        return this;
    }

    @Override
    public void write(JsonObject obj) {
        super.write(obj);
        obj.addProperty("token", accessToken);
    }

    @Override
    public AuthResult fetchInfo() {
        if (accessToken == null || accessToken.isBlank()) return AuthResult.failure(AuthError.INVALID_TOKEN);

        ProfileResponse profile;
        try {
            profile = Http.get("https://api.minecraftservices.com/minecraft/profile")
                    .bearer(accessToken)
                    .sendJson(ProfileResponse.class);
        } catch (IllegalArgumentException e) {
            Constants.LOGGER.error("Invalid session account token", e);
            return AuthResult.failure(AuthError.INVALID_TOKEN, e.getMessage());
        }

        if (profile == null) return AuthResult.failure(AuthError.INVALID_TOKEN);
        if (profile.id == null || profile.name == null) return AuthResult.failure(AuthError.NO_PROFILE);

        cache.username = profile.name;
        cache.uuid = profile.id;

        return AuthResult.ok();
    }

    @Override
    public AuthResult login() {
        if (accessToken == null || accessToken.isBlank()) return AuthResult.failure(AuthError.INVALID_TOKEN);
        if (cache.uuid == null || cache.uuid.isBlank()) return AuthResult.failure(AuthError.NOT_FETCHED);

        super.login();

        try {
            setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), accessToken, Optional.empty(), Optional.empty()));
        } catch (IllegalArgumentException e) {
            return AuthResult.failure(AuthError.SESSION_FAILED, cache.uuid);
        }

        return AuthResult.ok();
    }

    @Override
    public String getToken() {
        return accessToken;
    }

    @Override
    protected String identity() {
        if (cache.uuid != null && !cache.uuid.isBlank()) return cache.uuid;
        return accessToken == null ? "" : accessToken;
    }

    private static class ProfileResponse {
        public String id;
        public String name;
    }

}
