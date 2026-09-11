package com.github.epsilon.utils.player;

import com.github.epsilon.interfaces.ChatComponentAccessor;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.render.ColorUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import static com.github.epsilon.Constants.mc;

public class ChatUtils {

    public static final String PREFIX = "[Epsilon] ";

    private static final double GRADIENT_CHAR_STEP = 0.55D;

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param message 消息内容
     */
    public static void addChatMessage(String message) {
        addChatMessage(true, Component.literal(message));
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param message 消息内容
     */
    public static void addChatMessage(Component message) {
        addChatMessage(true, message);
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     */
    public static void addChatMessage(boolean prefix, String message) {
        addChatMessage(prefix, Component.literal(message));
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     */
    public static void addChatMessage(boolean prefix, Component message) {
        Component component = buildClientMessage(prefix, message);
        if (mc.isSameThread()) {
            mc.gui.hud.getChat().addClientSystemMessage(component);
        } else {
            mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(component));
        }
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    public static void addChatMessage(String message, int hash) {
        addChatMessage(true, Component.literal(message), hash);
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    public static void addChatMessage(Component message, int hash) {
        addChatMessage(true, message, hash);
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    public static void addChatMessage(boolean prefix, String message, int hash) {
        addChatMessage(prefix, Component.literal(message), hash);
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    public static void addChatMessage(boolean prefix, Component message, int hash) {
        Component component = buildClientMessage(prefix, message);
        if (mc.isSameThread()) {
            ((ChatComponentAccessor) mc.gui.hud.getChat()).epsilon$addClientSystemMessage(component, hash);
        } else {
            mc.execute(() -> ((ChatComponentAccessor) mc.gui.hud.getChat()).epsilon$addClientSystemMessage(component, hash));
        }
    }

    /**
     * 构建可选带 Epsilon 前缀的客户端聊天消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     * @return 操作结果
     */
    public static Component buildClientMessage(boolean prefix, String message) {
        return buildClientMessage(prefix, Component.literal(message));
    }

    /**
     * 构建可选带 Epsilon 前缀的客户端聊天消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     * @return 操作结果
     */
    public static Component buildClientMessage(boolean prefix, Component message) {
        MutableComponent component = Component.empty();
        if (prefix) {
            component.append(Component.literal(PREFIX));
        }
        return component.append(message);
    }

    /**
     * 为消息中的 Epsilon 前缀应用动态渐变颜色。
     *
     * @param original 原始格式化文本
     * @return 操作结果
     */
    public static FormattedCharSequence applyAnimatedPrefix(FormattedCharSequence original) {
        if (!ClientSetting.INSTANCE.animatedChatPrefix.getValue()) {
            return original;
        }

        String rawLine = toPlainString(original);
        if (!rawLine.startsWith(PREFIX)) {
            return original;
        }

        MutableComponent gradientLine = Component.empty();
        double animationTime = System.currentTimeMillis() / 180.0 * ClientSetting.INSTANCE.chatPrefixGradientSpeed.getValue();

        int visualIndex = 0;
        for (int offset = 0; offset < PREFIX.length(); ) {
            int codePoint = PREFIX.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            float blend = (float) ((Math.sin(animationTime - visualIndex * GRADIENT_CHAR_STEP) + 1.0D) * 0.5D);
            int color = ColorUtils.interpolateColor(ClientSetting.INSTANCE.chatPrefixColorStart.getValue(), ClientSetting.INSTANCE.chatPrefixColorEnd.getValue(), blend).getRGB() & 0xFFFFFF;

            gradientLine.append(Component.literal(character).withStyle(Style.EMPTY.withColor(color)));
            offset += Character.charCount(codePoint);
            visualIndex++;
        }

        appendStyledSuffix(gradientLine, original, PREFIX.length());
        return gradientLine.getVisualOrderText();
    }

    private static void appendStyledSuffix(MutableComponent component, FormattedCharSequence sequence, int skipCodePoints) {
        int[] seenCodePoints = {0};
        sequence.accept((index, style, codePoint) -> {
            if (seenCodePoints[0]++ >= skipCodePoints) {
                component.append(Component.literal(new String(Character.toChars(codePoint))).withStyle(style));
            }
            return true;
        });
    }

    private static String toPlainString(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

}
