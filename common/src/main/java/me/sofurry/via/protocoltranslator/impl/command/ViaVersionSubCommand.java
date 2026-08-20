package me.sofurry.via.protocoltranslator.impl.command;

import com.viaversion.viaversion.api.command.ViaCommandSender;
import com.viaversion.viaversion.api.command.ViaSubCommand;
import com.viaversion.viaversion.api.connection.UserConnection;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ChatUtil;

public interface ViaVersionSubCommand extends ViaSubCommand {

    /**
     * Automatically prefix all messages
     */
    default void sendMessage(final ViaCommandSender sender, final String message) {
        ViaSubCommand.super.sendMessage(sender, ChatUtil.PREFIX + " " + message);
    }

    default UserConnection getUser() {
        return ProtocolTranslator.getPlayNetworkUserConnection();
    }

}
