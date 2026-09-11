package com.github.epsilon.managers;

import com.github.epsilon.accounts.Account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AccountManager {

    public static final AccountManager INSTANCE = new AccountManager();

    private final List<Account<?>> accounts = new CopyOnWriteArrayList<>();

    private AccountManager() {
    }

    public void add(Account<?> account) {
        accounts.add(account);
        ConfigManager.INSTANCE.saveNow();
    }

    public boolean exists(Account<?> account) {
        return accounts.contains(account);
    }

    public void remove(Account<?> account) {
        if (accounts.remove(account)) {
            ConfigManager.INSTANCE.saveNow();
        }
    }

    public int size() {
        return accounts.size();
    }

    public boolean isEmpty() {
        return accounts.isEmpty();
    }

    public Account<?> get(int index) {
        return accounts.get(index);
    }

    public List<Account<?>> getAll() {
        return List.copyOf(accounts);
    }

    public void replaceAll(List<Account<?>> accounts) {
        this.accounts.clear();
        if (accounts != null) this.accounts.addAll(accounts);
    }

    public List<Account<?>> getAccounts() {
        return accounts;
    }

}
