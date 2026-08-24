package me.sofurry.via.features.movement.elytra;

import net.lenni0451.reflect.stream.RStream;

import java.util.Set;

public class FabricAPIWorkaround {

    public static void init() {
        // Force unload some FabricAPI mixins because FabricAPI overwrites some of the elytra code
        final Set<String> loadedMixins = RStream.of("org.spongepowered.asm.mixin.transformer.MixinConfig").fields().by("globalMixinList").get();
        loadedMixins.add("net.fabricmc.fabric.mixin.client.entity.event.elytra.ClientPlayerEntityMixin");
        loadedMixins.add("net.fabricmc.fabric.mixin.entity.event.elytra.LivingEntityMixin");
        loadedMixins.add("net.fabricmc.fabric.mixin.entity.event.elytra.PlayerEntityMixin");
    }

}
