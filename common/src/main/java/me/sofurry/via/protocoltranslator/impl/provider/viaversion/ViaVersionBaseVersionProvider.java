package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocol.version.BaseVersionProvider;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;

public class ViaVersionBaseVersionProvider extends BaseVersionProvider {

    @Override
    public ProtocolVersion getClosestServerProtocol(UserConnection connection) throws Exception {
        if (connection.isClientSide()) {
            return ProtocolTranslator.getTargetVersion(connection.getChannel());
        } else {
            return super.getClosestServerProtocol(connection);
        }
    }

}
