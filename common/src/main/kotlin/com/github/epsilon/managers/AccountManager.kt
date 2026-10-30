package com.github.epsilon.managers

import com.github.epsilon.accounts.Account
import java.util.concurrent.CopyOnWriteArrayList

object AccountManager {

    private val accounts = CopyOnWriteArrayList<Account<*>>()

    fun add(account: Account<*>) {
        accounts.add(account)
        ConfigManager.saveNow()
    }

    fun exists(account: Account<*>): Boolean {
        return accounts.contains(account)
    }

    fun remove(account: Account<*>) {
        if (accounts.remove(account)) {
            ConfigManager.saveNow()
        }
    }

    fun size(): Int {
        return accounts.size
    }

    fun isEmpty(): Boolean {
        return accounts.isEmpty()
    }

    fun get(index: Int): Account<*> {
        return accounts[index]
    }

    fun getAll(): List<Account<*>> {
        return accounts.toList()
    }

    fun replaceAll(accounts: List<Account<*>>?) {
        this.accounts.clear()
        if (accounts != null) this.accounts.addAll(accounts)
    }

    fun getAccounts(): List<Account<*>> {
        return accounts
    }

}
