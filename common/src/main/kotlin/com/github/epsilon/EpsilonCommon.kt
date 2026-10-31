package com.github.epsilon

import com.github.epsilon.assets.i18n.EpsilonLanguageManager
import com.github.epsilon.assets.i18n.I18NFileGenerator
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.managers.*
import com.github.epsilon.managers.network.ClientboundPacketManager
import com.github.epsilon.managers.network.ServerboundPacketManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.impl.ClientSetting

/**
 * 客户端公共初始化入口，由各加载器的入口在自己的初始化阶段调用一次。
 *
 * 初始化顺序不可调整：模块与 HUD 元素必须先完成注册，Manager 预热要早于配置恢复，
 * 语言选择必须在配置恢复之后。
 */
object EpsilonCommon {

    fun init() {
        Constants.LOGGER.info("Welcome to ${Constants.NAME}.")

        // 上下文刷新必须先于其它监听器与主线程任务注册。
        NonNullContext.hashCode()

        // 初始化客户端系统：模块与 HUD 元素必须在配置恢复之前完成注册
        ModuleManager.initModules()
        HudElementManager.initElements()

        // 预热 Managers（Kotlin object 在首次访问时完成类初始化）
        ExecutorManager.hashCode()
        ClientboundPacketManager.hashCode()
        ServerboundPacketManager.hashCode()
        TargetManager.hashCode()
        ExtrapolationManager.hashCode()
        HealthManager.hashCode()
        SkinManager.hashCode()

        // 恢复配置，随后按配置选定语言
        ConfigManager.initConfig()
        EpsilonLanguageManager.selectLanguage(ClientSetting.language.value)

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
