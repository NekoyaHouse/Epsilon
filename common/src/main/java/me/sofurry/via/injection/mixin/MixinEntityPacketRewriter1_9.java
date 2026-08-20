package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.protocols.v1_8to1_9.Protocol1_8To1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.rewriter.EntityPacketRewriter1_9;
import com.viaversion.viaversion.rewriter.entitydata.EntityDataHandlerEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPacketRewriter1_9.class, remap = false)
public abstract class MixinEntityPacketRewriter1_9 {

    @Inject(method = "handleEntityData", at = @At(value = "FIELD", target = "Lcom/viaversion/viaversion/api/minecraft/entities/EntityTypes1_9$EntityType;PLAYER:Lcom/viaversion/viaversion/api/minecraft/entities/EntityTypes1_9$EntityType;", ordinal = 0, opcode = Opcodes.GETSTATIC), cancellable = true)
    private void preventMetadataForClientPlayer(EntityDataHandlerEvent event, EntityData data, CallbackInfo ci) {
        if (event.user().storables(Protocol1_8To1_9.class).entityTracker().clientEntityId() == event.entityId()) {
            ci.cancel();
        }
    }

}
