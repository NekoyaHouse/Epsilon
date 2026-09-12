package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.ClientboundEntityEventPacketAccessor;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundEntityEventPacket.class)
public abstract class MixinClientboundEntityEventPacket implements ClientboundEntityEventPacketAccessor {

    @Override
    @Accessor("entityId")
    public abstract int epsilon$getEntityId();
}
