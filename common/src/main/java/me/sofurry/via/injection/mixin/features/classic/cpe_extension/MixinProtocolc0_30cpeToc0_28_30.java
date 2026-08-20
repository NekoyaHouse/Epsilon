package me.sofurry.via.injection.mixin.features.classic.cpe_extension;

import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandlers;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;
import me.sofurry.via.features.classic.cpe_extension.CPEAdditions;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.packet.ClientboundPacketsc0_28;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.packet.ServerboundPacketsc0_28;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.Protocolc0_30cpeToc0_28_30;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.packet.ClientboundPacketsc0_30cpe;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.packet.ServerboundPacketsc0_30cpe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Protocolc0_30cpeToc0_28_30.class, remap = false)
public abstract class MixinProtocolc0_30cpeToc0_28_30 extends AbstractProtocol<ClientboundPacketsc0_30cpe, ClientboundPacketsc0_28, ServerboundPacketsc0_30cpe, ServerboundPacketsc0_28> {

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void extendPackets(CallbackInfo ci) {
        this.registerClientbound(CPEAdditions.EXT_WEATHER_TYPE, null, new PacketHandlers() {
            @Override
            public void register() {
                handler(wrapper -> {
                    wrapper.cancel();
                    final byte weatherType = wrapper.read(Types.BYTE);

                    final PacketWrapper changeRainState = PacketWrapper.create(ClientboundPackets1_19_4.GAME_EVENT, wrapper.user());
                    changeRainState.write(Types.UNSIGNED_BYTE, weatherType == 0 /* sunny */ ? (short) 1 : (short) 2); // start raining
                    changeRainState.write(Types.FLOAT, 0F); // unused
                    changeRainState.send(Protocol1_19_3To1_19_4.class);

                    if (weatherType == 1 /* raining */ || weatherType == 2 /* snowing */) {
                        final PacketWrapper changeRainType = PacketWrapper.create(ClientboundPackets1_19_4.GAME_EVENT, wrapper.user());
                        changeRainType.write(Types.UNSIGNED_BYTE, (short) 7); // set rain gradient
                        changeRainType.write(Types.FLOAT, 1F);
                        changeRainType.send(Protocol1_19_3To1_19_4.class);
                    }
                    CPEAdditions.setSnowing(weatherType == 2);
                });
            }
        });
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void resetSnowing(CallbackInfo ci) {
        CPEAdditions.setSnowing(false);
    }

}
