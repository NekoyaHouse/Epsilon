package me.sofurry.via.protocoltranslator.translator;

import com.github.epsilon.Constants;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.protocols.v1_13_2to1_14.packet.ClientboundPackets1_14;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;

public class TextComponentTranslator {

    private static final UserConnection DUMMY_USER_CONNECTION = ProtocolTranslator.createDummyUserConnection(ProtocolTranslator.NATIVE_VERSION, ProtocolVersion.v1_14);

    /**
     * Converts a ViaVersion 1.14 text component to a native text component, both in JSON format
     *
     * @param component The ViaVersion 1.14 text component
     * @return The ViaVersion text component for the native version
     */
    public static Tag via1_14toViaLatest(final JsonElement component) {
        try {
            final PacketWrapper openScreen = PacketWrapper.create(ClientboundPackets1_14.OPEN_SCREEN, DUMMY_USER_CONNECTION);
            openScreen.write(Types.VAR_INT, 1); // window id
            openScreen.write(Types.VAR_INT, 0); // type id
            openScreen.write(Types.COMPONENT, component); // title

            openScreen.resetReader();
            openScreen.user().getProtocolInfo().getPipeline().transform(Direction.CLIENTBOUND, State.PLAY, openScreen);

            openScreen.read(Types.VAR_INT); // window id
            openScreen.read(Types.VAR_INT); // type id
            return openScreen.read(Types.TAG); // title
        } catch (Throwable t) {
            Constants.LOGGER.error("Error converting ViaVersion 1.14 text component to native text component", t);
            return null;
        }
    }

}
