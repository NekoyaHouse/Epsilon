package me.sofurry.via.injection.mixin.features.networking.server_pinging;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.injection.access.core.IServerData;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.multiplayer.ServerStatusPinger$1")
public abstract class MixinServerStatusPinger_1 {

    @Final
    @Shadow
    ServerData val$data;

    @Shadow
    private static Component sanitizeDescription(final Component original) {
        return null;
    }

    @Redirect(method = "handleStatusResponse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerStatusPinger$1;sanitizeDescription(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"))
    private Component removeSanitizeDescription(Component component) {
        if (ClientSetting.INSTANCE.removeServerDescriptionSanitize.isEnabled(((IServerData) val$data).viaVersion$translatingVersion())) {
            return component;
        } else {
            return sanitizeDescription(component);
        }
    }

}
