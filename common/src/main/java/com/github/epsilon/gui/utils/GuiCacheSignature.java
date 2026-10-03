package com.github.epsilon.gui.utils;

import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.managers.TranslationManager;
import com.github.epsilon.modules.Module;

/**
 * 离屏缓存 GUI 的外部状态签名。
 * <p>
 * 只覆盖不经过 GUI 输入事件也会改变画面的状态：语言、模块开关与按键绑定。
 * 输入、动画和鼠标移动由各 Screen 自行失效。
 */
public final class GuiCacheSignature {

    private GuiCacheSignature() {
    }

    public static long compute() {
        long signature = 17L;
        signature = signature * 31L + TranslationManager.INSTANCE.getRevision();
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            signature = signature * 31L + (module.isEnabled() ? 1 : 0);
            signature = signature * 31L + module.getKeyBind();
        }
        return signature;
    }

}
