package com.github.epsilon.accounts

import com.github.epsilon.Constants
import com.github.epsilon.accounts.types.CrackedAccount
import com.github.epsilon.accounts.types.MicrosoftAccount
import com.github.epsilon.accounts.types.SessionAccount
import com.github.epsilon.accounts.types.TheAlteningAccount
import com.google.gson.JsonObject
import com.mojang.authlib.minecraft.UserApiService
import com.mojang.authlib.services.FriendsService
import com.mojang.authlib.services.MinecraftServicesDiscoveryService
import com.mojang.authlib.services.ServicesKeyType
import net.minecraft.client.User
import net.minecraft.client.gui.screens.social.PlayerSocialManager
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment
import net.minecraft.client.multiplayer.chat.report.ReportingContext
import net.minecraft.client.renderer.texture.SkinTextureDownloader
import net.minecraft.client.resources.SkinManager
import net.minecraft.server.Services
import net.minecraft.util.SignatureValidator
import net.minecraft.util.Util
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

abstract class Account<T : Account<T>>(
    var type: AccountType,
    protected var name: String,
) {

    val cache: AccountCache = AccountCache()

    abstract fun fetchInfo(): Boolean

    open fun login(): Boolean {
        applyLoginEnvironment(MinecraftServicesDiscoveryService.create(Constants.client.proxy))
        return true
    }

    val username: String
        get() {
            if (cache.username.isEmpty()) return name
            return cache.username
        }

    open fun write(obj: JsonObject) {
        obj.addProperty("type", type.name)
        obj.addProperty("name", name)
        val cacheObj = JsonObject()
        cache.write(cacheObj)
        obj.add("cache", cacheObj)
    }

    @Suppress("UNCHECKED_CAST")
    open fun read(obj: JsonObject?): T {
        if (obj == null) throw RuntimeException("Invalid account JSON.")
        name = obj.get("name").asString
        val cacheObj = obj.getAsJsonObject("cache")
        if (cacheObj != null) cache.read(cacheObj)
        return this as T
    }

    companion object {

        fun setSession(session: User) {
            Constants.client.user = session

            val discoveryService = MinecraftServicesDiscoveryService.create(Constants.client.proxy)

            val apiService: UserApiService = discoveryService.createUserApiService(session.accessToken)
            val friendsService: FriendsService = discoveryService.createFriendsService(session.accessToken)
            val remoteFriendListUpdateHandler = RemoteFriendListUpdateHandler(friendsService, Constants.client)
            Constants.client.userApiService = apiService
            Constants.client.playerSocialManager = PlayerSocialManager(Constants.client, apiService, friendsService, remoteFriendListUpdateHandler)
            Constants.client.profileKeyPairManager = ProfileKeyPairManager.create(apiService, session, Constants.client.gameDirectory.toPath())
            Constants.client.reportingContext = ReportingContext.create(ReportEnvironment.local(), apiService)
            Constants.client.profileFuture = CompletableFuture.supplyAsync(
                { Constants.client.services().sessionService().fetchProfile(Constants.client.user.profileId, true) },
                Util.ioPool(),
            )
        }

        /**
         * 应用登录环境。
         *
         * authlib 10 用 [MinecraftServicesDiscoveryService] 取代了
         * `YggdrasilAuthenticationService`：会话、资料和好友服务都通过 discovery 文档创建，
         * 因此这里统一改为传入 discovery 服务。
         *
         * @param discoveryService 已创建的 Minecraft 服务发现入口
         */
        fun applyLoginEnvironment(discoveryService: MinecraftServicesDiscoveryService) {
            SignatureValidator.from(discoveryService.servicesKeySet, ServicesKeyType.PROFILE_KEY)
            val skinCache: SkinManager.TextureCache = Constants.client.skinManager.skinTextures
            val skinCachePath: Path = skinCache.root
            Constants.client.services = Services.create(discoveryService, Constants.client.gameDirectory)
            Constants.client.skinManager = SkinManager(
                skinCachePath,
                Constants.client.services(),
                SkinTextureDownloader(Constants.client.proxy, Constants.client.textureManager, Constants.client),
                Constants.client,
            )
        }

        fun fromJson(obj: JsonObject): Account<*> {
            val type = AccountType.valueOf(obj.get("type").asString)

            return when (type) {
                AccountType.Cracked -> CrackedAccount("").read(obj)
                AccountType.Microsoft -> MicrosoftAccount("").read(obj)
                AccountType.TheAltening -> TheAlteningAccount("").read(obj)
                AccountType.Session -> SessionAccount("").read(obj)
            }
        }

    }

}
