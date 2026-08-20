package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen_CoreIntegration extends Screen {

    public MixinJoinMultiplayerScreen_CoreIntegration(final Component component) {
        super(component);
    }

    @WrapOperation(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddress;parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;"))
    private ServerAddress replaceDefaultPort(String input, Operation<ServerAddress> original, @Local(argsOnly = true) ServerData data) {
        final IServerData mixinServerInfo = (IServerData) data;

        final ProtocolVersion version;
        if (mixinServerInfo.viaVersion$passedDirectConnectScreen()) {
            version = ProtocolTranslator.getTargetVersion();
        } else {
            version = mixinServerInfo.viaVersion$forcedVersion();
        }
        return original.call(ClientSetting.replaceDefaultPort(input, version));
    }

    @WrapOperation(method = "directJoinCallback", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;join(Lnet/minecraft/client/multiplayer/ServerData;)V"))
    private void storeDirectConnectionPhase(JoinMultiplayerScreen instance, ServerData data, Operation<Void> original) {
        ((IServerData) data).viaVersion$passDirectConnectScreen(true);
        original.call(instance, data);
    }

}
