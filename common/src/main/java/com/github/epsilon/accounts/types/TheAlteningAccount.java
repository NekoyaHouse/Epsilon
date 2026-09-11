package com.github.epsilon.accounts.types;

import com.github.epsilon.Constants;
import com.github.epsilon.accounts.Account;
import com.github.epsilon.accounts.AccountType;
import com.github.epsilon.accounts.TokenAccount;
import com.github.epsilon.utils.network.Http;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
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
    public boolean fetchInfo() {
        try {
            AuthResponse res = authenticate();
            if (res == null || res.accessToken == null || res.selectedProfile == null) {
                Constants.LOGGER.error("Invalid TheAltening credentials.");
                return false;
            }

            accessToken = res.accessToken;
            cache.username = res.selectedProfile.name;
            cache.uuid = res.selectedProfile.id;
            cache.loadHead();

            return true;
        } catch (Exception _) {
            Constants.LOGGER.error("Failed to fetch info for TheAltening account!");
            return false;
        }
    }

    @Override
    public boolean login() {
        if (accessToken == null || cache.username.isEmpty() || cache.uuid.isEmpty()) return false;
        applyLoginEnvironment(SERVICE);

        try {
            setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), accessToken, Optional.empty(), Optional.empty()));
            return true;
        } catch (Exception _) {
            Constants.LOGGER.error("Failed to login with TheAltening.");
            return false;
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
        token = obj.get("token").getAsString();
        return this;
    }

    private record AuthRequest(
            @SerializedName("agent") String agent,
            @SerializedName("username") String username,
            @SerializedName("password") String password,
            @SerializedName("clientToken") String clientToken,
            @SerializedName("requestUser") boolean requestUser
    ) {
    }

    private static class AuthResponse {
        @SerializedName("accessToken")
        public String accessToken;

        @SerializedName("selectedProfile")
        public AuthProfile selectedProfile;
    }

    private static class AuthProfile {
        @SerializedName("id")
        public String id;

        @SerializedName("name")
        public String name;
    }

}
