package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_11_1to1_12.Protocol1_11_1To1_12;
import com.viaversion.viaversion.protocols.v1_9_1to1_9_3.packet.ServerboundPackets1_9_3;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_FeaturesNetworkingOpenInventoryPacket {

    @Shadow
    public LocalPlayer player;

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/Tutorial;onOpenInventory()V", shift = At.Shift.AFTER))
    private void sendOpenInventoryPacket(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
            final PacketWrapper clientCommand = PacketWrapper.create(ServerboundPackets1_9_3.CLIENT_COMMAND, ProtocolTranslator.getPlayNetworkUserConnection());
            clientCommand.write(Types.VAR_INT, 2); // Open Inventory Achievement
            clientCommand.scheduleSendToServer(Protocol1_11_1To1_12.class);
        }
    }

}
