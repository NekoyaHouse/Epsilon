package com.github.epsilon.accounts.types;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;

public class CrackedAccount extends Account<CrackedAccount> {

    public CrackedAccount(String name) {
        super(AccountType.Cracked, name);
    }

    @Override
    public boolean fetchInfo() {
        cache.username = name;
        return true;
    }

    @Override
    public boolean login() {
        super.login();

        setSession(new User(name, UUIDUtil.createOfflinePlayerUUID(name), "", Optional.empty(), Optional.empty()));
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CrackedAccount account)) return false;
        return account.getUsername().equals(this.getUsername());
    }

}
