package com.github.epsilon.accounts.types;

import com.github.epsilon.Constants;
import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.AuthError;
import com.github.epsilon.accounts.AuthResult;
import com.github.epsilon.accounts.TokenAccount;
import com.github.epsilon.utils.network.Http;
import com.google.gson.JsonObject;
import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

import static com.github.epsilon.Constants.mc;

public class TheAlteningAccount extends Account<TheAlteningAccount> implements TokenAccount {

    private static final Environment ENVIRONMENT = new Environment("http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "https://api.mojang.com", "The Altening");
    private static final YggdrasilAuthenticationService SERVICE = new YggdrasilAuthenticationService(mc.getProxy(), ENVIRONMENT);
    private String token;
    private String accessToken;

    public TheAlteningAccount(String token) {
        super(AccountType.TheAltening, token);
        this.token = token;
    }

    @Override
    public AuthResult fetchInfo() {
        if (token == null || token.isBlank()) return AuthResult.failure(AuthError.INVALID_TOKEN);

        try {
            AuthResponse res = authenticate();
            if (res == null) {
                Constants.LOGGER.error("Invalid TheAltening credentials.");
                return AuthResult.failure(AuthError.INVALID_TOKEN);
            }
            if (res.accessToken == null || res.selectedProfile == null
                    || res.selectedProfile.id == null || res.selectedProfile.name == null) {
                Constants.LOGGER.error("Invalid TheAltening credentials.");
                return AuthResult.failure(AuthError.NO_PROFILE);
            }

            accessToken = res.accessToken;
            cache.username = res.selectedProfile.name;
            cache.uuid = res.selectedProfile.id;
            cache.loadHead();

            return AuthResult.ok();
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to fetch info for TheAltening account!", e);
            return AuthResult.failure(AuthError.NETWORK, e.getMessage());
        }
    }

    @Override
    public AuthResult login() {
        if (accessToken == null || cache.username.isEmpty() || cache.uuid.isEmpty()) {
            return AuthResult.failure(AuthError.NOT_FETCHED);
        }
        applyLoginEnvironment(SERVICE);

        try {
            setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), accessToken, Optional.empty(), Optional.empty()));
            return AuthResult.ok();
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to login with TheAltening.", e);
            return AuthResult.failure(AuthError.SESSION_FAILED, e.getMessage());
        }
    }

    private AuthResponse authenticate() {
        return Http.post(ENVIRONMENT.servicesHost() + "/authenticate")
                .bodyJson(new AuthRequest("MINECRAFT", token, "Meteor on Crack!", UUID.randomUUID().toString(), true))
                .sendJson(AuthResponse.class);
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public void write(JsonObject obj) {
        super.write(obj);
        obj.addProperty("token", token);
    }

    @Override
    public TheAlteningAccount read(JsonObject obj) {
        super.read(obj);
        token = readString(obj, "token", "");
        return this;
    }

    @Override
    protected String identity() {
        return token == null ? "" : token;
    }

    private record AuthRequest(String agent, String username, String password, String clientToken,
                               boolean requestUser) {
    }

    private static class AuthResponse {
        public String accessToken;
        public AuthProfile selectedProfile;
    }

    private static class AuthProfile {
        public String id;
        public String name;
    }

}
