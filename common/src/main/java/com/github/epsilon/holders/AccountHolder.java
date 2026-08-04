package com.github.epsilon.holders;

import com.github.epsilon.accounts.Account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AccountHolder {

    public static final AccountHolder INSTANCE = new AccountHolder();

    private final List<Account<?>> accounts = new CopyOnWriteArrayList<>();

    public void add(Account<?> account) {
        accounts.add(account);
        ConfigHolder.INSTANCE.saveNow();
    }

    public boolean exists(Account<?> account) {
        return accounts.contains(account);
    }

    public void remove(Account<?> account) {
        if (accounts.remove(account)) {
            ConfigHolder.INSTANCE.saveNow();
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
