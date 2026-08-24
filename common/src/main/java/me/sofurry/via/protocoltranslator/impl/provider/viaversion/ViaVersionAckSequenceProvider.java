package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;

public class ViaVersionAckSequenceProvider extends AckSequenceProvider {

    @Override
    public void handleSequence(UserConnection connection, int sequence) {
    }

}
