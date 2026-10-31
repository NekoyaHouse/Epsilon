package com.github.epsilon.accounts.types

import com.github.epsilon.Constants
import com.github.epsilon.accounts.Account
import com.github.epsilon.accounts.AccountType
import com.github.epsilon.accounts.TokenAccount
import com.github.epsilon.utils.network.Http
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.mojang.authlib.Environment
import com.mojang.authlib.services.MinecraftServicesDiscoveryService
import com.mojang.util.UndashedUuid
import net.minecraft.client.User
import java.util.Optional
import java.util.UUID

class TheAlteningAccount(token: String) : Account<TheAlteningAccount>(AccountType.TheAltening, token), TokenAccount {

    private var token: String? = token
    private var accessToken: String? = null

    override fun fetchInfo(): Boolean {
        try {
            val res = authenticate()
            if (res == null || res.accessToken == null || res.selectedProfile == null) {
                Constants.LOGGER.error("Invalid TheAltening credentials.")
                return false
            }

            accessToken = res.accessToken
            cache.username = res.selectedProfile!!.name!!
            cache.uuid = res.selectedProfile!!.id!!
            cache.loadHead()

            return true
        } catch (e: Exception) {
            Constants.LOGGER.error("Failed to fetch info for TheAltening account!", e)
            return false
        }
    }

    override fun login(): Boolean {
        val currentAccessToken = accessToken
        if (currentAccessToken == null || cache.username.isEmpty() || cache.uuid.isEmpty()) return false
        Account.applyLoginEnvironment(SERVICE)

        try {
            Account.setSession(
                User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), currentAccessToken, Optional.empty(), Optional.empty())
            )
            return true
        } catch (e: Exception) {
            Constants.LOGGER.error("Failed to login with TheAltening.", e)
            return false
        }
    }

    private fun authenticate(): AuthResponse? {
        return Http.post(API_HOST + "/authenticate")
            .bodyJson(AuthRequest("MINECRAFT", token ?: "", "Meteor on Crack!", UUID.randomUUID().toString(), true))
            .sendJson(AuthResponse::class.java)
    }

    override fun getToken(): String {
        return token ?: ""
    }

    override fun write(obj: JsonObject) {
        super.write(obj)
        obj.addProperty("token", token)
    }

    override fun read(obj: JsonObject?): TheAlteningAccount {
        super.read(obj)
        token = obj!!.get("token").asString
        return this
    }

    private data class AuthRequest(
        @field:SerializedName("agent") val agent: String,
        @field:SerializedName("username") val username: String,
        @field:SerializedName("password") val password: String,
        @field:SerializedName("clientToken") val clientToken: String,
        @field:SerializedName("requestUser") val requestUser: Boolean,
    )

    private class AuthResponse {
        @field:SerializedName("accessToken")
        var accessToken: String? = null

        @field:SerializedName("selectedProfile")
        var selectedProfile: AuthProfile? = null
    }

    private class AuthProfile {
        @field:SerializedName("id")
        var id: String? = null

        @field:SerializedName("name")
        var name: String? = null
    }

    companion object {

        /** TheAltening 的账号接口地址，认证请求与 discovery 文档都在该主机下。 */
        private const val API_HOST = "http://authserver.thealtening.com"

        /**
         * authlib 10 的环境只保留 discovery 地址与名称。
         *
         * TheAltening 未提供公开的 discovery 文档说明，这里沿用其账号主机下的 `/discovery` 路径；
         * 若第三方服务调整接口，需要同步更新该地址。
         */
        private val ENVIRONMENT = Environment(API_HOST + "/discovery", "The Altening")

        private val SERVICE: MinecraftServicesDiscoveryService =
            MinecraftServicesDiscoveryService.create(Constants.client.proxy, true, ENVIRONMENT)

    }

}
