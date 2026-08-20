package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import me.sofurry.via.features.classic.cpe_extension.CPEAdditions;
import me.sofurry.via.features.classic.cpe_extension.CPEWeatherTypePacketHandlers;
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
        this.registerClientbound(CPEAdditions.EXT_WEATHER_TYPE, null, new CPEWeatherTypePacketHandlers());
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void resetSnowing(CallbackInfo ci) {
        CPEAdditions.setSnowing(false);
    }

}
