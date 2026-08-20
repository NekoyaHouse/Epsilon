package com.github.epsilon.fabric;

import me.sofurry.via.api.ViaVersionBase;
import me.sofurry.via.api.entrypoint.ViaVersionLoadEntrypoint;
import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.platform.ViaVersionPlatform.ModAuthor;
import me.sofurry.via.platform.ViaVersionPlatform.ModInfo;
import me.sofurry.via.protocoltranslator.impl.command.ViaVersionCommandHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ViaVersionFabricPlatform implements ViaVersionPlatform.Provider {

    private static final ViaVersionFabricPlatform INSTANCE = new ViaVersionFabricPlatform();

    private ViaVersionFabricPlatform() {
    }

    public static void install() {
        ViaVersionPlatform.install(INSTANCE);
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isFabric() {
        return true;
    }

    @Override
    public boolean isModLoaded(final String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<ModInfo> getMods() {
        final List<ModInfo> mods = new ArrayList<>();
        for (final ModContainer container : FabricLoader.getInstance().getAllMods()) {
            final ModMetadata metadata = container.getMetadata();
            final List<ModAuthor> authors = new ArrayList<>();
            for (final Person author : metadata.getAuthors()) {
                authors.add(new ModAuthor(author.getName(), author.getContact().asMap()));
            }
            mods.add(new ModInfo(metadata.getId(), metadata.getName(), metadata.getVersion().getFriendlyString(), authors));
        }
        return List.copyOf(mods);
    }

    @Override
    public void registerClientCommands(final ViaVersionCommandHandler handler) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> handler.register(dispatcher));
    }

    @Override
    public void invokeLoadEntrypoints(final ViaVersionBase platform) {
        for (final EntrypointContainer<ViaVersionLoadEntrypoint> container : FabricLoader.getInstance().getEntrypointContainers(
                ViaVersionLoadEntrypoint.KEY, ViaVersionLoadEntrypoint.class
        )) {
            container.getEntrypoint().onPlatformLoad(platform);
        }
    }

}
