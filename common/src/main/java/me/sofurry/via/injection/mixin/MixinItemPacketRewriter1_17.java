package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ClientboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.Protocol1_16_4To1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ServerboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.rewriter.ItemPacketRewriter1_17;
import com.viaversion.viaversion.rewriter.ItemRewriter;
import me.sofurry.via.util.NotificationUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemPacketRewriter1_17.class, remap = false)
public abstract class MixinItemPacketRewriter1_17 extends ItemRewriter<ClientboundPackets1_16_2, ServerboundPackets1_17, Protocol1_16_4To1_17> {

    public MixinItemPacketRewriter1_17(Protocol1_16_4To1_17 protocol, Type<Item> itemType, Type<Item[]> itemArrayType) {
        super(protocol, itemType, itemArrayType);
    }

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void removeContainerClickHandler(CallbackInfo ci) {
        // Don't allow mods to directly send window interactions which would skip our clientside fix in MixinClientPlayerInteractionManager
        // Use ClientPlayerInteractionManager#clickSlot instead
        this.protocol.registerServerbound(ServerboundPackets1_17.CONTAINER_CLICK, ServerboundPackets1_16_2.CONTAINER_CLICK, wrapper -> {
            NotificationUtil.warnIncompatibilityPacket("1.17", "CONTAINER_CLICK", "MultiPlayerGameMode#handleInventoryMouseClick");
            wrapper.cancel();
        }, true);
    }

}
