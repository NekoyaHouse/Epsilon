package com.github.epsilon.settings

import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.settings.impl.StringSetting
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import java.awt.Color

/**
 * 设置宿主：模块/HUD 通过这里声明的 DSL 创建并注册设置。
 *
 * 所有工厂方法都会把新建设置加入 [mutableSettings]，因此 Kotlin 侧可以直接用属性委托：
 *
 * ```kotlin
 * private val range by doubleSetting("Range", 3.0, 0.0, 6.0, 0.1)
 * ```
 */
interface SettingHost {

    fun mutableSettings(): MutableList<Setting<*>>

    fun mutableSettingGroups(): MutableList<SettingGroup>

    fun <T : Setting<*>> addSetting(setting: T): T {
        mutableSettings().add(setting)
        return setting
    }

    fun settingGroup(name: String): SettingGroup {
        for (group in mutableSettingGroups()) {
            if (group.name.equals(name, ignoreCase = true)) {
                return group
            }
        }
        val group = SettingGroup(name)
        mutableSettingGroups().add(group)
        return group
    }

    fun intSetting(name: String, defaultValue: Int, min: Int, max: Int, step: Int): IntSetting =
        addSetting(IntSetting(name, defaultValue, min, max, step, Setting.Dependency { true }, null))

    fun intSetting(
        name: String, defaultValue: Int, min: Int, max: Int, step: Int, onChanged: (Int) -> Unit,
    ): IntSetting =
        addSetting(IntSetting(name, defaultValue, min, max, step, Setting.Dependency { true }, onChanged))

    fun intSetting(
        name: String, defaultValue: Int, min: Int, max: Int, step: Int, dependency: Setting.Dependency,
    ): IntSetting =
        addSetting(IntSetting(name, defaultValue, min, max, step, dependency, null))

    fun intSetting(
        name: String, defaultValue: Int, min: Int, max: Int, step: Int,
        dependency: Setting.Dependency, onChanged: (Int) -> Unit,
    ): IntSetting =
        addSetting(IntSetting(name, defaultValue, min, max, step, dependency, onChanged))

    fun boolSetting(name: String, defaultValue: Boolean, dependency: Setting.Dependency): BoolSetting =
        addSetting(BoolSetting(name, defaultValue, dependency, null))

    fun boolSetting(name: String, defaultValue: Boolean): BoolSetting =
        addSetting(BoolSetting(name, defaultValue, Setting.Dependency { true }, null))

    fun boolSetting(
        name: String, defaultValue: Boolean, dependency: Setting.Dependency, onChanged: (Boolean) -> Unit,
    ): BoolSetting =
        addSetting(BoolSetting(name, defaultValue, dependency, onChanged))

    fun boolSetting(name: String, defaultValue: Boolean, onChanged: (Boolean) -> Unit): BoolSetting =
        addSetting(BoolSetting(name, defaultValue, Setting.Dependency { true }, onChanged))

    fun doubleSetting(
        name: String, defaultValue: Double, min: Double, max: Double, step: Double,
    ): DoubleSetting =
        addSetting(DoubleSetting(name, defaultValue, min, max, step, Setting.Dependency { true }, null))

    fun doubleSetting(
        name: String, defaultValue: Double, min: Double, max: Double, step: Double,
        dependency: Setting.Dependency,
    ): DoubleSetting =
        addSetting(DoubleSetting(name, defaultValue, min, max, step, dependency, null))

    fun doubleSetting(
        name: String, defaultValue: Double, min: Double, max: Double, step: Double,
        onChanged: (Double) -> Unit,
    ): DoubleSetting =
        addSetting(DoubleSetting(name, defaultValue, min, max, step, Setting.Dependency { true }, onChanged))

    fun doubleSetting(
        name: String, defaultValue: Double, min: Double, max: Double, step: Double,
        dependency: Setting.Dependency, onChanged: (Double) -> Unit,
    ): DoubleSetting =
        addSetting(DoubleSetting(name, defaultValue, min, max, step, dependency, onChanged))

    fun stringSetting(name: String, defaultValue: String, dependency: Setting.Dependency): StringSetting =
        addSetting(StringSetting(name, defaultValue, dependency))

    fun stringSetting(
        name: String, defaultValue: String, dependency: Setting.Dependency, onChanged: (String) -> Unit,
    ): StringSetting =
        addSetting(StringSetting(name, defaultValue, dependency, onChanged))

    fun stringSetting(name: String, defaultValue: String): StringSetting =
        addSetting(StringSetting(name, defaultValue, Setting.Dependency { true }))

    fun stringSetting(name: String, defaultValue: String, onChanged: (String) -> Unit): StringSetting =
        addSetting(StringSetting(name, defaultValue, Setting.Dependency { true }, onChanged))

