package com.github.epsilon.accounts.types;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.MicrosoftLogin;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;

import java.util.Optional;

public class MicrosoftAccount extends Account<MicrosoftAccount> {

    private String token;

    public MicrosoftAccount(String refreshToken) {
        super(AccountType.Microsoft, refreshToken);
    }

    @Override
    public boolean fetchInfo() {
        token = auth();
        return token != null;
    }

    @Override
    public boolean login() {
        if (token == null) return false;

        super.login();

        setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), token, Optional.empty(), Optional.empty()));
        return true;
    }

    private String auth() {
        MicrosoftLogin.LoginData data = MicrosoftLogin.login(name);
        if (!data.isGood()) return null;

        name = data.newRefreshToken;
        cache.username = data.username;
        cache.uuid = data.uuid;

        return data.mcToken;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MicrosoftAccount account)) return false;
        return account.name.equals(this.name);
    }

}
