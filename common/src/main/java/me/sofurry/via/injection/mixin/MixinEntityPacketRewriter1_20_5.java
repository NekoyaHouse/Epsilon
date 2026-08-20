package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.rewriter.EntityPacketRewriter1_20_5;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(value = EntityPacketRewriter1_20_5.class, remap = false)
public abstract class MixinEntityPacketRewriter1_20_5 {

    @Shadow
    protected abstract void writeAttribute(PacketWrapper wrapper, String attributeId, double base, UUID modifierId, double amount);

    @Redirect(method = "sendRangeAttributes", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/protocols/v1_20_3to1_20_5/rewriter/EntityPacketRewriter1_20_5;writeAttribute(Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;Ljava/lang/String;DLjava/util/UUID;D)V"))
    private void useLegacyValues(EntityPacketRewriter1_20_5 instance, PacketWrapper wrapper, String attributeId, double base, UUID modifierId, double amount) {
        if (attributeId.equals("player.block_interaction_range") && wrapper.user().getProtocolInfo().serverProtocolVersion().olderThan(LegacyProtocolVersion.r1_0_0tor1_0_1)) {
            this.writeAttribute(wrapper, attributeId, 4D, modifierId, 1D);
        } else if (attributeId.equals("player.entity_interaction_range") && wrapper.user().getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
            this.writeAttribute(wrapper, attributeId, 3D, modifierId, 3D);
        } else {
            this.writeAttribute(wrapper, attributeId, base, modifierId, amount);
        }
    }

}
