package me.sofurry.via;

import me.sofurry.via.api.ViaVersionBase;

/**
 * Holder class for the {@link ViaVersionBase} implementation
 */
public class ViaVersion {

    private static ViaVersionBase impl;

    public static void init(final ViaVersionBase impl) {
        if (ViaVersion.impl != null) {
            throw new IllegalStateException("ViaVersion has already been initialized!");
        }
        ViaVersion.impl = impl;
    }

    /**
     * @return the ViaVersionBase implementation that is set by the internals
     */
    public static ViaVersionBase getImpl() {
        if (impl == null) {
            throw new IllegalStateException("ViaVersion has not been initialized yet!");
        }
        return impl;
    }

}