    fun blockListSetting(
        name: String, defaultValue: Collection<Block>, dependency: Setting.Dependency,
    ): RegistryListSetting<Block> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.BLOCK, null, dependency))

    fun blockListSetting(name: String, defaultValue: Collection<Block>): RegistryListSetting<Block> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.BLOCK, null, Setting.Dependency { true }))

    fun itemListSetting(
        name: String, defaultValue: Collection<Item>, dependency: Setting.Dependency,
    ): RegistryListSetting<Item> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ITEM, null, dependency))

    fun itemListSetting(name: String, defaultValue: Collection<Item>): RegistryListSetting<Item> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ITEM, null, Setting.Dependency { true }))

    fun entityTypeListSetting(
        name: String, defaultValue: Collection<EntityType<*>>, dependency: Setting.Dependency,
    ): RegistryListSetting<EntityType<*>> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ENTITY_TYPE, null, dependency))

    fun entityTypeListSetting(name: String, defaultValue: Collection<EntityType<*>>): RegistryListSetting<EntityType<*>> =
        addSetting(
            RegistryListSetting(
                name, defaultValue, RegistryListSetting.Type.ENTITY_TYPE, null, Setting.Dependency { true }
            )
        )

    fun enchantmentListSetting(
        name: String, defaultValue: Collection<String>, dependency: Setting.Dependency,
    ): RegistryListSetting<String> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ENCHANTMENT, null, dependency))

    fun enchantmentListSetting(name: String, defaultValue: Collection<String>): RegistryListSetting<String> =
        addSetting(
            RegistryListSetting(
                name, defaultValue, RegistryListSetting.Type.ENCHANTMENT, null, Setting.Dependency { true }
            )
        )

    fun <E : Enum<E>> enumSetting(
        name: String, defaultValue: E, dependency: Setting.Dependency, onChanged: (E) -> Unit,
    ): EnumSetting<E> = addSetting(EnumSetting(name, defaultValue, dependency, onChanged))

    fun <E : Enum<E>> enumSetting(name: String, defaultValue: E, onChanged: (E) -> Unit): EnumSetting<E> =
        addSetting(EnumSetting(name, defaultValue, Setting.Dependency { true }, onChanged))

    fun <E : Enum<E>> enumSetting(
        name: String, defaultValue: E, dependency: Setting.Dependency,
    ): EnumSetting<E> = addSetting(EnumSetting(name, defaultValue, dependency, null))

    fun <E : Enum<E>> enumSetting(name: String, defaultValue: E): EnumSetting<E> =
        addSetting(EnumSetting(name, defaultValue, Setting.Dependency { true }, null))

    fun colorSetting(
        name: String, defaultValue: Color, allowAlpha: Boolean, dependency: Setting.Dependency,
    ): ColorSetting = addSetting(ColorSetting(name, defaultValue, allowAlpha, dependency))

    fun colorSetting(name: String, defaultValue: Color, dependency: Setting.Dependency): ColorSetting =
        addSetting(ColorSetting(name, defaultValue, true, dependency))

    fun colorSetting(name: String, defaultValue: Color, allowAlpha: Boolean): ColorSetting =
        addSetting(ColorSetting(name, defaultValue, allowAlpha, Setting.Dependency { true }))

    fun colorSetting(name: String, defaultValue: Color): ColorSetting =
        addSetting(ColorSetting(name, defaultValue, true, Setting.Dependency { true }))

    fun keybindSetting(name: String, defaultValue: Int, dependency: Setting.Dependency): KeybindSetting =
        addSetting(KeybindSetting(name, defaultValue, dependency))

    fun keybindSetting(name: String, defaultValue: Int): KeybindSetting =
        addSetting(KeybindSetting(name, defaultValue, Setting.Dependency { true }))

    fun buttonSetting(name: String, func: Runnable, dependency: Setting.Dependency): ButtonSetting =
        addSetting(ButtonSetting(name, func, dependency))

    fun buttonSetting(name: String, func: Runnable): ButtonSetting =
        addSetting(ButtonSetting(name, func, Setting.Dependency { true }))

    fun stringListSetting(
        name: String, defaultValue: Collection<String>, dependency: Setting.Dependency,
    ): StringListSetting = addSetting(StringListSetting(name, defaultValue, dependency))

    fun stringListSetting(name: String, defaultValue: Collection<String>): StringListSetting =
        addSetting(StringListSetting(name, defaultValue, Setting.Dependency { true }))

    fun soundEventListSetting(
        name: String, defaultValue: Collection<SoundEvent>, dependency: Setting.Dependency,
    ): RegistryListSetting<SoundEvent> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, dependency))

    fun soundEventListSetting(name: String, defaultValue: Collection<SoundEvent>): RegistryListSetting<SoundEvent> =
        addSetting(
            RegistryListSetting(
                name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, Setting.Dependency { true }
            )
        )

}
