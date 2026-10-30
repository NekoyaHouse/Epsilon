package com.github.epsilon.accounts.types

import com.github.epsilon.accounts.Account
import com.github.epsilon.accounts.AccountType
import com.github.epsilon.accounts.MicrosoftLogin
import com.mojang.util.UndashedUuid
import net.minecraft.client.User
import java.util.Optional

class MicrosoftAccount(refreshToken: String) : Account<MicrosoftAccount>(AccountType.Microsoft, refreshToken) {

    private var token: String? = null

    override fun fetchInfo(): Boolean {
        token = auth()
        return token != null
    }

    override fun login(): Boolean {
        val currentToken = token ?: return false

        super.login()

        Account.setSession(
            User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), currentToken, Optional.empty(), Optional.empty())
        )
        return true
    }

    private fun auth(): String? {
        val data = MicrosoftLogin.login(name)
        if (!data.isGood()) return null

        name = data.newRefreshToken ?: return null
        cache.username = data.username ?: ""
        cache.uuid = data.uuid ?: ""

        return data.mcToken
    }

    override fun equals(o: Any?): Boolean {
        if (o !is MicrosoftAccount) return false
        return o.name == this.name
    }

}
