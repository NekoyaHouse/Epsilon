package me.sofurry.via.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * This class contains methods to send messages to the player with the ViaVersion prefix
 */
public class ChatUtil {

    public static final String PREFIX = ChatFormatting.WHITE + "[" + ChatFormatting.GOLD + "ViaVersion" + ChatFormatting.WHITE + "]";
    public static final Component PREFIX_TEXT = Component.literal("[").withStyle(ChatFormatting.WHITE).append(Component.literal("ViaVersion").withStyle(ChatFormatting.GOLD)).append("]");

    /**
     * Prefixes the message with the ViaVersion prefix
     *
     * @param message The message to send
     * @return The prefixed message
     */
    public static Component prefixText(final String message) {
        return prefixText(Component.nullToEmpty(message));
    }

    /**
     * Prefixes the message with the ViaVersion prefix
     *
     * @param message The message to send
     * @return The prefixed message
     */
    public static Component prefixText(final Component message) {
        return Component.empty().append(PREFIX_TEXT).append(" ").append(message);
    }

    /**
     * Sends a prefixed message to the player
     *
     * @param message The message to send
     */
    public static void sendPrefixedMessage(final Component message) {
        if (Minecraft.getInstance().isSameThread()) {
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(prefixText(message));
        } else {
            Minecraft.getInstance().execute(() -> sendPrefixedMessage(message));
        }
    }

    /**
     * @param text The text to uncover
     * @return The translation key of the text
     */
    public static String uncoverTranslationKey(final Component text) {
        return ((TranslatableContents) text.getContents()).getKey();
    }

}
