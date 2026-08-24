package me.sofurry.via.protocoltranslator.impl.provider.viabedrock;

import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.Channel;
import net.minecraft.network.HandlerNames;
import net.raphimc.viabedrock.netty.CompressionCodec;
import net.raphimc.viabedrock.netty.raknet.AesEncryptionCodec;
import net.raphimc.viabedrock.netty.raknet.MessageCodec;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PacketCompressionAlgorithm;
import net.raphimc.viabedrock.protocol.provider.NettyPipelineProvider;

import javax.crypto.SecretKey;

public class ViaVersionNettyPipelineProvider extends NettyPipelineProvider {

    @Override
    public void enableCompression(UserConnection user, PacketCompressionAlgorithm preferredCompressionAlgorithm, int threshold) {
        final Channel channel = user.getChannel();
        if (!channel.pipeline().names().contains(HandlerNames.COMPRESS)) {
            channel.pipeline().addBefore(HandlerNames.SPLITTER, HandlerNames.COMPRESS, new CompressionCodec(preferredCompressionAlgorithm, threshold));
        } else {
            channel.pipeline().replace(HandlerNames.COMPRESS, HandlerNames.COMPRESS, new CompressionCodec(preferredCompressionAlgorithm, threshold));
        }
    }

    @Override
    public void enableEncryption(UserConnection user, SecretKey key) {
        final Channel channel = user.getChannel();
        if (channel.pipeline().names().contains(HandlerNames.ENCRYPT)) {
            throw new IllegalStateException("Encryption already enabled");
        }

        if (channel.pipeline().get(MessageCodec.NAME) != null) { // Only enable encryption for RakNet connections
            try {
                channel.pipeline().addAfter(MessageCodec.NAME, HandlerNames.ENCRYPT, new AesEncryptionCodec(key));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

}
