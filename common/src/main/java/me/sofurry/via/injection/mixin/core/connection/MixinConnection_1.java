package me.sofurry.via.injection.mixin.core.connection;

import io.netty.channel.Channel;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class MixinConnection_1 {

    @Final
    @Shadow
    Connection val$connection;

    @Inject(method = "initChannel", at = @At("RETURN"))
    private void injectViaIntoPipeline(Channel channel, CallbackInfo ci) {
        ProtocolTranslator.injectViaPipeline(this.val$connection, channel);
    }

}
