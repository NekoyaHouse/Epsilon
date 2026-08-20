package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.injection.access.core.bedrock.IEventLoopGroupHolder;
import me.sofurry.via.protocoltranslator.netty.RakNetPingEncapsulationCodec;
import me.sofurry.via.util.bedrock.NetherNetInetSocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.netty.PacketCodec;
import net.raphimc.viabedrock.netty.raknet.MessageCodec;
import net.raphimc.viabedrock.protocol.RakNetStatusProtocol;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(value = Connection.class, priority = 1001) // Apply after connection/MixinConnection_CoreConnectionBedrock
public abstract class MixinConnection_CoreConnectionBedrock extends SimpleChannelInboundHandler<Packet<?>> {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        if (BedrockProtocolVersion.bedrockLatest.equals(((IConnection) this).viaVersion$getTargetVersion())) { // Call channelActive manually when the channel is registered
            this.channelActive(ctx);
        }
    }

    @WrapWithCondition(method = "channelActive", at = @At(value = "INVOKE", target = "Lio/netty/channel/SimpleChannelInboundHandler;channelActive(Lio/netty/channel/ChannelHandlerContext;)V", remap = false))
    private boolean dontCallChannelActiveTwice(SimpleChannelInboundHandler<Packet<?>> instance, ChannelHandlerContext channelHandlerContext) {
        return !BedrockProtocolVersion.bedrockLatest.equals(((IConnection) this).viaVersion$getTargetVersion());
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private static void setTargetVersion(InetSocketAddress inetSocketAddress, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir, @Local(argsOnly = true) LocalRef<EventLoopGroupHolder> eventLoopGroupHolderRef) {
        final ProtocolVersion targetVersion = ((IConnection) connection).viaVersion$getTargetVersion();
        if (BedrockProtocolVersion.bedrockLatest.equals(targetVersion) && (eventLoopGroupHolder.channelCls() == KQueueSocketChannel.class || inetSocketAddress instanceof NetherNetInetSocketAddress)) { // RakNet does not support KQueue, switch to NIO. NetherNet requires NIO
            final EventLoopGroupHolder newEventLoopGroupHolder = EventLoopGroupHolder.remote(false);
            ((IEventLoopGroupHolder) newEventLoopGroupHolder).viaVersion$setConnecting(((IEventLoopGroupHolder) eventLoopGroupHolder).viaVersion$isConnecting());
            eventLoopGroupHolderRef.set(newEventLoopGroupHolder);
        }
    }

    @WrapOperation(method = "connect", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;", remap = false))
    private static AbstractBootstrap<?, ?> useRakNetChannelFactory(Bootstrap instance, Class<? extends Channel> channelTypeClass, Operation<AbstractBootstrap<Bootstrap, Channel>> original, @Local(argsOnly = true) InetSocketAddress address, @Local(argsOnly = true) Connection clientConnection) {
        if (BedrockProtocolVersion.bedrockLatest.equals(((IConnection) clientConnection).viaVersion$getTargetVersion())) {
            if (address instanceof NetherNetInetSocketAddress) {
                throw new IllegalStateException("NetherNet connections require a Bedrock account");
            } else { // RakNet
                if (channelTypeClass == NioSocketChannel.class) {
                    channelTypeClass = NioDatagramChannel.class;
                } else if (channelTypeClass == EpollSocketChannel.class) {
                    channelTypeClass = EpollDatagramChannel.class;
                } else {
                    throw new IllegalStateException("Unsupported channel type for RakNet: " + channelTypeClass);
                }
                return instance.channelFactory(RakChannelFactory.client((Class<? extends DatagramChannel>) channelTypeClass));
            }
        } else {
            return original.call(instance, channelTypeClass);
        }
    }

    @WrapOperation(method = "connect", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;", remap = false))
    private static ChannelFuture useRakNetPingHandlers(Bootstrap instance, InetAddress inetHost, int inetPort, Operation<ChannelFuture> original, @Local(argsOnly = true) InetSocketAddress address, @Local(argsOnly = true) Connection clientConnection, @Local(argsOnly = true) EventLoopGroupHolder eventLoopGroupHolder) {
        if (BedrockProtocolVersion.bedrockLatest.equals(((IConnection) clientConnection).viaVersion$getTargetVersion())) {
            if (address instanceof NetherNetInetSocketAddress netherNetAddress) {
                return instance.connect(netherNetAddress.getNetherNetAddress()).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, (ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        f.channel().pipeline().remove(MessageCodec.NAME);
                    }
                });
            } else if (!((IEventLoopGroupHolder) eventLoopGroupHolder).viaVersion$isConnecting()) {
                // Bedrock edition / RakNet has different handlers for pinging a server
                return instance.register().syncUninterruptibly().channel().bind(new InetSocketAddress(0)).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, (ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        f.channel().pipeline().replace(
                                MessageCodec.NAME,
                                RakNetPingEncapsulationCodec.NAME,
                                new RakNetPingEncapsulationCodec(new InetSocketAddress(inetHost, inetPort))
                        );
                        f.channel().pipeline().remove(PacketCodec.NAME);
                        f.channel().pipeline().remove(HandlerNames.SPLITTER);

                        UserConnection user = ((IConnection) clientConnection).viaVersion$getUserConnection();
                        user.getProtocolInfo().getPipeline().add(RakNetStatusProtocol.INSTANCE);
                    }
                });
            }
        }
        return original.call(instance, inetHost, inetPort);
    }

}
