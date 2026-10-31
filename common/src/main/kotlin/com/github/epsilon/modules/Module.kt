package com.github.epsilon.modules

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.bus.listeners.IListener
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.SettingHost
import com.github.epsilon.utils.safety.*
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

/**
 * 模块基类。
 *
 * 保留原 Java 版本的 getter 语义（`getName`/`getCategory`/`isEnabled`/`setEnabled` 等），
 * 以便 ModuleManager、GUI 等尚未迁移的 Java 调用方逐步过渡。
 */
open class Module(
    val name: String,
    val category: Category?,
) : SettingHost {

    enum class BindMode {
        Toggle,
        Hold,
    }

    var keyBind: Int = -1
    var bindMode: BindMode = BindMode.Toggle
    var isHidden: Boolean = true

    var isEnabled: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                EventBus.subscribe(this)
                if (!nullCheck()) {
                    NotificationManager.moduleState(translatedName, notificationHash, true)
                }
                onEnable()
            } else {
                EventBus.unsubscribe(this)
                dynamicListeners.forEach(EventBus::unsubscribe)
                dynamicListeners.clear()
                if (!nullCheck()) {
                    NotificationManager.moduleState(translatedName, notificationHash, false)
                }
                onDisable()
            }
        }

    /** 运行期动态注册、需要在禁用时统一注销的监听器（例如按条件订阅的渲染事件）。 */
    private val dynamicListeners: MutableList<IListener> = ArrayList()

    var translateComponent: TranslateComponent? = null

    val settings: MutableList<Setting<*>> = ArrayList()
    val settingGroups: MutableList<SettingGroup> = ArrayList()

    protected val mc: Minecraft = Minecraft.getInstance()

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

    protected fun nullCheck(): Boolean = mc.player == null || mc.level == null

    protected open fun onEnable() {
    }

    protected open fun onDisable() {
    }

    // ---- 事件注册 ----
    // 模块在 object 的 init 块中声明监听器：
    //   init { nonNullListener<ClientTickEvent.Pre> { ... } }
    // 监听器在模块对象初始化时注册一次，事件派发时按 isEnabled 过滤，
    // 因此禁用状态下不会执行，与原先「启用才 subscribe」的行为一致；
    // 依赖玩家/世界的监听器会在上下文缺失时自动跳过。

    /** [EventBus.nonNullHandler] 的模块作用域版本，额外按 [isEnabled] 过滤。 */
    protected inline fun <reified E : Any> nonNullListener(
        priority: Int = EventPriority.MEDIUM,
        noinline block: context(NonNullContext) (E) -> Unit,
    ) {
        EventBus.nonNullHandler<E>(priority) { event ->
            if (isEnabled) {
                block(event)
            }
        }
    }

    /** 不依赖世界的事件监听器（菜单、渲染、按键等），额外按 [isEnabled] 过滤。 */
    protected inline fun <reified E : Any> handler(
        priority: Int = EventPriority.MEDIUM,
        noinline block: (E) -> Unit,
    ) {
        EventBus.handler<E>(priority) { event ->
            if (isEnabled) {
                block(event)
            }
        }
    }

    /**
     * 跟随模块启用状态注销的动态监听器（在 onEnable 中注册）。
     */
    protected fun <T : IListener> trackListener(listener: T): T {
        dynamicListeners += listener
        return listener
    }

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
