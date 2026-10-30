package com.github.epsilon.settings.impl

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.client.PlatformRequirement

class EnumSetting<E : Enum<E>>(
    name: String,
    defaultValue: E,
    dependency: Setting.Dependency?,
    onChanged: ((E) -> Unit)?,
) : Setting<E>(name, dependency, onChanged) {

    private val modeTranslations: MutableMap<E, TranslateComponent> = HashMap()

    private val modeRequirements: MutableMap<E, PlatformRequirement> = HashMap()

    val modes: Array<E> = defaultValue.declaringJavaClass.enumConstants

    init {
        initializeValue(defaultValue)
    }

    override fun initTranslateComponent(component: TranslateComponent) {
        super.initTranslateComponent(component)
        modeTranslations.clear()
        for (mode in modes) {
            modeTranslations[mode] = component.createChild(mode.toString().lowercase())
        }
    }

    val translatedValue: String
        get() {
            val comp = modeTranslations[value]
            return comp?.getTranslatedName() ?: value.toString()
        }

    fun getTranslatedValueByIndex(index: Int): String {
        if (index < 0 || index >= modes.size) return ""
        return getTranslatedValue(modes[index])
    }

    fun getTranslatedValue(enumValue: E): String {
        val comp = modeTranslations[enumValue]
        return comp?.getTranslatedName() ?: enumValue.toString()
    }

    fun getTranslatedValueUnchecked(enumValue: Enum<*>?): String {
        for (mode in modes) {
            if (mode === enumValue) {
                return getTranslatedValue(mode)
            }
        }
        return enumValue?.toString() ?: ""
    }

    fun `is`(enumValue: E): Boolean = value == enumValue

    /**
     * 声明某个枚举选项只在指定平台上可用。
     */
    fun restrictMode(mode: E?, requirement: PlatformRequirement?): EnumSetting<E> {
        if (mode != null && requirement != null && requirement !== PlatformRequirement.ANY) {
            modeRequirements[mode] = requirement
        } else if (mode != null) {
            modeRequirements.remove(mode)
        }
        return this
    }

    fun getModeRequirement(mode: E): PlatformRequirement =
        modeRequirements[mode] ?: PlatformRequirement.ANY

    fun isModeSupported(mode: E): Boolean = getModeRequirement(mode).isSatisfied()

    /**
     * 供 UI 层在 `EnumSetting<?>` 上使用的不定型查询。
     */
    fun getModeRequirementUnchecked(mode: Enum<*>?): PlatformRequirement {
        for (candidate in modes) {
            if (candidate === mode) {
                return getModeRequirement(candidate)
            }
        }
        return PlatformRequirement.ANY
    }

    fun isModeSupportedUnchecked(mode: Enum<*>?): Boolean = getModeRequirementUnchecked(mode).isSatisfied()

    /**
     * 当前取值是否可以在本平台使用。
     */
    fun isCurrentValueSupported(): Boolean = value == null || isModeSupported(value)

    fun `is`(string: String?): Boolean = matchesMode(value, string)

    fun setMode(mode: String?) {
        for (candidate in modes) {
            if (matchesMode(candidate, mode)) {
                value = candidate
            }
        }
    }

    fun setModeSilently(mode: String?) {
        for (candidate in modes) {
            if (matchesMode(candidate, mode)) {
                setValueSilently(candidate)
            }
        }
    }

    fun setMode(mode: E) {
        value = mode
    }

    val modeIndex: Int
        get() {
            var index = 0
            for (candidate in modes) {
                if (candidate === value) return index
                index++
            }
            return -1
        }

    private fun matchesMode(enumValue: E, mode: String?): Boolean {
        return mode != null && (
                enumValue.name == mode ||
                        enumValue.toString() == mode ||
                        enumValue.name.equals(mode, ignoreCase = true) ||
                        enumValue.toString().equals(mode, ignoreCase = true)
                )
    }

}
