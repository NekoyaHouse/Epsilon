package me.sofurry.via.features.movement.constants;

import net.lenni0451.reflect.stream.RStream;

import java.util.Set;

public final class LithiumWorkaround {

    public static void init() {
        // Workaround for https://github.com/ViaVersion/ViaFabricPlus/issues/1206 where lithium replaces sin/cos lookups using an optimized version
        final Set<String> loadedMixins = RStream.of("org.spongepowered.asm.mixin.transformer.MixinConfig").fields().by("globalMixinList").get();
        loadedMixins.add("net.caffeinemc.mods.lithium.mixin.math.sine_lut.MthMixin");
    }

}
