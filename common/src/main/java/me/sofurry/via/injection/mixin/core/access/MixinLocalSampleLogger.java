package me.sofurry.via.injection.mixin.core.access;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.ILocalSampleLogger;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LocalSampleLogger.class)
public abstract class MixinLocalSampleLogger implements ILocalSampleLogger {

    @Unique
    private ProtocolVersion viaVersion$forcedVersion;

    @Override
    public ProtocolVersion viaVersion$getForcedVersion() {
        return this.viaVersion$forcedVersion;
    }

    @Override
    public void viaVersion$setForcedVersion(ProtocolVersion version) {
        this.viaVersion$forcedVersion = version;
    }

}
