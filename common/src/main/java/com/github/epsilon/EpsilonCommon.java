package com.github.epsilon;

import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.assets.i18n.I18NFileGenerator;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.holders.AddonHolder;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.holders.HudElementHolder;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.ClientSetting;

import java.lang.invoke.MethodHandles;

public class EpsilonCommon {

    public static void init() {
        Constants.LOGGER.info("Welcome to " + Constants.NAME + ".");

        EventBus.INSTANCE.registerLambdaFactory(EpsilonCommon.class.getPackageName(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // 初始化客户端系统
        ModuleHolder.INSTANCE.initModules();
        HudElementHolder.INSTANCE.initElements();
        AddonHolder.INSTANCE.setupAddons();
        ConfigHolder.INSTANCE.initConfig();
        EpsilonLanguageManager.INSTANCE.selectLanguage(ClientSetting.INSTANCE.language.getValue());

        // 初始化 Managers
        Managers.initManagers();

        // 初始化 Render3DScheduler 里的 RenderPipeline
        Render3DScheduler.init();

        // 生成空的 i18n 文件
        I18NFileGenerator.generate("epsilon-empty-i18n.json");

        // 添加一个退出游戏时候的钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigHolder.INSTANCE.saveNow();
            Constants.LOGGER.info(Constants.NAME + " saved config on shutdown.");
        }));

        Constants.LOGGER.info(Constants.NAME + " has loaded successfully.");
    }

}
