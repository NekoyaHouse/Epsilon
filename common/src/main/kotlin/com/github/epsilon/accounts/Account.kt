package com.github.epsilon.accounts

import com.github.epsilon.Constants.mc
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
        applyLoginEnvironment(MinecraftServicesDiscoveryService.create(mc.proxy))
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
            mc.user = session

            val discoveryService = MinecraftServicesDiscoveryService.create(mc.proxy)

            val apiService: UserApiService = discoveryService.createUserApiService(session.accessToken)
            val friendsService: FriendsService = discoveryService.createFriendsService(session.accessToken)
            val remoteFriendListUpdateHandler = RemoteFriendListUpdateHandler(friendsService, mc)
            mc.userApiService = apiService
            mc.playerSocialManager =
                PlayerSocialManager(mc, apiService, friendsService, remoteFriendListUpdateHandler)
            mc.profileKeyPairManager =
                ProfileKeyPairManager.create(apiService, session, mc.gameDirectory.toPath())
            mc.reportingContext = ReportingContext.create(ReportEnvironment.local(), apiService)
            mc.profileFuture = CompletableFuture.supplyAsync(
                { mc.services().sessionService().fetchProfile(mc.user.profileId, true) },
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
            val skinCache: SkinManager.TextureCache = mc.skinManager.skinTextures
            val skinCachePath: Path = skinCache.root
            mc.services = Services.create(discoveryService, mc.gameDirectory)
            mc.skinManager = SkinManager(
                skinCachePath,
                mc.services(),
                SkinTextureDownloader(mc.proxy, mc.textureManager, mc),
                mc,
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
