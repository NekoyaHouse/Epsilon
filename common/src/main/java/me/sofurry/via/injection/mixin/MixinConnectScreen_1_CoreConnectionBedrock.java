package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.sofurry.via.injection.access.core.bedrock.IEventLoopGroupHolder;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class MixinConnectScreen_1_CoreConnectionBedrock {

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Ljava/lang/Exception;getMessage()Ljava/lang/String;", remap = false))
    private String handleNullExceptionMessage(Exception instance, Operation<String> original) {
        // Vanilla doesn't have these cases, but we do because of RakNet and other modifications to the Netty pipeline
        return instance.getMessage() == null ? "" : original.call(instance);
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/EventLoopGroupHolder;remote(Z)Lnet/minecraft/server/network/EventLoopGroupHolder;"))
    private EventLoopGroupHolder markAsConnecting(boolean allowNativeTransport) {
        final EventLoopGroupHolder holder = EventLoopGroupHolder.remote(allowNativeTransport);
        ((IEventLoopGroupHolder) holder).viaVersion$setConnecting(true);
        return holder;
    }

}
