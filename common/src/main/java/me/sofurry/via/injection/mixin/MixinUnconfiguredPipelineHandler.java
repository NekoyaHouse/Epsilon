package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.ChannelConfig;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.network.UnconfiguredPipelineHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(UnconfiguredPipelineHandler.class)
public abstract class MixinUnconfiguredPipelineHandler {

    @WrapOperation(method = "lambda$setupInboundHandler$0", at = @At(value = "INVOKE", target = "Lio/netty/channel/ChannelConfig;setAutoRead(Z)Lio/netty/channel/ChannelConfig;", remap = false))
    private static ChannelConfig dontChangeAutoRead(ChannelConfig instance, boolean b, Operation<ChannelConfig> original) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
            return null;
        } else {
            return original.call(instance, b);
        }
    }

}
