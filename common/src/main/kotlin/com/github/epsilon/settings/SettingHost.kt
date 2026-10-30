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
 * 依赖条件位于参数最后且带默认值，因此可以直接用尾随 lambda 表达：
 *
 * ```kotlin
 * private val boxOutline by boolSetting("Box Outline", true) { renderBox }
 * private val onlyWeapon by boolSetting("Only Weapon", true, dependency = { mode == SwingMode.Flux })
 * ```
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

    fun intSetting(
        name: String, defaultValue: Int, min: Int, max: Int, step: Int,
        onChanged: ((Int) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): IntSetting = addSetting(IntSetting(name, defaultValue, min, max, step, dependency, onChanged))

    fun boolSetting(
        name: String, defaultValue: Boolean,
        onChanged: ((Boolean) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): BoolSetting = addSetting(BoolSetting(name, defaultValue, dependency, onChanged))

    fun doubleSetting(
        name: String, defaultValue: Double, min: Double, max: Double, step: Double,
        onChanged: ((Double) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): DoubleSetting = addSetting(DoubleSetting(name, defaultValue, min, max, step, dependency, onChanged))

    fun stringSetting(
        name: String, defaultValue: String,
        onChanged: ((String) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): StringSetting = addSetting(StringSetting(name, defaultValue, dependency, onChanged))

    fun blockListSetting(
        name: String, defaultValue: Collection<Block>, dependency: Setting.Dependency = { true },
    ): RegistryListSetting<Block> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.BLOCK, null, dependency))

    fun itemListSetting(
        name: String, defaultValue: Collection<Item>, dependency: Setting.Dependency = { true },
    ): RegistryListSetting<Item> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ITEM, null, dependency))

    fun entityTypeListSetting(
        name: String, defaultValue: Collection<EntityType<*>>, dependency: Setting.Dependency = { true },
    ): RegistryListSetting<EntityType<*>> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ENTITY_TYPE, null, dependency))

    fun enchantmentListSetting(
        name: String, defaultValue: Collection<String>, dependency: Setting.Dependency = { true },
    ): RegistryListSetting<String> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ENCHANTMENT, null, dependency))

    fun <E : Enum<E>> enumSetting(
        name: String, defaultValue: E,
        onChanged: ((E) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): EnumSetting<E> = addSetting(EnumSetting(name, defaultValue, dependency, onChanged))

    fun colorSetting(
        name: String, defaultValue: Color, allowAlpha: Boolean = true,
        onChanged: ((Color) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): ColorSetting = addSetting(ColorSetting(name, defaultValue, allowAlpha, dependency))

    fun keybindSetting(
        name: String, defaultValue: Int,
        onChanged: ((Int) -> Unit)? = null, dependency: Setting.Dependency = { true },
    ): KeybindSetting = addSetting(KeybindSetting(name, defaultValue, dependency))

    fun buttonSetting(
        name: String, func: Runnable, dependency: Setting.Dependency = { true },
    ): ButtonSetting = addSetting(ButtonSetting(name, func, dependency))

    fun stringListSetting(
        name: String, defaultValue: Collection<String>, dependency: Setting.Dependency = { true },
    ): StringListSetting = addSetting(StringListSetting(name, defaultValue, dependency))

    fun soundEventListSetting(
        name: String, defaultValue: Collection<SoundEvent>, dependency: Setting.Dependency = { true },
    ): RegistryListSetting<SoundEvent> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, dependency))

}
