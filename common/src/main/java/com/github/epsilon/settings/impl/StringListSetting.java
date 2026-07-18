package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StringListSetting extends Setting<List<String>> {

    public StringListSetting(String name, Collection<String> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<String> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<String> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(String entry) {
        return value.contains(entry);
    }

    public void add(String entry) {
        if (entry == null || value.contains(entry)) return;
        List<String> next = new ArrayList<>(value);
        next.add(entry);
        setValue(next);
    }

    public void remove(String entry) {
        if (!value.contains(entry)) return;
        List<String> next = new ArrayList<>(value);
        next.remove(entry);
        setValue(next);
    }

    public void toggle(String entry) {
        if (contains(entry)) {
            remove(entry);
        } else {
            add(entry);
        }
    }

    public String get(int index) {
        return getValue().get(index);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private List<String> normalize(Collection<String> source) {
        if (source == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String entry : source) {
            if (entry != null && !result.contains(entry)) {
                result.add(entry);
            }
        }
        return result;
    }
}
