package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener_FeaturesNetworkingRunCommandAction {

    @Shadow
    protected abstract ClientPacketListener.CommandCheckResult verifyCommand(final String command);

    @Redirect(method = "sendUnattendedCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;verifyCommand(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/ClientPacketListener$CommandCheckResult;"))
    private ClientPacketListener.CommandCheckResult dontOpenConfirmationScreens(ClientPacketListener instance, String command) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            final ClientPacketListener.CommandCheckResult result = verifyCommand(command);
            return result == ClientPacketListener.CommandCheckResult.PARSE_ERRORS || result == ClientPacketListener.CommandCheckResult.PERMISSIONS_REQUIRED ? ClientPacketListener.CommandCheckResult.NO_ISSUES : result;
        } else {
            return verifyCommand(command);
        }
    }

}
