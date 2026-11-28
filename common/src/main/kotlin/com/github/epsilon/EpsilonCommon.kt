package com.github.epsilon

import com.github.epsilon.assets.i18n.EpsilonLanguageManager
import com.github.epsilon.assets.i18n.I18NFileGenerator
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.*
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.impl.ClientSetting

object EpsilonCommon {

    fun init() {
        Constants.LOGGER.info("Welcome to ${Constants.NAME}.")

        // 初始化客户端系统：模块与 HUD 元素必须在配置恢复之前完成注册
        ModuleManager.initModules()
        HudElementManager.initElements()

        // 预热 Managers
        ExecutorManager.hashCode()
        TargetManager.hashCode()
        HealthManager.hashCode()
        SkinManager.hashCode()

        // 恢复配置，随后按配置选定语言
        ConfigManager.initConfig()
        EpsilonLanguageManager.selectLanguage(ClientSetting.language)

        // 触发 Render3DScheduler 中的 RenderPipeline 初始化
        Render3DScheduler.hashCode()

        // 生成空的 i18n 模板
        I18NFileGenerator.generate("epsilon-empty-i18n.json")

        // 退出游戏时保存配置
        Runtime.getRuntime().addShutdownHook(
            Thread {
                ConfigManager.saveNow()
                Constants.LOGGER.info("${Constants.NAME} saved config on shutdown.")
            },
        )

        Constants.LOGGER.info("${Constants.NAME} has loaded successfully.")
    }

}
