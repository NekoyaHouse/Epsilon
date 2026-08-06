package com.github.epsilon.accounts.types;

import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.AuthError;
import com.github.epsilon.accounts.AuthResult;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;

public class CrackedAccount extends Account<CrackedAccount> {

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;

    public CrackedAccount(String name) {
        super(AccountType.Cracked, name);
    }

    public static boolean isValidName(String name) {
        if (name == null) return false;

        String trimmed = name.trim();
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '_' && !Character.isLetterOrDigit(c)) return false;
        }

        return true;
    }

    @Override
    public AuthResult fetchInfo() {
        if (!isValidName(name)) return AuthResult.failure(AuthError.INVALID_NAME, name);

        name = name.trim();
        cache.username = name;
        return AuthResult.ok();
    }

    @Override
    public AuthResult login() {
        if (!isValidName(name)) return AuthResult.failure(AuthError.INVALID_NAME, name);

        super.login();

        setSession(new User(name, UUIDUtil.createOfflinePlayerUUID(name), "", Optional.empty(), Optional.empty()));
        return AuthResult.ok();
    }

    @Override
    protected String identity() {
        return name == null ? "" : name.trim();
    }

}
