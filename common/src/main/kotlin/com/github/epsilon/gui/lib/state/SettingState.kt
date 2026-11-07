package com.github.epsilon.gui.lib.state

import com.github.epsilon.settings.Setting
import java.util.WeakHashMap

/**
 * 把 [Setting] 适配成可订阅状态。
 *
 * - 读取走状态缓存，recompose 期间会登记依赖；
 * - 写入回写 `Setting.value`，因此触发设置的 `onChanged` 与钳制逻辑；
 * - `Setting` 注册的变更监听负责把外部写入（模块代码、快捷键、UI）同步回状态。
 *
 * 注意：`setValueSilently`（如配置加载）不会触发监听，加载完成后需调用
 * `UiScene.invalidateAllUi()` 或 `LuminSceneRoot.invalidateAll()` 强制重建。
 */
private class SettingState<V>(private val setting: Setting<V>) : LuminState<V>() {

    private val backing: MutableState<V> = MutableState(setting.readCurrentValue())

    init {
        setting.addChangeListener { newValue -> backing.value = newValue }
    }

    override var value: V
        get() = backing.value
        set(newValue) {
            if (setting.value != newValue) {
                setting.value = newValue
            }
        }

    override fun readUntracked(): V = backing.readUntracked()

    internal override fun subscribe(composition: LuminComposition) = backing.subscribe(composition)

    internal override fun unsubscribe(composition: LuminComposition) = backing.unsubscribe(composition)

    internal override fun invalidateSubscribers() = backing.invalidateSubscribers()

}

private val adapters: WeakHashMap<Setting<*>, LuminState<*>> = WeakHashMap()

/**
 * 取得该设置对应的可订阅状态；同一个 [Setting] 共享同一个适配器，可在 recompose 块内反复调用。
 */
@Suppress("UNCHECKED_CAST")
fun <V> Setting<V>.asState(): LuminState<V> {
    val existing = adapters[this]
    if (existing != null) {
        return existing as LuminState<V>
    }
    val created = SettingState(this)
    adapters[this] = created
    return created
}
