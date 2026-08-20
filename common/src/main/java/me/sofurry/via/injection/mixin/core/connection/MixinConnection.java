package me.sofurry.via.injection.mixin.core.connection;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.injection.access.core.ILocalSampleLogger;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.network.CipherDecoder;
import net.minecraft.network.CipherEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.netty.PreNettyLengthPrepender;
import net.raphimc.vialegacy.netty.PreNettyLengthRemover;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.crypto.Cipher;
import java.net.InetSocketAddress;

@Mixin(Connection.class)
public abstract class MixinConnection extends SimpleChannelInboundHandler<Packet<?>> implements IConnection {

    @Shadow
    public Channel channel;

    @Shadow
    public abstract void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception;

    @Unique
    private UserConnection viaVersion$userConnection;

    @Unique
    private ProtocolVersion viaVersion$serverVersion;

    @Unique
    private Cipher viaVersion$decryptionCipher;

    @Inject(method = "setupCompression", at = @At("RETURN"))
    private void reorderCompression(int threshold, boolean validateDecompressed, CallbackInfo ci) {
        // Compression enabled, and elements put into pipeline, move via handlers
        ViaChannelInitializer.reorderPipeline(channel.pipeline(), HandlerNames.COMPRESS, HandlerNames.DECOMPRESS);
    }

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void storeDecryptionCipher(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (this.viaVersion$serverVersion != null /* This happens when opening a lan server and people are joining */ && this.viaVersion$serverVersion.olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
            // Minecraft's encryption code is bad for us, we need to reorder the pipeline
            ci.cancel();

            // Minecraft 1.6.4 supports split encryption/ decryption, which means the server can only enable one side of the encryption
            // So we only enable the encryption side and later enable the decryption side if the 1.7 -> 1.6 protocol
            // tells us to do, therefore, we need to store the cipher instance.
            this.viaVersion$decryptionCipher = decryptCipher;

            // Enabling the encryption side
            if (encryptCipher == null) {
                throw new IllegalStateException("Encryption cipher is null");
            }

            this.channel.pipeline().addBefore(PreNettyLengthRemover.NAME, HandlerNames.ENCRYPT, new CipherEncoder(encryptCipher));
        }
    }

    @Inject(method = "connectToServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private static void setTargetVersion(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, LocalSampleLogger bandwidthLogger, CallbackInfoReturnable<Connection> cir, @Local(name = "connection") Connection connection) {
        // Set the target version stored in the PerformanceLog field to the ClientConnection instance
        if (bandwidthLogger instanceof ILocalSampleLogger mixinMultiValueDebugSampleLogImpl && mixinMultiValueDebugSampleLogImpl.viaVersion$getForcedVersion() != null) {
            ((IConnection) connection).viaVersion$setTargetVersion(mixinMultiValueDebugSampleLogImpl.viaVersion$getForcedVersion());
        }
    }

    @WrapWithCondition(method = "connectToServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setBandwidthLogger(Lnet/minecraft/util/debugchart/LocalSampleLogger;)V"))
    private static boolean dontSetPerformanceLog(Connection instance, LocalSampleLogger bandwidthLogger) {
        // We need to restore vanilla behavior since we use the PerformanceLog as a way to store the target version
        return !(bandwidthLogger instanceof ILocalSampleLogger mixinMultiValueDebugSampleLogImpl) || mixinMultiValueDebugSampleLogImpl.viaVersion$getForcedVersion() == null;
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private static void setTargetVersion(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        ProtocolVersion targetVersion = ((IConnection) connection).viaVersion$getTargetVersion();
        if (targetVersion == null) { // No server-specific override
            targetVersion = ProtocolTranslator.getTargetVersion();
        }
        if (targetVersion == ProtocolTranslator.AUTO_DETECT_PROTOCOL) { // Auto-detect enabled (when pinging always use a native version). Auto-detect is resolved in ConnectScreen mixin
            targetVersion = ProtocolTranslator.NATIVE_VERSION;
        }
        ((IConnection) connection).viaVersion$setTargetVersion(targetVersion);
    }

    @Override
    public void viaVersion$setupPreNettyDecryption() {
        if (this.viaVersion$decryptionCipher == null) {
            throw new IllegalStateException("Decryption cipher is null");
        }

        // Enabling the decryption side for 1.6.4 if the 1.7 -> 1.6 protocol tells us to do
        this.channel.pipeline().addBefore(PreNettyLengthPrepender.NAME, HandlerNames.DECRYPT, new CipherDecoder(this.viaVersion$decryptionCipher));
    }

    @Override
    public UserConnection viaVersion$getUserConnection() {
        return this.viaVersion$userConnection;
    }

    @Override
    public void viaVersion$setUserConnection(UserConnection connection) {
        this.viaVersion$userConnection = connection;
    }

    @Override
    public ProtocolVersion viaVersion$getTargetVersion() {
        return this.viaVersion$serverVersion;
    }

    @Override
    public void viaVersion$setTargetVersion(final ProtocolVersion serverVersion) {
        this.viaVersion$serverVersion = serverVersion;
    }

}
