package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CompressionProvider;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.network.Connection;

public final class ViaVersionCompressionProvider extends CompressionProvider {

    @Override
    public void handlePlayCompression(final UserConnection user, final int threshold) {
        final Connection connection = user.getChannel().attr(ProtocolTranslator.CLIENT_CONNECTION_ATTRIBUTE_KEY).get();
        connection.setupCompression(threshold, true);
    }

}
