package me.sofurry.via.util;

import com.github.epsilon.Constants;

public class NotificationUtil {

    public static void warnIncompatibilityPacket(final String version, final String packet, final String methodName) {
        Constants.LOGGER.error("===========================================");
        Constants.LOGGER.error("The {} packet (>= {}) could not be remapped without breaking content!", packet, version);
        Constants.LOGGER.error("Try disabling mods one by one or using a binary search method to identify the problematic mod.");
        if (methodName != null) {
            Constants.LOGGER.error("Mods authors should use {} instead of sending packets directly.", methodName);
        } else {
            Constants.LOGGER.error("Mod authors should not send this packet directly.");
        }
        Constants.LOGGER.error("Need help? Join our Discord: https://discord.gg/viaversion");
        Constants.LOGGER.error("===========================================");
    }

}
