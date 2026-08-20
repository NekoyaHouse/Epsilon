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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class ViaVersionEvent<T> {

    private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
    private final Function<List<T>, T> invokerFactory;

    public ViaVersionEvent(final Function<List<T>, T> invokerFactory) {
        this.invokerFactory = invokerFactory;
    }

    public void register(final T listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public T invoker() {
        return invokerFactory.apply(List.copyOf(listeners));
    }

}
