package com.github.epsilon.accounts.types

import com.github.epsilon.Constants
import com.github.epsilon.accounts.Account
import com.github.epsilon.accounts.AccountType
import com.github.epsilon.accounts.TokenAccount
import com.github.epsilon.utils.network.Http
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.mojang.util.UndashedUuid
import net.minecraft.client.User
import java.util.*

class SessionAccount(label: String) : Account<SessionAccount>(AccountType.Session, label), TokenAccount {

    private var accessToken: String? = label

    override fun read(obj: JsonObject?): SessionAccount {
        super.read(obj)
        accessToken = obj!!.get("token").asString
        return this
    }

    override fun write(obj: JsonObject) {
        super.write(obj)
        obj.addProperty("token", accessToken)
    }

    override fun fetchInfo(): Boolean {
        val token = accessToken
        if (token.isNullOrBlank()) return false

        val profile: ProfileResponse? = try {
            Http.get("https://api.minecraftservices.com/minecraft/profile")
                .bearer(token)
                .sendJson(ProfileResponse::class.java)
        } catch (e: IllegalArgumentException) {
            Constants.LOGGER.error("Invalid session account token", e)
            return false
        }

        if (profile == null || profile.id == null || profile.name == null) return false

        cache.username = profile.name!!
        cache.uuid = profile.id!!

        return true
    }

    override fun login(): Boolean {
        val token = accessToken
        if (token.isNullOrBlank()) return false

        super.login()

        setSession(
            User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), token, Optional.empty(), Optional.empty())
        )
        return true
    }

    override fun getToken(): String {
        return accessToken ?: ""
    }

    override fun equals(o: Any?): Boolean {
        return o is SessionAccount && o.name == this.name
    }

    private class ProfileResponse {
        @field:SerializedName("id")
        var id: String? = null

        @field:SerializedName("name")
        var name: String? = null
    }

}
