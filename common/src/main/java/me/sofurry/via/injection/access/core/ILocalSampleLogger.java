package me.sofurry.via.injection.access.core;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public interface ILocalSampleLogger {

    ProtocolVersion viaVersion$getForcedVersion();

    void viaVersion$setForcedVersion(final ProtocolVersion version);

}
