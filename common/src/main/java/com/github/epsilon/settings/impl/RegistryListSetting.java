package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import com.github.epsilon.utils.world.BlockRegistryUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class RegistryListSetting<T> extends Setting<List<T>> {

    public enum Type {
        BLOCK,
        ITEM,
        ENTITY_TYPE,
        SOUND_EVENT,
        ENCHANTMENT;

        @SuppressWarnings("unchecked")
        <T> Registry<T> registry() {
            return (Registry<T>) switch (this) {
                case BLOCK -> BuiltInRegistries.BLOCK;
                case ITEM -> BuiltInRegistries.ITEM;
                case ENTITY_TYPE -> BuiltInRegistries.ENTITY_TYPE;
                case SOUND_EVENT -> BuiltInRegistries.SOUND_EVENT;
                case ENCHANTMENT -> null;
            };
        }

        @SuppressWarnings("unchecked")
        <T> Predicate<T> defaultFilter() {
            return (Predicate<T>) switch (this) {
                case BLOCK -> (Predicate<Block>) BlockRegistryUtils::isSelectable;
                case ITEM -> (Predicate<Item>) item -> item != null && item != Items.AIR;
                case ENTITY_TYPE -> (Predicate<EntityType<?>>) entityType -> entityType != null;
                case SOUND_EVENT -> (Predicate<SoundEvent>) sound -> sound != null;
                case ENCHANTMENT -> (Predicate<String>) id -> id != null && Identifier.tryParse(id) != null;
            };
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        String toId(Object entry) {
            if (this == ENCHANTMENT) {
                return entry instanceof String id ? id : "";
            }
            Registry registry = registry();
            if (registry == null) {
                return "";
            }
            Identifier key = registry.getKey(entry);
            return key != null ? key.toString() : "";
        }

        @SuppressWarnings("unchecked")
        <T> T fromId(String id) {
            Identifier loc = Identifier.tryParse(id);
            if (loc == null) {
                return null;
            }
            if (this == ENCHANTMENT) {
                return (T) loc.toString();
            }
            Registry<T> registry = registry();
            return registry == null ? null : registry.getOptional(loc).orElse(null);
        }
    }

    private final Type registryType;
    private final Registry<T> registry;
    private final Predicate<T> filter;

    public RegistryListSetting(String name, Collection<T> defaultValue, Type registryType,
                               Predicate<T> filter, Dependency dependency) {
        super(name, dependency, null);
        this.registryType = registryType;
        this.registry = registryType.registry();
        Predicate<T> defaultFilter = registryType.defaultFilter();
        this.filter = filter != null ? filter : defaultFilter;
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<T> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<T> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(T entry) {
        return value.contains(entry);
    }

    public void add(T entry) {
        if (!canUse(entry) || value.contains(entry)) return;
        List<T> next = new ArrayList<>(value);
        next.add(entry);
        setValue(next);
    }

    public void remove(T entry) {
        if (!value.contains(entry)) return;
        List<T> next = new ArrayList<>(value);
        next.remove(entry);
        setValue(next);
    }

    public void toggle(T entry) {
        if (contains(entry)) {
            remove(entry);
        } else {
            add(entry);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (T entry : value) {
            String id = registryType.toId(entry);
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<T> entries = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                T entry = registryType.fromId(id);
                if (canUse(entry) && !entries.contains(entry)) {
                    entries.add(entry);
                }
            }
        }
        setValue(entries);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Type getRegistryType() {
        return registryType;
    }

    public Registry<T> getRegistry() {
        return registry;
    }

    public Predicate<T> getFilter() {
        return filter;
    }

    private List<T> normalize(Collection<T> source) {
        if (source == null) return new ArrayList<>();
        List<T> result = new ArrayList<>();
        for (T entry : source) {
            if (canUse(entry) && !result.contains(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    private boolean canUse(T entry) {
        return entry != null && (filter == null || filter.test(entry));
    }
}
