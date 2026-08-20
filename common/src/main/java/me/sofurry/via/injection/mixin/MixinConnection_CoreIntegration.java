package me.sofurry.via.injection.mixin;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.ConnectException;
import java.net.SocketException;

@Mixin(Connection.class)
public abstract class MixinConnection_CoreIntegration extends SimpleChannelInboundHandler<Packet<?>> {

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void printNetworkingErrors(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
        if (ClientSetting.INSTANCE.printNetworkingErrorsToLogs.getValue()) {
            if (cause instanceof SocketException || cause instanceof ConnectException) {
                // Thrown when server is not reachable
                return;
            }
            Constants.LOGGER.error("An exception occurred while handling a packet", cause);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        // Krypton mixins are badly implemented and override parts of the network pipeline. They rejected
        // to make the mixins mod compatible and instead added this bad API which mods now have to use for no reason.
        if (evt.getClass().getName().equals("me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent")) {
            if (evt.toString().equals("COMPRESSION_ENABLED")) {
                ViaChannelInitializer.reorderPipeline(ctx.pipeline(), HandlerNames.COMPRESS, HandlerNames.DECOMPRESS);
                Constants.LOGGER.warn("ViaVersion has detected that the Krypton mod is installed. Please note that Krypton is mostly snake oil on the client side, and it is not recommended to use it.");
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

}
