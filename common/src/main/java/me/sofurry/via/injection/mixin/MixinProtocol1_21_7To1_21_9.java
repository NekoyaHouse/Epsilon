package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ClientboundPacket1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ClientboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPacket1_21_6;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.Protocol1_21_7To1_21_9;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.packet.ClientboundPacket1_21_9;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.packet.ServerboundPacket1_21_9;
import me.sofurry.via.injection.access.networking.packet_handling.IGameTestBlockHighlightRenderer;
import me.sofurry.via.util.network.SyncTasks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Protocol1_21_7To1_21_9.class, remap = false)
public abstract class MixinProtocol1_21_7To1_21_9 extends AbstractProtocol<ClientboundPacket1_21_6, ClientboundPacket1_21_9, ServerboundPacket1_21_6, ServerboundPacket1_21_9> {

    public MixinProtocol1_21_7To1_21_9(final Class<ClientboundPacket1_21_6> unmappedClientboundPacketType, final Class<ClientboundPacket1_21_9> mappedClientboundPacketType, final Class<ServerboundPacket1_21_6> mappedServerboundPacketType, final Class<ServerboundPacket1_21_9> unmappedServerboundPacketType) {
        super(unmappedClientboundPacketType, mappedClientboundPacketType, mappedServerboundPacketType, unmappedServerboundPacketType);
    }

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void handleGameTestPayloads(CallbackInfo ci) {
        registerClientbound(ClientboundPackets1_21_6.CUSTOM_PAYLOAD, wrapper -> {
            final String channel = wrapper.passthrough(Types.STRING);
            if (channel.equals("minecraft:debug/game_test_add_marker")) {
                wrapper.set(Types.STRING, 0, SyncTasks.PACKET_SYNC_IDENTIFIER);
                wrapper.write(Types.STRING, SyncTasks.executeSyncTask(buf -> {
                    final BlockPos pos = buf.readBlockPos();
                    final int color = buf.readInt();
                    final String name = buf.readUtf();
                    final int duration = buf.readInt();

                    final IGameTestBlockHighlightRenderer mixinTestDebugRenderer = (IGameTestBlockHighlightRenderer) Minecraft.getInstance().levelExtractor.gameTestBlockHighlightRenderer;
                    mixinTestDebugRenderer.viaVersion$addMarker(pos, color, name, duration);
                }));
            } else if (channel.equals("minecraft:debug/game_test_clear")) {
                wrapper.set(Types.STRING, 0, SyncTasks.PACKET_SYNC_IDENTIFIER);
                wrapper.write(Types.STRING, SyncTasks.executeSyncTask(buf -> Minecraft.getInstance().levelExtractor.gameTestBlockHighlightRenderer.clear()));
            }
        });
    }

}
