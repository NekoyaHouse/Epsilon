package me.sofurry.via.features.limitation.max_chat_length;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.storage.ExtensionProtocolMetadataStorage;

public final class MaxChatLength {

    public static final int MAX_CHAT_LENGTH_LATEST = 256;

    public static int getChatLength() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.c0_28toc0_30)) {
            final ClientPacketListener handler = Minecraft.getInstance().getConnection();
            final ExtensionProtocolMetadataStorage extensionProtocol = ((IConnection) handler.getConnection()).viaVersion$getUserConnection().get(ExtensionProtocolMetadataStorage.class);

            if (extensionProtocol != null && extensionProtocol.hasServerExtension(ClassicProtocolExtension.LONGER_MESSAGES)) {
                return Short.MAX_VALUE * 2;
            } else {
                return 64 - (Minecraft.getInstance().getUser().getName().length() + 2);
            }
        } else if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return 512;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_9_3)) {
            return 100;
        } else {
            return MAX_CHAT_LENGTH_LATEST;
        }
    }

}
