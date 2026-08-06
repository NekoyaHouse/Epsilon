package com.github.epsilon.accounts.types;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.AuthError;
import com.github.epsilon.accounts.AuthResult;
import com.github.epsilon.accounts.MicrosoftLogin;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;

import java.util.Optional;

public class MicrosoftAccount extends Account<MicrosoftAccount> {

    private String token;

    public MicrosoftAccount(String refreshToken) {
        super(AccountType.Microsoft, refreshToken);
    }

    public void adoptCredentials(MicrosoftAccount other) {
        if (other == null) return;

        this.name = other.name;
        this.token = other.token;
        this.cache.username = other.cache.username;
        this.cache.uuid = other.cache.uuid;
    }

    @Override
    public AuthResult fetchInfo() {
        MicrosoftLogin.LoginData data = MicrosoftLogin.login(name);
        if (!data.isGood()) return AuthResult.failure(data.getError());

        name = data.newRefreshToken;
        cache.username = data.username;
        cache.uuid = data.uuid;
        token = data.mcToken;

        return AuthResult.ok();
    }

    @Override
    public AuthResult login() {
        if (token == null) return AuthResult.failure(AuthError.NOT_FETCHED);
        if (cache.uuid == null || cache.uuid.isBlank()) return AuthResult.failure(AuthError.NOT_FETCHED);

        super.login();

        try {
            setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), token, Optional.empty(), Optional.empty()));
        } catch (IllegalArgumentException e) {
            return AuthResult.failure(AuthError.SESSION_FAILED, cache.uuid);
        }

        return AuthResult.ok();
    }

    @Override
    protected String identity() {
        if (cache.uuid != null && !cache.uuid.isBlank()) return cache.uuid;
        return name == null ? "" : name;
    }

}
