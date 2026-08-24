package me.sofurry.via.protocoltranslator.impl.provider.vialegacy;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.connection.UserConnection;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.raphimc.vialegacy.protocol.release.r1_2_4_5tor1_3_1_2.provider.OldAuthProvider;

public class ViaVersionOldAuthProvider extends OldAuthProvider {

    @Override
    public void sendAuthRequest(UserConnection connection, String serverId) {
        if (!ClientSetting.INSTANCE.verifySessionForOnlineModeServers.getValue()) {
            return;
        }

        try {
            final Minecraft client = Minecraft.getInstance();
            client.services().sessionService().joinServer(client.getUser().getProfileId(), client.getUser().getAccessToken(), serverId);
        } catch (Exception e) {
            connection.getChannel().attr(ProtocolTranslator.CLIENT_CONNECTION_ATTRIBUTE_KEY).get().disconnect(ChatUtil.prefixText(EpsilonTranslations.component(EpsilonTranslations.Via.JAVA_FAILED_TO_VERIFY_SESSION)));
            Constants.LOGGER.error("Error occurred while calling join server to verify session", e);
        }
    }

}
