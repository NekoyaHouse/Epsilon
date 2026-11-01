package com.github.epsilon.modules

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.events.api.ListenerOwner
import com.github.epsilon.kernel.MinecraftWrapper
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.SettingHost
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

open class Module(
    val name: String,
    val category: Category?,
) : ListenerOwner(), SettingHost {

    enum class BindMode {
        Toggle,
        Hold,
    }

    var keyBind: Int = -1
    var bindMode: BindMode = BindMode.Toggle
    var isHidden: Boolean = true

    private val enableCallbacks = ArrayList<() -> Unit>()
    private val disableCallbacks = ArrayList<() -> Unit>()

    var isEnabled: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) subscribe() else unsubscribe()
            runSafe {
                NotificationManager.moduleState(this@Module.translatedName, notificationHash, value)
            }
            val callbacks = if (value) enableCallbacks else disableCallbacks
            for (callback in callbacks) {
                callback()
            }
        }

    var translateComponent: TranslateComponent? = null

    val settings: MutableList<Setting<*>> = ArrayList()
    val settingGroups: MutableList<SettingGroup> = ArrayList()

    protected val mc: Minecraft get() = MinecraftWrapper.mc

    private var defaultHidden = true
    private var defaultEnabled = false

    fun initI18n(moduleComponent: TranslateComponent) {
        translateComponent = moduleComponent
        for (group in settingGroups) {
            initGroupI18n(moduleComponent, group)
        }
        for (setting in settings) {
            setting.initTranslateComponent(moduleComponent.createChild(setting.name.lowercase()))
        }
    }

    /** 在 init 中注册启用回调；依赖世界的操作放入 runSafe，回调按注册顺序执行。 */
    protected fun onEnable(block: () -> Unit) {
        enableCallbacks.add(block)
    }

    /** 在 init 中注册禁用回调；不依赖世界的清理不能放入 runSafe，以保证退服后仍能执行。 */
    protected fun onDisable(block: () -> Unit) {
        disableCallbacks.add(block)
    }

    // ---- 事件注册 ----
    // 模块在 object 的 init 块中注册监听器（import com.github.epsilon.events.api.safeEventHandler / handler）：
    //   init { safeEventHandler<ClientTickEvent.Pre> { ... } }
    // 注册的隐式 owner 是模块；safeEventHandler 的 lambda receiver 是 NonNullContext。
    // 监听器是否派发由 isEnabled 的
    // subscribe() / unsubscribe() 决定，因此禁用状态下不会再收到事件；
    // 依赖玩家/世界的监听器会在上下文缺失时自动跳过。

    fun toggle() {
        isEnabled = !isEnabled
    }

    protected fun setDefaultEnabled(defaultEnabled: Boolean) {
        this.defaultEnabled = defaultEnabled
        isEnabled = defaultEnabled
    }

    protected fun setDefaultHidden(defaultHidden: Boolean) {
        this.defaultHidden = defaultHidden
        this.isHidden = defaultHidden
    }

    private val notificationHash: Int
        get() = "epsilon:$name".hashCode()

    open fun reset() {
        isEnabled = false
        keyBind = -1
        bindMode = BindMode.Toggle
        isHidden = defaultHidden
        resetCustomState()
        for (setting in settings) {
            setting.reset()
        }
        if (defaultEnabled) {
            isEnabled = true
        }
    }

    val translatedName: String
        get() = translateComponent?.getTranslatedName() ?: name

    open fun getInfo(): String? = null

    open fun resetCustomState() {
    }

    open fun saveCustomState(): JsonObject? = null

    open fun loadCustomState(state: JsonObject) {
    }

    override fun mutableSettings(): MutableList<Setting<*>> = settings

    override fun mutableSettingGroups(): MutableList<SettingGroup> = settingGroups

    private companion object {

        /** 递归初始化分组翻译组件；子分组 key 逐级拼接在父分组 key 之下。 */
        fun initGroupI18n(parentComponent: TranslateComponent, group: SettingGroup) {
            val component = parentComponent.createChild(group.name.lowercase())
            group.initTranslateComponent(component)
            for (child in group.children) {
                initGroupI18n(component, child)
            }
        }

    }

}
