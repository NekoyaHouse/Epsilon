package com.github.epsilon.accounts.types

import com.github.epsilon.accounts.Account
import com.github.epsilon.accounts.AccountType
import net.minecraft.client.User
import net.minecraft.core.UUIDUtil
import java.util.Optional

class CrackedAccount(name: String) : Account<CrackedAccount>(AccountType.Cracked, name) {

    override fun fetchInfo(): Boolean {
        cache.username = name
        return true
    }

    override fun login(): Boolean {
        super.login()

        Account.setSession(User(name, UUIDUtil.createOfflinePlayerUUID(name), "", Optional.empty(), Optional.empty()))
        return true
    }

    override fun equals(o: Any?): Boolean {
        if (o !is CrackedAccount) return false
        return o.username == this.username
    }

}
