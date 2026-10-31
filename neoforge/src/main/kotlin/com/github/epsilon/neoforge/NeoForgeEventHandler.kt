package com.github.epsilon.neoforge

import com.github.epsilon.BuildConfig
import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.LanguageReloadListener
import com.github.epsilon.assets.resources.ResourceLocationUtils
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent

/**
 * NeoForge 事件订阅入口。
 *
 * NeoForge 的 `AutomaticEventSubscriber` 只接受 **static** 的 `@SubscribeEvent` 方法
 * （非 static 会抛 IllegalArgumentException），而 Kotlin 无法在不生成 JVM 桥接注解的情况下
 * 在类上直接定义静态方法，因此这里用 `@JvmStatic` 生成带注解的静态桥接方法。
 * 这是整次迁移中唯一保留的 JVM 互操作注解，属平台事件扫描的硬性要求。
 */
@EventBusSubscriber(modid = BuildConfig.MOD_ID, value = [Dist.CLIENT])
class NeoForgeEventHandler {

    companion object {

        @JvmStatic
        @SubscribeEvent
        fun onResourcesReload(event: AddClientReloadListenersEvent) {
            event.addListener(ResourceLocationUtils.getIdentifier("objects/reload_listener"), LanguageReloadListener())
        }

    }

}
