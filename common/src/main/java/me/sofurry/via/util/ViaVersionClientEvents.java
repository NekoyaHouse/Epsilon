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

package me.sofurry.via.util;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ViaVersionClientEvents {

    public static final LevelTickEvent START_LEVEL_TICK = new LevelTickEvent();

    private ViaVersionClientEvents() {
    }

    public static final class LevelTickEvent {

        private final CopyOnWriteArrayList<Consumer<ClientLevel>> listeners = new CopyOnWriteArrayList<>();

        public void register(final Consumer<ClientLevel> listener) {
            if (listener != null) {
                listeners.add(listener);
            }
        }

        public void invoke(final ClientLevel level) {
            for (final Consumer<ClientLevel> listener : listeners) {
                listener.accept(level);
            }
        }

    }

}
