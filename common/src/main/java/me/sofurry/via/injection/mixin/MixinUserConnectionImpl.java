package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.connection.UserConnectionImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import me.sofurry.via.protocoltranslator.util.NoPacketSendChannel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UserConnectionImpl.class, remap = false)
public abstract class MixinUserConnectionImpl {

    @Shadow
    @Final
    private Channel channel;

    @Inject(method = "sendRawPacket(Lio/netty/buffer/ByteBuf;Z)V", at = @At("HEAD"), cancellable = true)
    private void handleNoPacketSendChannel(ByteBuf packet, boolean currentThread, CallbackInfo ci) {
        if (this.channel instanceof NoPacketSendChannel) {
            ci.cancel();
        }
    }

}
