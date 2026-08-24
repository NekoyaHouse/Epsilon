package me.sofurry.via.protocoltranslator.impl.provider.vialegacy;

import com.viaversion.viaversion.api.connection.UserConnection;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.provider.EncryptionProvider;

public class ViaVersionEncryptionProvider extends EncryptionProvider {

    @Override
    public void enableDecryption(UserConnection connection) {
        ((IConnection) connection.getChannel().attr(ProtocolTranslator.CLIENT_CONNECTION_ATTRIBUTE_KEY).get()).viaVersion$setupPreNettyDecryption();
    }

}
