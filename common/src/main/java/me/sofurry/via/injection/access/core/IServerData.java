package me.sofurry.via.injection.access.core;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public interface IServerData {

    ProtocolVersion viaVersion$forcedVersion();

    void viaVersion$forceVersion(final ProtocolVersion version);

    boolean viaVersion$passedDirectConnectScreen();

    void viaVersion$passDirectConnectScreen(final boolean state);

    ProtocolVersion viaVersion$translatingVersion();

    void viaVersion$setTranslatingVersion(final ProtocolVersion version);

}
