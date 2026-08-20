package me.sofurry.via.injection.access.core;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public interface IConnection {

    void viaVersion$setupPreNettyDecryption();

    ProtocolVersion viaVersion$getTargetVersion();

    void viaVersion$setTargetVersion(final ProtocolVersion serverVersion);

    UserConnection viaVersion$getUserConnection();

    void viaVersion$setUserConnection(final UserConnection connection);

}
