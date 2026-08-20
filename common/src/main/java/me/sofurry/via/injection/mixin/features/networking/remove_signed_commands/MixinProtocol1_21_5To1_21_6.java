package me.sofurry.via.injection.mixin.features.networking.remove_signed_commands;

import com.viaversion.viaversion.api.minecraft.GameMode;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ClientboundPacket1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPacket1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ClientboundPacket1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPacket1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import me.sofurry.via.util.NotificationUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(value = Protocol1_21_5To1_21_6.class, remap = false)
public abstract class MixinProtocol1_21_5To1_21_6 extends AbstractProtocol<ClientboundPacket1_21_5, ClientboundPacket1_21_6, ServerboundPacket1_21_5, ServerboundPacket1_21_6> {

    public MixinProtocol1_21_5To1_21_6(final Class<ClientboundPacket1_21_5> unmappedClientboundPacketType, final Class<ClientboundPacket1_21_6> mappedClientboundPacketType, final Class<ServerboundPacket1_21_5> mappedServerboundPacketType, final Class<ServerboundPacket1_21_6> unmappedServerboundPacketType) {
        super(unmappedClientboundPacketType, mappedClientboundPacketType, mappedServerboundPacketType, unmappedServerboundPacketType);
    }

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void cancelInvalidPackets(CallbackInfo ci) {
        registerServerbound(ServerboundPackets1_21_6.CHANGE_GAME_MODE, ServerboundPackets1_21_5.CHAT_COMMAND, wrapper -> {
            if (wrapper.user().getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
                // In ViaVersion, unsigned commands are not allowed to be sent in VV protocols as we implemented signing on a client level
                // where we can't reach and fixup signatures in all cases.
                NotificationUtil.warnIncompatibilityPacket("1.21.6", "CHANGE_GAME_MODE", null);
                wrapper.cancel();
                return;
            }

            final int gameMode = wrapper.read(Types.VAR_INT);
            final GameMode mode = GameMode.getById(gameMode);
            wrapper.write(Types.STRING, "gamemode " + mode.name().toLowerCase(Locale.ROOT));
        }, true);
    }

}
