package com.github.epsilon.settings

import com.github.epsilon.assets.i18n.TranslateComponent
import java.util.*

/**
 * Setting 的显式分组模型。
 *
 * 分组只描述语义和折叠状态，具体位置仍交给 UiTree / Dropdown stack 统一计算。
 * 分组可以嵌套：[child] 创建或复用子分组，子分组只属于创建它的父分组。
 */
class SettingGroup(val name: String) {

    private val childGroups: MutableList<SettingGroup> = ArrayList()

    var parent: SettingGroup? = null
        private set

    var translateComponent: TranslateComponent? = null

    var isCollapsed: Boolean = true

    /**
     * 创建或复用同名子分组（忽略大小写）。
     */
    fun child(childName: String): SettingGroup {
        for (child in childGroups) {
            if (child.name.equals(childName, ignoreCase = true)) {
                return child
            }
        }
        val child = SettingGroup(childName)
        child.parent = this
        childGroups.add(child)
        return child
    }

    /**
     * 返回按声明顺序排列的子分组。
     */
    val children: List<SettingGroup>
        get() = Collections.unmodifiableList(childGroups)

    fun initTranslateComponent(component: TranslateComponent) {
        this.translateComponent = component
    }

    fun getDisplayName(): String = translateComponent?.getTranslatedName() ?: name

    fun toggleCollapsed() {
        isCollapsed = !isCollapsed
    }

}
