/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.sofurry.via.platform;

import me.sofurry.via.api.ViaVersionBase;
import me.sofurry.via.protocoltranslator.impl.command.ViaVersionCommandHandler;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Small loader boundary used by the embedded ViaVersion implementation.
 * Minecraft-facing code stays in common while loader APIs are supplied here.
 */
public final class ViaVersionPlatform {

    public static final String DEFAULT_VERSION = "4.6.2-SNAPSHOT";

    private static volatile Provider provider = _ -> false;

    private ViaVersionPlatform() {
    }

    public static void install(final Provider platform) {
        if (platform != null) {
            provider = platform;
        }
    }

    public static Provider get() {
        return provider;
    }

    public static boolean isModLoaded(final String modId) {
        return provider.isModLoaded(modId);
    }

    public interface Provider {

        default Path getConfigDirectory() {
            return Path.of("config");
        }

        default String getVersion() {
            return DEFAULT_VERSION;
        }

        default String getImplVersion() {
            return getVersion();
        }

        default boolean isFabric() {
            return false;
        }

        boolean isModLoaded(String modId);

        default List<ModInfo> getMods() {
            return List.of();
        }

        default void registerClientCommands(final ViaVersionCommandHandler handler) {
        }

        default void invokeLoadEntrypoints(final ViaVersionBase platform) {
        }

        default void registerSyncPayload() {
        }
    }

    public record ModInfo(String id, String name, String version, List<ModAuthor> authors) {
    }

    public record ModAuthor(String name, Map<String, String> contact) {
    }

}
