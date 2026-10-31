package com.github.epsilon.settings

import com.github.epsilon.settings.impl.*
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import java.awt.Color

/**
 * 设置宿主：模块/HUD 通过这里声明的 DSL 创建并注册设置。
 *
 * 数值设置使用 Kotlin 区间表达范围，可见性条件直接写在参数列表内：
 *
 * ```kotlin
 * private val range by doubleSetting("Range", 3.0, 0.0..6.0, 0.1)
 * private val onlyWeapon by boolSetting("Only Weapon", true, { mode == SwingMode.Flux })
 * ```
 *
 * 所有工厂方法都会把新建设置加入 [mutableSettings]，因此 Kotlin 侧可以直接用属性委托：
 *
 * ```kotlin
 * private val aimStrength by intSetting("Aim Strength", 10, 1..20, 1)
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
        name: String,
        defaultValue: Int,
        range: IntRange,
        step: Int,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((Int) -> Unit)? = null,
    ): IntSetting = addSetting(IntSetting(name, defaultValue, range, step, visible, onChanged))

    fun boolSetting(
        name: String,
        defaultValue: Boolean,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((Boolean) -> Unit)? = null,
    ): BoolSetting = addSetting(BoolSetting(name, defaultValue, visible, onChanged))

    fun doubleSetting(
        name: String,
        defaultValue: Double,
        range: ClosedFloatingPointRange<Double>,
        step: Double,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((Double) -> Unit)? = null,
    ): DoubleSetting = addSetting(DoubleSetting(name, defaultValue, range, step, visible, onChanged))

    fun stringSetting(
        name: String,
        defaultValue: String,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((String) -> Unit)? = null,
    ): StringSetting = addSetting(StringSetting(name, defaultValue, visible, onChanged))

    fun blockListSetting(
        name: String,
        defaultValue: Collection<Block>,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): RegistryListSetting<Block> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.BLOCK, null, visible))

    fun itemListSetting(
        name: String,
        defaultValue: Collection<Item>,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): RegistryListSetting<Item> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ITEM, null, visible))

    fun entityTypeListSetting(
        name: String,
        defaultValue: Collection<EntityType<*>>,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): RegistryListSetting<EntityType<*>> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ENTITY_TYPE, null, visible))

    fun enchantmentListSetting(
        name: String,
        defaultValue: Collection<String>,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): RegistryListSetting<String> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.ENCHANTMENT, null, visible))

    fun <E : Enum<E>> enumSetting(
        name: String,
        defaultValue: E,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((E) -> Unit)? = null,
    ): EnumSetting<E> = addSetting(EnumSetting(name, defaultValue, visible, onChanged))

    fun colorSetting(
        name: String,
        defaultValue: Color,
        allowAlpha: Boolean = true,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((Color) -> Unit)? = null,
    ): ColorSetting = addSetting(ColorSetting(name, defaultValue, allowAlpha, visible))

    fun keybindSetting(
        name: String,
        defaultValue: Int,
        visible: Setting.Visibility = Setting.Visibility { true },
        onChanged: ((Int) -> Unit)? = null,
    ): KeybindSetting = addSetting(KeybindSetting(name, defaultValue, visible))

    fun buttonSetting(
        name: String,
        func: Runnable,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): ButtonSetting = addSetting(ButtonSetting(name, func, visible))

    fun stringListSetting(
        name: String,
        defaultValue: Collection<String>,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): StringListSetting = addSetting(StringListSetting(name, defaultValue, visible))

    fun soundEventListSetting(
        name: String,
        defaultValue: Collection<SoundEvent>,
        visible: Setting.Visibility = Setting.Visibility { true },
    ): RegistryListSetting<SoundEvent> =
        addSetting(RegistryListSetting(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, visible))

}
