package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.item.ItemHasher;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.rewriter.StructuredItemRewriter;
import me.sofurry.via.protocoltranslator.protocol.ViaVersionProtocol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StructuredItemRewriter.class, remap = false)
public abstract class MixinStructuredItemRewriter {

    @Redirect(method = "isFirstServerbound", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/connection/UserConnection;getItemHasher(Lcom/viaversion/viaversion/api/protocol/Protocol;)Lcom/viaversion/viaversion/api/data/item/ItemHasher;"))
    private ItemHasher skipUnregisteredViaVersionProtocol(UserConnection connection, Protocol<?, ?, ?, ?> protocol) {
        return protocol == ViaVersionProtocol.INSTANCE ? null : connection.getItemHasher(protocol);
    }

}
