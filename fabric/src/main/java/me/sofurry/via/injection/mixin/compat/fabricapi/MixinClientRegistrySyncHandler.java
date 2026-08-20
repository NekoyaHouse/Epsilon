/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.sofurry.via.injection.mixin.compat.fabricapi;

import me.sofurry.via.ViaVersionImpl;
import com.github.epsilon.modules.impl.ClientSetting;
import net.fabricmc.fabric.impl.client.registry.sync.ClientRegistrySyncHandler;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientRegistrySyncHandler.class)
public abstract class MixinClientRegistrySyncHandler {

    @Inject(method = "checkRemoteRemap", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;)V", ordinal = 0), cancellable = true, remap = false)
    private static void ignoreFabricSyncErrors(RegistrySyncPayload data, CallbackInfo ci) {
        if (ClientSetting.INSTANCE.ignoreFabricSyncErrors.getValue()) {
            ViaVersionImpl.INSTANCE.getLogger().warn("Ignoring missing registries from Fabric API");
            ci.cancel();
        }
    }

}
