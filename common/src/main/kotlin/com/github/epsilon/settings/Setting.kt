package com.github.epsilon.settings

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.kernel.client.platform.PlatformRequirement
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Setting<V>(
    val name: String,
    defaultValue: V,
    val visible: Visibility?,
    var onChanged: ((V) -> Unit)?,
) : ReadWriteProperty<Any?, V> {

    open var value: V
        get() = valueStore
        set(newValue) = writeValue(newValue)

    // 默认值，仅供子类在构造期归一化与 [reset] 使用。
    protected var defaultValue: V = defaultValue

    var isGlobalSetting: Boolean = false

    var isApplyWhenRelease: Boolean = false

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

    /** 直接写入当前值并触发回调，绕过子类对 [value] 的重写。 */
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
        defaultValue = initial
        valueStore = initial
    }

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

/**
 * 将设置标记为只在 UI 交互结束时应用，并保留具体设置类型。
 */
fun <T : Setting<*>> T.applyWhenRelease(): T {
    this.isApplyWhenRelease = true
    return this
}
