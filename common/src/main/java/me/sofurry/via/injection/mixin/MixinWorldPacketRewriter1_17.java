package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ClientboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.Protocol1_16_4To1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.rewriter.WorldPacketRewriter1_17;
import me.sofurry.via.features.classic.world_height.WorldHeightSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings({"unchecked", "rawtypes"})
@Mixin(value = WorldPacketRewriter1_17.class, remap = false)
public abstract class MixinWorldPacketRewriter1_17 {

    @Redirect(method = "register", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/protocols/v1_16_4to1_17/Protocol1_16_4To1_17;registerClientbound(Lcom/viaversion/viaversion/api/protocol/packet/ClientboundPacketType;Lcom/viaversion/viaversion/api/protocol/remapper/PacketHandler;)V"))
    private static void handleClassicWorldHeight(Protocol1_16_4To1_17 instance, ClientboundPacketType packetType, PacketHandler packetHandler) {
        if (packetType == ClientboundPackets1_16_2.LEVEL_CHUNK) {
            packetHandler = WorldHeightSupport.handleChunkData(packetHandler);
        }
        if (packetType == ClientboundPackets1_16_2.LIGHT_UPDATE) {
            packetHandler = WorldHeightSupport.handleUpdateLight(packetHandler);
        }

        ((Protocol) instance).registerClientbound(packetType, packetHandler);
    }

}
