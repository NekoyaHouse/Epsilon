package com.github.epsilon.settings;

import com.github.epsilon.settings.impl.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public interface SettingHost {

    List<Setting<?>> mutableSettings();

    List<SettingGroup> mutableSettingGroups();

    default <T extends Setting<?>> T addSetting(T setting) {
        mutableSettings().add(setting);
        return setting;
    }

    default SettingGroup settingGroup(String name) {
        for (SettingGroup group : mutableSettingGroups()) {
            if (group.getName().equalsIgnoreCase(name)) {
                return group;
            }
        }
        SettingGroup group = new SettingGroup(name);
        mutableSettingGroups().add(group);
        return group;
    }

    default IntSetting intSetting(String name, int defaultValue, int min, int max, int step) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, () -> true, null));
    }

    default IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Consumer<Integer> onChanged) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, () -> true, onChanged));
    }

    default IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, dependency, null));
    }

    default IntSetting intSetting(String name, int defaultValue, int min, int max, int step,
                                  Setting.Dependency dependency, Consumer<Integer> onChanged) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, dependency, onChanged));
    }

    default BoolSetting boolSetting(String name, boolean defaultValue, Setting.Dependency dependency) {
        return addSetting(new BoolSetting(name, defaultValue, dependency, null));
    }

    default BoolSetting boolSetting(String name, boolean defaultValue) {
        return addSetting(new BoolSetting(name, defaultValue, () -> true, null));
    }

    default BoolSetting boolSetting(String name, boolean defaultValue, Setting.Dependency dependency,
                                    Consumer<Boolean> onChanged) {
        return addSetting(new BoolSetting(name, defaultValue, dependency, onChanged));
    }

    default BoolSetting boolSetting(String name, boolean defaultValue, Consumer<Boolean> onChanged) {
        return addSetting(new BoolSetting(name, defaultValue, () -> true, onChanged));
    }

    default DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, () -> true, null));
    }

    default DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step,
                                        Setting.Dependency dependency) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, dependency, null));
    }

    default DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step,
                                        Consumer<Double> onChanged) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, () -> true, onChanged));
    }

    default DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step,
                                        Setting.Dependency dependency, Consumer<Double> onChanged) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, dependency, onChanged));
    }

    default StringSetting stringSetting(String name, String defaultValue, Setting.Dependency dependency) {
        return addSetting(new StringSetting(name, defaultValue, dependency));
    }

    default StringSetting stringSetting(String name, String defaultValue, Setting.Dependency dependency,
                                        Consumer<String> onChanged) {
        return addSetting(new StringSetting(name, defaultValue, dependency, onChanged));
    }

    default StringSetting stringSetting(String name, String defaultValue) {
        return addSetting(new StringSetting(name, defaultValue, () -> true));
    }

    default StringSetting stringSetting(String name, String defaultValue, Consumer<String> onChanged) {
        return addSetting(new StringSetting(name, defaultValue, () -> true, onChanged));
    }

    default RegistryListSetting<Block> blockListSetting(String name, Collection<Block> defaultValue,
                                                        Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.BLOCK, null, dependency));
    }

    default RegistryListSetting<Block> blockListSetting(String name, Collection<Block> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.BLOCK, null, () -> true));
    }

    default RegistryListSetting<Item> itemListSetting(String name, Collection<Item> defaultValue,
                                                      Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ITEM, null, dependency));
    }

    default RegistryListSetting<Item> itemListSetting(String name, Collection<Item> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ITEM, null, () -> true));
    }

    default RegistryListSetting<EntityType<?>> entityTypeListSetting(String name, Collection<EntityType<?>> defaultValue,
                                                                     Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ENTITY_TYPE, null, dependency));
    }

    default RegistryListSetting<EntityType<?>> entityTypeListSetting(String name, Collection<EntityType<?>> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ENTITY_TYPE, null, () -> true));
    }

    default RegistryListSetting<String> enchantmentListSetting(String name, Collection<String> defaultValue,
                                                               Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ENCHANTMENT, null, dependency));
    }

    default RegistryListSetting<String> enchantmentListSetting(String name, Collection<String> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ENCHANTMENT, null, () -> true));
    }

    default <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue,
                                                           Setting.Dependency dependency, Consumer<E> onChanged) {
        return addSetting(new EnumSetting<>(name, defaultValue, dependency, onChanged));
    }

    default <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue, Consumer<E> onChanged) {
        return addSetting(new EnumSetting<>(name, defaultValue, () -> true, onChanged));
    }

    default <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue,
                                                           Setting.Dependency dependency) {
        return addSetting(new EnumSetting<>(name, defaultValue, dependency, null));
    }

    default <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue) {
        return addSetting(new EnumSetting<>(name, defaultValue, () -> true, null));
    }

    default ColorSetting colorSetting(String name, Color defaultValue, boolean allowAlpha,
                                      Setting.Dependency dependency) {
        return addSetting(new ColorSetting(name, defaultValue, allowAlpha, dependency));
    }

    default ColorSetting colorSetting(String name, Color defaultValue, Setting.Dependency dependency) {
        return addSetting(new ColorSetting(name, defaultValue, true, dependency));
    }

    default ColorSetting colorSetting(String name, Color defaultValue, boolean allowAlpha) {
        return addSetting(new ColorSetting(name, defaultValue, allowAlpha, () -> true));
    }

    default ColorSetting colorSetting(String name, Color defaultValue) {
        return addSetting(new ColorSetting(name, defaultValue, true, () -> true));
    }

    default KeybindSetting keybindSetting(String name, int defaultValue, Setting.Dependency dependency) {
        return addSetting(new KeybindSetting(name, defaultValue, dependency));
    }

    default KeybindSetting keybindSetting(String name, int defaultValue) {
        return addSetting(new KeybindSetting(name, defaultValue, () -> true));
    }

    default ButtonSetting buttonSetting(String name, Runnable func, Setting.Dependency dependency) {
        return addSetting(new ButtonSetting(name, func, dependency));
    }

    default ButtonSetting buttonSetting(String name, Runnable func) {
        return addSetting(new ButtonSetting(name, func, () -> true));
    }

    default StringListSetting stringListSetting(String name, Collection<String> defaultValue,
                                                Setting.Dependency dependency) {
        return addSetting(new StringListSetting(name, defaultValue, dependency));
    }

    default StringListSetting stringListSetting(String name, Collection<String> defaultValue) {
        return addSetting(new StringListSetting(name, defaultValue, () -> true));
    }

    default RegistryListSetting<SoundEvent> soundEventListSetting(String name, Collection<SoundEvent> defaultValue,
                                                                  Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, dependency));
    }

    default RegistryListSetting<SoundEvent> soundEventListSetting(String name, Collection<SoundEvent> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, () -> true));
    }
}
