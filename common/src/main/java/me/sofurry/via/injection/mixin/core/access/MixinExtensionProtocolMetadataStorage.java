package me.sofurry.via.injection.mixin.core.access;

import me.sofurry.via.injection.access.core.IExtensionProtocolMetadataStorage;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.storage.ExtensionProtocolMetadataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.EnumMap;

@Mixin(ExtensionProtocolMetadataStorage.class)
public abstract class MixinExtensionProtocolMetadataStorage implements IExtensionProtocolMetadataStorage {

    @Shadow
    @Final
    private EnumMap<ClassicProtocolExtension, Integer> serverExtensions;

    @Override
    public EnumMap<ClassicProtocolExtension, Integer> viaVersion$getServerExtensions() {
        return this.serverExtensions;
    }

}
