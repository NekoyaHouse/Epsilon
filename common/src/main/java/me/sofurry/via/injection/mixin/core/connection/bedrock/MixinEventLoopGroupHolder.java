package me.sofurry.via.injection.mixin.core.connection.bedrock;

import me.sofurry.via.injection.access.core.bedrock.IEventLoopGroupHolder;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EventLoopGroupHolder.class)
public abstract class MixinEventLoopGroupHolder implements IEventLoopGroupHolder {

    @Unique
    private boolean viaVersion$connecting = false;

    @Inject(method = "remote", at = @At("RETURN"))
    private static void resetConnectingFlag(CallbackInfoReturnable<EventLoopGroupHolder> cir) {
        ((IEventLoopGroupHolder) cir.getReturnValue()).viaVersion$setConnecting(false);
    }

    @Override
    public boolean viaVersion$isConnecting() {
        return viaVersion$connecting;
    }

    @Override
    public void viaVersion$setConnecting(final boolean connecting) {
        this.viaVersion$connecting = connecting;
    }

}
