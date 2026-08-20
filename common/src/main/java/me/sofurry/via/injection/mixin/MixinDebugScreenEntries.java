package me.sofurry.via.injection.mixin;

import me.sofurry.via.features.hud.ViaVersionDebugHudEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(DebugScreenEntries.class)
public abstract class MixinDebugScreenEntries {

    @Mutable
    @Shadow
    @Final
    public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

    @Shadow
    private static Identifier register(final Identifier id, final DebugScreenEntry entry) {
        return null;
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void addViaVersionEntry(CallbackInfo ci) {
        final Identifier id = register(ViaVersionDebugHudEntry.ID, new ViaVersionDebugHudEntry());
        final Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> profiles = new HashMap<>();
        for (Map.Entry<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> entry : PROFILES.entrySet()) {
            final Map<Identifier, DebugScreenEntryStatus> entries = new HashMap<>(entry.getValue());
            if (entry.getKey() == DebugScreenProfile.DEFAULT) {
                entries.put(id, DebugScreenEntryStatus.IN_OVERLAY);
            }
            profiles.put(entry.getKey(), entries);
        }
        PROFILES = profiles;
    }

}
