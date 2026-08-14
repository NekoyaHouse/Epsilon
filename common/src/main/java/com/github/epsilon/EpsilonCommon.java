package com.github.epsilon;

import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.assets.i18n.I18NFileGenerator;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.*;
import com.github.epsilon.managers.network.ClientboundPacketManager;
import com.github.epsilon.managers.network.ServerboundPacketManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.Minecraft;

import java.lang.invoke.MethodHandles;

public class EpsilonCommon {

    public static void init() {
        Constants.LOGGER.info("Welcome to " + Constants.NAME + ".");

        Constants.mc = Minecraft.getInstance();

        EventBus.INSTANCE.registerLambdaFactory(EpsilonCommon.class.getPackageName(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // 初始化客户端系统
        ModuleManager.INSTANCE.initModules();
        HudElementManager.INSTANCE.initElements();
        AddonManager.INSTANCE.setupAddons();

        // 初始化 Managers
        ExecutorManager.INSTANCE.getClass();
        ClientboundPacketManager.INSTANCE.getClass();
        ServerboundPacketManager.INSTANCE.getClass();
        TargetManager.INSTANCE.getClass();
        ExtrapolationManager.INSTANCE.getClass();
        HealthManager.INSTANCE.getClass();
        SkinManager.INSTANCE.getClass();

        // 恢复配置
        ConfigManager.INSTANCE.initConfig();
        EpsilonLanguageManager.INSTANCE.selectLanguage(ClientSetting.INSTANCE.language.getValue());

        // 初始化 Render3DScheduler 里的 RenderPipeline
        Render3DScheduler.INSTANCE.getClass();

        // 生成空的 i18n 文件
        I18NFileGenerator.generate("epsilon-empty-i18n.json");

        // 添加一个退出游戏时候的钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.INSTANCE.saveNow();
            Constants.LOGGER.info(Constants.NAME + " saved config on shutdown.");
        }));

        Constants.LOGGER.info(Constants.NAME + " has loaded successfully.");
    }

}
