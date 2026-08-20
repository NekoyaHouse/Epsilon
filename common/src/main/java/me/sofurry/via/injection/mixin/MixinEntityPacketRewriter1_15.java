package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.protocols.v1_14_3to1_14_4.packet.ClientboundPackets1_14_4;
import com.viaversion.viaversion.protocols.v1_14_4to1_15.Protocol1_14_4To1_15;
import com.viaversion.viaversion.protocols.v1_14_4to1_15.rewriter.EntityPacketRewriter1_15;
import com.viaversion.viaversion.rewriter.EntityRewriter;
import com.viaversion.viaversion.rewriter.entitydata.EntityDataFilter;
import me.sofurry.via.features.entity.metadata.WolfHealthTracker1_14_4;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityPacketRewriter1_15.class, remap = false)
public abstract class MixinEntityPacketRewriter1_15 extends EntityRewriter<ClientboundPackets1_14_4, Protocol1_14_4To1_15> {

    public MixinEntityPacketRewriter1_15(Protocol1_14_4To1_15 protocol) {
        super(protocol);
    }

    @Redirect(method = "registerRewrites", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/rewriter/entitydata/EntityDataFilter$Builder;removeIndex(I)V"))
    private void removeAndTrackHealth(EntityDataFilter.Builder instance, int index) {
        instance.handler((event, meta) -> {
            final int metaIndex = event.index();
            if (metaIndex == index) {
                event.user().get(WolfHealthTracker1_14_4.class).setWolfHealth(event.entityId(), meta.value());
                event.cancel();
            } else if (metaIndex > index) {
                event.setIndex(metaIndex - 1);
            }
        });
    }

}
