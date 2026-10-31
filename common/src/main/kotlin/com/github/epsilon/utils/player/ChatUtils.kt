package com.github.epsilon.utils.player

import com.github.epsilon.Constants.mc
import com.github.epsilon.interfaces.ChatComponentAccessor
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.render.ColorUtils
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import kotlin.math.sin

/**
 * 客户端聊天栏消息工具类。
 *
 * 不依赖世界，因此直接使用 [mc]；消息投递的线程判断与原实现一致。
 */
object ChatUtils {

    const val PREFIX: String = "[Epsilon] "

    private const val GRADIENT_CHAR_STEP = 0.55

    /**
     * 向客户端聊天栏添加消息。
     */
    fun addChatMessage(message: String) {
        addChatMessage(true, Component.literal(message))
    }

    /**
     * 向客户端聊天栏添加消息。
     */
    fun addChatMessage(message: Component) {
        addChatMessage(true, message)
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     */
    fun addChatMessage(prefix: Boolean, message: String) {
        addChatMessage(prefix, Component.literal(message))
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     */
    fun addChatMessage(prefix: Boolean, message: Component) {
        val component = buildClientMessage(prefix, message)
        if (mc.isSameThread) {
            mc.gui.hud.chat.addClientSystemMessage(component)
        } else {
            mc.execute { mc.gui.hud.chat.addClientSystemMessage(component) }
        }
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    fun addChatMessage(message: String, hash: Int) {
        addChatMessage(true, Component.literal(message), hash)
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    fun addChatMessage(message: Component, hash: Int) {
        addChatMessage(true, message, hash)
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    fun addChatMessage(prefix: Boolean, message: String, hash: Int) {
        addChatMessage(prefix, Component.literal(message), hash)
    }

    /**
     * 向客户端聊天栏添加消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     * @param hash    用于替换同一条聊天消息的稳定标识
     */
    fun addChatMessage(prefix: Boolean, message: Component, hash: Int) {
        val component = buildClientMessage(prefix, message)
        if (mc.isSameThread) {
            (mc.gui.hud.chat as ChatComponentAccessor).`epsilon$addClientSystemMessage`(component, hash)
        } else {
            mc.execute {
                (mc.gui.hud.chat as ChatComponentAccessor).`epsilon$addClientSystemMessage`(component, hash)
            }
        }
    }

    /**
     * 构建可选带 Epsilon 前缀的客户端聊天消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     */
    fun buildClientMessage(prefix: Boolean, message: String): Component {
        return buildClientMessage(prefix, Component.literal(message))
    }

    /**
     * 构建可选带 Epsilon 前缀的客户端聊天消息。
     *
     * @param prefix  是否添加 Epsilon 消息前缀
     * @param message 消息内容
     */
    fun buildClientMessage(prefix: Boolean, message: Component): Component {
        val component = Component.empty()
        if (prefix) {
            component.append(Component.literal(PREFIX))
        }
        return component.append(message)
    }

    /**
     * 为消息中的 Epsilon 前缀应用动态渐变颜色。
     *
     * @param original 原始格式化文本
     */
    fun applyAnimatedPrefix(original: FormattedCharSequence): FormattedCharSequence {
        if (!ClientSetting.animatedChatPrefix) {
            return original
        }

        val rawLine = toPlainString(original)
        if (!rawLine.startsWith(PREFIX)) {
            return original
        }

        val gradientLine = Component.empty()
        val animationTime = System.currentTimeMillis() / 180.0 * ClientSetting.chatPrefixGradientSpeed

        var visualIndex = 0
        var offset = 0
        while (offset < PREFIX.length) {
            val codePoint = PREFIX.codePointAt(offset)
            val character = String(Character.toChars(codePoint))
            val blend = ((sin(animationTime - visualIndex * GRADIENT_CHAR_STEP) + 1.0) * 0.5).toFloat()
            val color = ColorUtils.interpolateColor(
                ClientSetting.chatPrefixColorStart,
                ClientSetting.chatPrefixColorEnd,
                blend,
            ).rgb and 0xFFFFFF

            gradientLine.append(Component.literal(character).withStyle(Style.EMPTY.withColor(color)))
            offset += Character.charCount(codePoint)
            visualIndex++
        }

        appendStyledSuffix(gradientLine, original, PREFIX.length)
        return gradientLine.visualOrderText
    }

    private fun appendStyledSuffix(component: MutableComponent, sequence: FormattedCharSequence, skipCodePoints: Int) {
        var seenCodePoints = 0
        sequence.accept { _, style, codePoint ->
            if (seenCodePoints++ >= skipCodePoints) {
                component.append(Component.literal(String(Character.toChars(codePoint))).withStyle(style))
            }
            true
        }
    }

    private fun toPlainString(sequence: FormattedCharSequence): String {
        val builder = StringBuilder()
        sequence.accept { _, _, codePoint ->
            builder.appendCodePoint(codePoint)
            true
        }
        return builder.toString()
    }

}
