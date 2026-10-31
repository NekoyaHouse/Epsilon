package com.github.epsilon.settings

import kotlin.reflect.KProperty

/**
 * 设置属性的 Kotlin 委托：直接暴露设置的值，读写都不需要经过 `.value`。
 *
 * ```kotlin
 * private var aimStrength by intSetting("Aim Strength", 10, 1, 20, 1)
 *
 * fun use() {
 *     if (aimStrength > 10) aimStrength = 10
 * }
 * ```
 *
 * 设置对象本身仍由 `SettingHost.intSetting(...)` 等在创建时注册到
 * [SettingHost.mutableSettings] 列表，GUI 与配置序列化继续按列表访问。
 *
 * 使用方需要在文件头 `import com.github.epsilon.settings.getValue` 与
 * （需要写入时）`import com.github.epsilon.settings.setValue`。
 */
operator fun <T> Setting<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

/**
 * 变体：属性声明为 `var ... by ...` 时使用。
 */
operator fun <T> Setting<T>.setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
    value = newValue
}
