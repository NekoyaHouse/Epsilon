package com.github.epsilon.modules

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventPriority
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
                if (!nullCheck()) {
                    NotificationManager.moduleState(translatedName, notificationHash, false)
                }
                onDisable()
            }
        }

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
    // 监听器绑定到模块对象本身，是否派发由 isEnabled 的 EventBus.subscribe / unsubscribe 决定，
    // 因此禁用状态下不会再收到事件；依赖玩家/世界的监听器会在上下文缺失时自动跳过。
    // alwaysListening = true 用于确实需要在禁用期间继续监听的场景（如残留渲染效果淡出）。

    /**
     * 模块作用域的 [EventBus.nonNullHandler]。
     *
     * [alwaysListening] 为 false（默认）时监听器只在模块启用期间生效；为 true 时立即订阅并一直监听，
     * 处理器执行前仍按 [isEnabled] 过滤。
     */
    protected inline fun <reified E : Any> nonNullListener(
        priority: Int = EventPriority.MEDIUM,
        alwaysListening: Boolean = false,
        noinline block: context(NonNullContext) (E) -> Unit,
    ) {
        EventBus.nonNullHandler<E>(this, priority, alwaysListening) { event ->
            if (!alwaysListening || isEnabled) {
                block(event)
            }
        }
    }

    /** 模块作用域的 [EventBus.handler]，语义同 [nonNullListener]。 */
    protected inline fun <reified E : Any> handler(
        priority: Int = EventPriority.MEDIUM,
        alwaysListening: Boolean = false,
        noinline block: (E) -> Unit,
    ) {
        EventBus.handler<E>(this, priority, alwaysListening) { event ->
            if (!alwaysListening || isEnabled) {
                block(event)
            }
        }
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
