package com.github.epsilon.holders;

import com.github.epsilon.accounts.Account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AccountHolder {

    public static final AccountHolder INSTANCE = new AccountHolder();

    private final List<Account<?>> accounts = new CopyOnWriteArrayList<>();

    public boolean add(Account<?> account) {
        if (account == null || accounts.contains(account)) return false;

        accounts.add(account);
        ConfigHolder.INSTANCE.saveNow();
        return true;
    }

    public void save() {
        ConfigHolder.INSTANCE.saveNow();
    }

    public boolean exists(Account<?> account) {
        return accounts.contains(account);
    }

    public Account<?> find(Account<?> account) {
        if (account == null) return null;

        for (Account<?> existing : accounts) {
            if (existing.equals(account)) return existing;
        }
        return null;
    }

    public void remove(Account<?> account) {
        if (accounts.remove(account)) {
            account.getCache().releaseHead();
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
        for (Account<?> old : this.accounts) {
            old.getCache().releaseHead();
        }

        this.accounts.clear();
        if (accounts != null) this.accounts.addAll(accounts);
    }

    public List<Account<?>> getAccounts() {
        return accounts;
    }

}
