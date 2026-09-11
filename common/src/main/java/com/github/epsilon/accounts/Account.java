package com.github.epsilon.accounts;

import com.github.epsilon.accounts.types.CrackedAccount;
import com.github.epsilon.accounts.types.MicrosoftAccount;
import com.github.epsilon.accounts.types.SessionAccount;
import com.github.epsilon.accounts.types.TheAlteningAccount;
import com.google.gson.JsonObject;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.github.epsilon.Constants.mc;

public abstract class Account<T extends Account<?>> {

    protected AccountType type;
    protected String name;

    protected final AccountCache cache;

    protected Account(AccountType type, String name) {
        this.type = type;
        this.name = name;
        this.cache = new AccountCache();
    }

    public abstract boolean fetchInfo();

    public boolean login() {
        YggdrasilAuthenticationService authenticationService = new YggdrasilAuthenticationService(mc.getProxy());
        applyLoginEnvironment(authenticationService);
        return true;
    }

    public String getUsername() {
        if (cache.username.isEmpty()) return name;
        return cache.username;
    }

    public AccountType getType() {
        return type;
    }

    public AccountCache getCache() {
        return cache;
    }

    public static void setSession(User session) {
        mc.user = session;

        YggdrasilAuthenticationService yggdrasilAuthenticationService = new YggdrasilAuthenticationService(mc.getProxy());

        UserApiService apiService = yggdrasilAuthenticationService.createUserApiService(session.getAccessToken());
        FriendsService friendsService = yggdrasilAuthenticationService.createFriendsService(session.getAccessToken());
        RemoteFriendListUpdateHandler remoteFriendListUpdateHandler = new RemoteFriendListUpdateHandler(friendsService, mc);
        mc.userApiService = apiService;
        mc.playerSocialManager = new PlayerSocialManager(mc, apiService, friendsService, remoteFriendListUpdateHandler);
        mc.profileKeyPairManager = ProfileKeyPairManager.create(apiService, session, mc.gameDirectory.toPath());
        mc.reportingContext = ReportingContext.create(ReportEnvironment.local(), apiService);
        mc.profileFuture = CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true), Util.ioPool());
    }

    public static void applyLoginEnvironment(YggdrasilAuthenticationService authService) {
        SignatureValidator.from(authService.getServicesKeySet(), ServicesKeyType.PROFILE_KEY);
        SkinManager.TextureCache skinCache = mc.getSkinManager().skinTextures;
        Path skinCachePath = skinCache.root;
        mc.services = Services.create(authService, mc.gameDirectory);
        mc.skinManager = new SkinManager(skinCachePath, mc.services(), new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc), mc);
    }

    public void write(JsonObject obj) {
        obj.addProperty("type", type.name());
        obj.addProperty("name", name);
        JsonObject cacheObj = new JsonObject();
        cache.write(cacheObj);
        obj.add("cache", cacheObj);
    }

    @SuppressWarnings("unchecked")
    public T read(JsonObject obj) {
        if (obj == null) throw new RuntimeException("Invalid account JSON.");
        name = obj.get("name").getAsString();
        JsonObject cacheObj = obj.getAsJsonObject("cache");
        if (cacheObj != null) cache.read(cacheObj);
        return (T) this;
    }

    public static Account<?> fromJson(JsonObject obj) {
        AccountType type = AccountType.valueOf(obj.get("type").getAsString());

        return switch (type) {
            case Cracked -> new CrackedAccount("").read(obj);
            case Microsoft -> new MicrosoftAccount("").read(obj);
            case TheAltening -> new TheAlteningAccount("").read(obj);
            case Session -> new SessionAccount("").read(obj);
        };
    }

}
