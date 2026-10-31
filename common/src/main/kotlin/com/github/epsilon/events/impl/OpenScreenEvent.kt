package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.client.gui.screens.Screen

/**
 * 设置当前界面时发布；[screen] 为 null 表示关闭界面（`Gui.setScreen(null)`），
 * 与原 Java 版本允许 null 的语义一致。
 */
class OpenScreenEvent(val screen: Screen?) : Cancellable(), IEvent<OpenScreenEvent>, IPosting by Companion {
    companion object : EventBus()
}
