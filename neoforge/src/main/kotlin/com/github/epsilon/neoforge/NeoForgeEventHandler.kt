package com.github.epsilon.neoforge

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.LanguageReloadListener
import com.github.epsilon.assets.resources.ResourceLocationUtils
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent

@EventBusSubscriber(modid = Constants.MOD_ID, value = [Dist.CLIENT])
class NeoForgeEventHandler {

    @SubscribeEvent
    fun onResourcesReload(event: AddClientReloadListenersEvent) {
        event.addListener(ResourceLocationUtils.getIdentifier("objects/reload_listener"), LanguageReloadListener())
    }

}
