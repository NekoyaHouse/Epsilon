package com.github.epsilon.settings

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.kernel.client.platform.PlatformRequirement
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 设置基类。
 *
 * 值本身以 `var value` 暴露（Java 侧即 `getValue()/setValue(...)`），写入会触发 [onChanged]。
 * 统一实现 [ReadWriteProperty]，所有设置（包括枚举）都可以直接委托：
 *
 * ```kotlin
 * private var mode by enumSetting("Mode", Mode.Default)
 * private val aimStrength by intSetting("Aim Strength", 10, 1..20, 1)
 * if (aimStrength > 10) { ... }
 * ```
 *
 * 属性类型就是值类型，不需要 import 任何委托扩展。
 */
abstract class Setting<V>(
    val name: String,
    defaultValue: V,
    /** 可见性条件；为 null 时始终可见。 */
    val visible: Visibility?,
    var onChanged: ((V) -> Unit)?,
) : ReadWriteProperty<Any?, V> {

    /** 设置当前值；默认写入后回调 [onChanged]（子类可覆盖 setter 做钳制/归一化）。 */
    open var value: V
        get() = valueStore
        set(newValue) = writeValue(newValue)

    /** 默认值，仅供子类在构造期归一化与 [reset] 使用。 */
    protected var defaultValue: V = defaultValue

    var isGlobalSetting: Boolean = false

    var groupRef: SettingGroup? = null

    var platformRequirement: PlatformRequirement by mutableStateOf(PlatformRequirement.ANY)

    var viaRequirement: ViaVersionRequirement? by mutableStateOf(null)

    var translateComponent: TranslateComponent? = null

    private var valueStore: V by mutableStateOf(defaultValue)

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

    fun isViaSupported(): Boolean = viaRequirement?.isSatisfied() ?: true

    fun isSupported(): Boolean = isPlatformSupported() && isViaSupported()

    /** 设置行禁用时的简短提示；点击后查看完整要求。 */
    val unsupportedLabel: String
        get() = if (isPlatformSupported() && !isViaSupported()) viaRequirement!!.displayName()
        else EpsilonTranslations.PlatformOnly.BADGE.getTranslatedName()

    open fun setValueSilently(newValue: V) {
        valueStore = newValue
    }

    open fun reset() {
        valueStore = defaultValue
    }

    /** 直接写入当前值并触发回调，绕过子类对 [value] 的重写（对应原 Java 的 `super.setValue`）。 */
    protected fun writeValue(newValue: V) {
        valueStore = newValue
        onChanged?.invoke(newValue)
        notifyChangeListeners(newValue)
    }

    /** 直接写入当前值且不触发回调。 */
    protected fun writeValueSilently(newValue: V) {
        valueStore = newValue
    }

    /** 构造期初始化默认值与当前值，避免触发 [onChanged]。 */
    protected fun initializeValue(initial: V) {
        defaultValue = initial
        valueStore = initial
    }

    /** recompose 用的变更监听；与 [onChanged] 独立，不改变既有的单回调语义。 */
    private var changeListeners: MutableList<(V) -> Unit>? = null

    fun addChangeListener(listener: (V) -> Unit) {
        val list = changeListeners ?: ArrayList<(V) -> Unit>(1).also { changeListeners = it }
        if (!list.contains(listener)) {
            list.add(listener)
        }
    }

    fun removeChangeListener(listener: (V) -> Unit) {
        changeListeners?.remove(listener)
    }

    private fun notifyChangeListeners(newValue: V) {
        val list = changeListeners ?: return
        for (index in list.indices.reversed()) {
            list[index].invoke(newValue)
        }
    }

    /** 供状态适配器读取当前值而不触发任何副作用。 */
    fun readCurrentValue(): V = valueStore

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        this.value = value
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
