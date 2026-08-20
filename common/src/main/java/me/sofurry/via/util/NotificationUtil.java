package me.sofurry.via.util;

import me.sofurry.via.ViaVersionImpl;
import org.apache.logging.log4j.Logger;

public final class NotificationUtil {

    public static void warnIncompatibilityPacket(final String version, final String packet, final String methodName) {
        final Logger logger = ViaVersionImpl.INSTANCE.getLogger();
        logger.error("===========================================");
        logger.error("The {} packet (>= {}) could not be remapped without breaking content!", packet, version);
        logger.error("Try disabling mods one by one or using a binary search method to identify the problematic mod.");
        if (methodName != null) {
            logger.error("Mods authors should use {} instead of sending packets directly.", methodName);
        } else {
            logger.error("Mod authors should not send this packet directly.");
        }
        logger.error("Need help? Join our Discord: https://discord.gg/viaversion");
        logger.error("===========================================");
    }

}
