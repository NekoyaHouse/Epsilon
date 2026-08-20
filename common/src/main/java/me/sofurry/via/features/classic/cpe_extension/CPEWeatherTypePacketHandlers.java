package me.sofurry.via.features.classic.cpe_extension;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandlers;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;

public final class CPEWeatherTypePacketHandlers extends PacketHandlers {

    @Override
    public void register() {
        handler(wrapper -> {
            wrapper.cancel();
            final byte weatherType = wrapper.read(Types.BYTE);

            final PacketWrapper changeRainState = PacketWrapper.create(ClientboundPackets1_19_4.GAME_EVENT, wrapper.user());
            changeRainState.write(Types.UNSIGNED_BYTE, weatherType == 0 /* sunny */ ? (short) 1 : (short) 2); // start raining
            changeRainState.write(Types.FLOAT, 0F); // unused
            changeRainState.send(Protocol1_19_3To1_19_4.class);

            if (weatherType == 1 /* raining */ || weatherType == 2 /* snowing */) {
                final PacketWrapper changeRainType = PacketWrapper.create(ClientboundPackets1_19_4.GAME_EVENT, wrapper.user());
                changeRainType.write(Types.UNSIGNED_BYTE, (short) 7); // set rain gradient
                changeRainType.write(Types.FLOAT, 1F);
                changeRainType.send(Protocol1_19_3To1_19_4.class);
            }
            CPEAdditions.setSnowing(weatherType == 2);
        });
    }

}
