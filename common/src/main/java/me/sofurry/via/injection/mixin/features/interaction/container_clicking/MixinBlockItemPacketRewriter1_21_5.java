package me.sofurry.via.injection.mixin.features.interaction.container_clicking;

import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPacket1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.rewriter.BlockItemPacketRewriter1_21_5;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ClientboundPacket1_21_2;
import com.viaversion.viaversion.rewriter.StructuredItemRewriter;
import me.sofurry.via.util.NotificationUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockItemPacketRewriter1_21_5.class, remap = false)
public abstract class MixinBlockItemPacketRewriter1_21_5 extends StructuredItemRewriter<ClientboundPacket1_21_2, ServerboundPacket1_21_5, Protocol1_21_4To1_21_5> {

    public MixinBlockItemPacketRewriter1_21_5(final Protocol1_21_4To1_21_5 protocol) {
        super(protocol);
    }

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void removeContainerClickHandler(CallbackInfo ci) {
        // Don't allow mods to directly send window interactions which would skip our clientside fix in MixinClientPlayerInteractionManager
        // Use ClientPlayerInteractionManager#clickSlot instead
        protocol.registerServerbound(ServerboundPackets1_21_5.CONTAINER_CLICK, ServerboundPackets1_21_4.CONTAINER_CLICK, wrapper -> {
            NotificationUtil.warnIncompatibilityPacket("1.21.5", "CONTAINER_CLICK", "MultiPlayerGameMode#handleInventoryMouseClick");
            wrapper.cancel();
        }, true);
    }

}
