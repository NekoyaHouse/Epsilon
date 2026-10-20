package com.github.epsilon.features;

import com.github.epsilon.modules.impl.player.AutoReconnect;
import static com.github.epsilon.Constants.mc;

/** Main-thread lifecycle marker. Intentional quits always win over reconnect. */
public final class DisconnectGuard {
    private static boolean networkCallback;
    private static boolean blocked;
    private DisconnectGuard() {}
    public static void networkBegin() { networkCallback = true; }
    public static void networkEnd() { networkCallback = false; }
    public static void disconnecting() { if (!networkCallback) suppress(); }
    public static void suppress() { blocked = true; AutoReconnect.INSTANCE.cancel(); }
    public static boolean unexpected() { return networkCallback && !blocked; }
    public static void joined() { blocked = false; networkCallback = false; }
    public static void safetyLogout(String reason) {
        if (mc.getConnection() == null) return;
        suppress();
        mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal(reason));
    }
}
