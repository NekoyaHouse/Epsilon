package me.sofurry.via.injection.mixin.core.integration.bedrock;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.sofurry.via.injection.access.core.IServerData;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerStatusPinger.class)
public abstract class MixinServerStatusPinger {

    @WrapOperation(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddress;parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;"))
    private ServerAddress replaceDefaultPort(String input, Operation<ServerAddress> original, @Local(argsOnly = true) ServerData data) {
        // Replace port when pinging the server and the forced version is set
        return original.call(ClientSetting.replaceDefaultPort(input, ((IServerData) data).viaVersion$forcedVersion()));
    }

}
