package com.github.epsilon.settings

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.utils.client.PlatformRequirement
import kotlin.reflect.KProperty

/**
 * 设置基类。
 *
 * 值本身以 `var value` 暴露（Java 侧即 `getValue()/setValue(...)`），写入会触发 [onChanged]。
 * 同时提供**成员**委托运算符，因此 Kotlin 侧可以写成：
 *
 * ```kotlin
 * private val aimStrength by intSetting("Aim Strength", 10, 1..20, 1)
 * if (aimStrength > 10) { ... }
 * ```
 *
 * 属性类型就是值类型，不需要 import 任何委托扩展。
 */
abstract class Setting<V>(
    val name: String,
    /** 可见性条件；为 null 时始终可见。 */
    val visible: Visibility?,
    var onChanged: ((V) -> Unit)?,
) {

    /** 设置当前值；默认写入后回调 [onChanged]（子类可覆盖 setter 做钳制/归一化）。 */
    open var value: V
        get() = readValue()
        set(newValue) = writeValue(newValue)

    /** 默认值，仅供子类在构造期写入与 [reset] 使用。 */
    protected var defaultValue: V
        get() = readDefaultValue()
        set(newValue) {
            defaultValueStore = newValue
        }

    var isGlobalSetting: Boolean = false

    var groupRef: SettingGroup? = null

    var platformRequirement: PlatformRequirement = PlatformRequirement.ANY

    var translateComponent: TranslateComponent? = null

    // 泛型值用 Any? 存储：Java 版本同样是未初始化的泛型字段，子类构造期写入。
    private var valueStore: Any? = null

    private var defaultValueStore: Any? = null

    val displayName: String
        get() = translateComponent?.getTranslatedName() ?: name

    /**
     * 可跨会话识别该设置的稳定 key，用于平台不支持提示去重。
     */
    val noticeKey: String
        get() = translateComponent?.getFullKey() ?: name

    /** 当前是否满足可见性条件。 */
    val isVisible: Boolean get() = visible?.check() ?: true

    open fun initTranslateComponent(component: TranslateComponent) {
        this.translateComponent = component
    }

    /**
     * 当前平台是否满足该设置的功能要求。
     */
    fun isPlatformSupported(): Boolean = platformRequirement.isSatisfied()

    open fun setValueSilently(newValue: V) {
        valueStore = newValue
    }

    open fun reset() {
        valueStore = defaultValueStore
    }

    /** 直接写入当前值并触发回调，绕过子类对 [value] 的重写（对应原 Java 的 `super.setValue`）。 */
    protected fun writeValue(newValue: V) {
        valueStore = newValue
        onChanged?.invoke(newValue)
    }

    /** 直接写入当前值且不触发回调。 */
    protected fun writeValueSilently(newValue: V) {
        valueStore = newValue
    }

    /** 构造期初始化默认值与当前值，避免触发 [onChanged]。 */
    protected fun initializeValue(initial: V) {
        defaultValueStore = initial
        valueStore = initial
    }

    /** 构造期单独写入默认值（当前值另行 [initializeCurrent]）。 */
    protected fun initializeDefault(initial: V) {
        defaultValueStore = initial
    }

    /** 构造期单独写入当前值。 */
    protected fun initializeCurrent(current: V) {
        valueStore = current
    }

    @Suppress("UNCHECKED_CAST")
    private fun readValue(): V = valueStore as V

    @Suppress("UNCHECKED_CAST")
    private fun readDefaultValue(): V = defaultValueStore as V

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: V) {
        value = newValue
    }

    /** 设置可见性条件。 */
    @FunctionalInterface
    fun interface Visibility {
        fun check(): Boolean
    }

}

/**
 * 将设置归入指定分组，并保留具体设置类型以支持后续链式调用。
 */
fun <T : Setting<*>> T.group(group: SettingGroup): T {
    this.groupRef = group
    return this
}

/**
 * 声明设置只在指定平台上可用，并保留具体设置类型。
 */
fun <T : Setting<*>> T.platformOnly(requirement: PlatformRequirement?): T {
    this.platformRequirement = requirement ?: PlatformRequirement.ANY
    return this
}

/**
 * 将设置标记为全局设置，并保留具体设置类型。
 */
fun <T : Setting<*>> T.globalSetting(): T {
    this.isGlobalSetting = true
    return this
}
