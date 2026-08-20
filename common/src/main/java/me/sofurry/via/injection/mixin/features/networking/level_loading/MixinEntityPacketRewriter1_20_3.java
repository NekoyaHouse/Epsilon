package me.sofurry.via.injection.mixin.features.networking.level_loading;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.rewriter.EntityPacketRewriter1_20_3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = EntityPacketRewriter1_20_3.class, remap = false)
public abstract class MixinEntityPacketRewriter1_20_3 {

    /**
     * @author Florian Reuth (EnZaXD)
     * @reason Handled in MixinClientPacketListener
     */
    @Overwrite
    private void sendChunksSentGameEvent(final PacketWrapper wrapper) {
    }

}
