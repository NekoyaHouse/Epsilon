package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.rewriter.BlockItemPacketRewriter1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.rewriter.BlockItemPacketRewriter1_21_5;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {BlockItemPacketRewriter1_21_4.class, BlockItemPacketRewriter1_21_5.class}, remap = false)
public abstract class MixinBlockItemPacketRewriter1_21_X {

    @Redirect(method = "appendItemDataFixComponents", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;olderThanOrEqualTo(Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;)Z"))
    private boolean changeSwordFixVersionRange(ProtocolVersion instance, ProtocolVersion other) {
        if (other == ProtocolVersion.v1_8) {
            return instance.betweenInclusive(LegacyProtocolVersion.b1_8tob1_8_1, ProtocolVersion.v1_8);
        } else {
            return instance.olderThanOrEqualTo(other);
        }
    }

}
