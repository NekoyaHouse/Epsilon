package me.sofurry.via.injection.mixin.features.bedrock.inventory;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InteractPacket_Action;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sendBedrockPacket(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            final UserConnection connection = ProtocolTranslator.getPlayNetworkUserConnection();

            final PacketWrapper interact = PacketWrapper.create(ServerboundBedrockPackets.INTERACT, connection);
            interact.write(Types.UNSIGNED_BYTE, (short) InteractPacket_Action.OpenInventory.getValue()); // action
            interact.write(BedrockTypes.UNSIGNED_VAR_LONG, connection.get(EntityTracker.class).getClientPlayer().runtimeId()); // target entity runtime id
            interact.write(BedrockTypes.OPTIONAL_POSITION_3F, null); // position
            interact.sendToServer(BedrockProtocol.class);
        }
    }

}
