package me.sofurry.via.api.entrypoint;

import me.sofurry.via.api.ViaVersionBase;
import me.sofurry.via.api.events.LoadingCycleCallback;

/**
 * Optional Entrypoint called before the ViaVersion loading cycle starts. This is needed to register a {@link LoadingCycleCallback} callback.
 * <p>
 * See {@link LoadingCycleCallback} for more information.
 */
@FunctionalInterface
public interface ViaVersionLoadEntrypoint {

    String KEY = "viafabricplus";

    void onPlatformLoad(final ViaVersionBase platform);

}
