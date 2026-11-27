package com.github.epsilon.fabric

import com.github.epsilon.EpsilonCommon
import com.github.epsilon.assets.i18n.LanguageReloadListener
import com.github.epsilon.assets.resources.ResourceLocationUtils
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.server.packs.PackType

object EpsilonFabric {

    fun init() {
        EpsilonCommon.init()

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            ResourceLocationUtils.getIdentifier("objects/reload_listener"),
            LanguageReloadListener(),
        )
    }

}
