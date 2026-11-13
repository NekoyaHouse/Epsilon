package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.jvm.hooks.ViaFabricPlusHooks
import com.github.epsilon.managers.ConfigManager

/** 宿主帧开始时同步有外部来源的数据；绘制只读取已发布的快照。 */
fun prepareLuminDataSources() {
    ViaFabricPlusHooks.refreshUiVersion()
    ConfigManager.listConfigs()
    StaticFontLoader.defaultFont()
    MD3Theme.syncFromSettings()
}
