package me.sofurry.via.protocoltranslator.util;

import io.netty.channel.local.LocalChannel;

/**
 * This class can be used when creating dummy {@link com.viaversion.viaversion.api.connection.UserConnection} instances.
 * This class will prevent packets created by ViaVersion from being sent to the server. This is mainly used in the
 * translator system. See {@link me.sofurry.via.protocoltranslator.translator} for more information.
 */
public class NoPacketSendChannel extends LocalChannel {

    public static final NoPacketSendChannel INSTANCE = new NoPacketSendChannel();

    private NoPacketSendChannel() {
    }

}
