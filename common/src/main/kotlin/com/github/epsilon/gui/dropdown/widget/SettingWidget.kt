package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.component.Component
import com.github.epsilon.settings.Setting

abstract class SettingWidget<S : Setting<*>>(val setting: S) : Component() {

    fun isVisible(): Boolean {
        return setting.isVisible
    }

    /**
     * 设置控件的缓存版本：设置值与可见性/平台支持都会参与，动画或焦点状态则叠加时间戳。
     *
     * 子类如果还有未写回设置的临时状态（如滑条拖拽预览值），需要覆盖并并入版本。
     */
    override fun cacheRevision(): Long {
        var revision = setting.value?.hashCode()?.toLong() ?: 0L
        revision = revision * 31L + (if (setting.isVisible) 1L else 0L)
        revision = revision * 31L + (if (setting.isViaSupported()) 1L else 0L)
        if (hasActiveAnimations()) {
            revision = revision * 31L + System.nanoTime()
        }
        return revision
    }

}
