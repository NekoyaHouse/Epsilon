package me.sofurry.via.injection.mixin.features.networking.remove_signed_commands;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.networking.remove_signed_commands.SignedCommands1_21_6;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {

    @Redirect(method = "handleDebugKeys", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapAsCommand(ClientPacketListener instance, Packet<ServerGamePacketListener> packet) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5) && packet instanceof ServerboundChangeGameModePacket(
                final GameType mode
        )) {
            SignedCommands1_21_6.sendGameMode(mode);
        } else {
            instance.send(packet);
        }
    }

}
