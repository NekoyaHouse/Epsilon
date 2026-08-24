package me.sofurry.via.features.networking.remove_signed_commands;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

public class SignedCommands1_21_6 {

    public static void sendGameMode(final GameType gameMode) {
        final String command;
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_2_4tor1_2_5)) {
            final String username = Minecraft.getInstance().getUser().getName();
            command = "gamemode " + username + " " + (gameMode.getId() > 1 ? 0 : gameMode.getName());
        } else {
            command = "gamemode " + gameMode.getName();
        }

        Minecraft.getInstance().getConnection().sendCommand(command);
    }

}
