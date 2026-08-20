package me.sofurry.via.protocoltranslator.netty;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.exception.CancelCodecException;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import io.netty.channel.ChannelHandlerContext;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.util.ChatUtil;
import net.minecraft.ChatFormatting;

public final class ViaVersionDecoder extends ViaDecodeHandler {

    public ViaVersionDecoder(UserConnection connection) {
        super(connection);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            super.channelRead(ctx, msg);
        } catch (Throwable e) {
            if (e instanceof CancelCodecException) {
                return;
            }

            final int mode = ClientSetting.INSTANCE.ignorePacketTranslationErrors.getModeIndex();
            if (mode > 0) {
                ViaVersionImpl.INSTANCE.getLogger().error("Error occurred while decoding packet in ViaVersion decoder", e);
                if (mode == 1) {
                    ChatUtil.sendPrefixedMessage(EpsilonTranslations.component(EpsilonTranslations.Via.PACKET_ERROR).withStyle(ChatFormatting.RED));
                }
            } else {
                throw e;
            }
        }
    }

}
