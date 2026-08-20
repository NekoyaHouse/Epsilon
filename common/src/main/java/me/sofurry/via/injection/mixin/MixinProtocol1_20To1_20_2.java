package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ServerboundPackets1_19_4;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.Protocol1_20To1_20_2;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ServerboundPackets1_20_2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Protocol1_20To1_20_2.class, remap = false)
public abstract class MixinProtocol1_20To1_20_2 {

    @Inject(method = "lambda$queueServerboundPacket$12", at = @At("HEAD"), cancellable = true)
    private static void dontQueueConfigPackets(ServerboundPackets1_20_2 packetType, PacketWrapper wrapper, CallbackInfo ci) {
        if (!ClientSetting.INSTANCE.queueConfigPackets.getValue()) {
            ci.cancel();
            if (packetType == ServerboundPackets1_20_2.CUSTOM_PAYLOAD) {
                wrapper.setPacketType(ServerboundPackets1_19_4.CUSTOM_PAYLOAD);
            } else if (packetType == ServerboundPackets1_20_2.KEEP_ALIVE) {
                wrapper.setPacketType(ServerboundPackets1_19_4.KEEP_ALIVE);
            } else if (packetType == ServerboundPackets1_20_2.PONG) {
                wrapper.setPacketType(ServerboundPackets1_19_4.PONG);
            } else {
                throw new IllegalStateException("Unexpected packet type: " + packetType);
            }
        }
    }

}
