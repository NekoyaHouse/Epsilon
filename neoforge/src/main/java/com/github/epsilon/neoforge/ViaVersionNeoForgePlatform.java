package com.github.epsilon.neoforge;

import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.platform.ViaVersionPlatform.ModInfo;
import me.sofurry.via.protocoltranslator.impl.command.ViaVersionCommandHandler;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public final class ViaVersionNeoForgePlatform implements ViaVersionPlatform.Provider {

    private static final ViaVersionNeoForgePlatform INSTANCE = new ViaVersionNeoForgePlatform();

    private ViaVersionNeoForgePlatform() {
    }

    public static void install() {
        ViaVersionPlatform.install(INSTANCE);
    }

    @Override
    public boolean isModLoaded(final String modId) {
        final ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    @Override
    public List<ModInfo> getMods() {
        return ModList.get().getMods().stream()
                .map(mod -> new ModInfo(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString(), List.of()))
                .toList();
    }

    @Override
    public void registerClientCommands(final ViaVersionCommandHandler handler) {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> handler.register(event.getDispatcher()));
    }

}
