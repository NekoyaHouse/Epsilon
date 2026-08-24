package me.sofurry.via.protocoltranslator.impl.provider.vialegacy;

import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.provider.ClassicWorldHeightProvider;

public class ViaVersionClassicWorldHeightProvider extends ClassicWorldHeightProvider {

    @Override
    public short getMaxChunkSectionCount(UserConnection user) {
        return 64;
    }

}
