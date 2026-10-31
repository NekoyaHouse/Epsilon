package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.group
import com.github.epsilon.utils.render.ColorUtils
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import java.awt.Color
import java.util.regex.Pattern

object TitleReplace : Module("Title Replace", Category.PLAYER) {

    private val value by stringSetting("Value", "跨圈皇帝萌萌刻")
    private val gradient by boolSetting("Gradient", true)
    private val color by colorSetting("Color", Color(0x55FFFF), false, { !gradient })
    private val startColor by colorSetting("Start Color", Color(255, 183, 197), false, { gradient })
    private val endColor by colorSetting("End Color", Color(255, 133, 161), false, { gradient })

    private val PATTERN: Pattern = Pattern.compile("(祝你好运\\s*)[^!！\\n]*")

    init {
        handler<PacketEvent.Receive> { onPacketReceive(it) }
    }

    private fun onPacketReceive(event: PacketEvent.Receive) {
        when (val packet = event.packet) {
            is ClientboundSetTitleTextPacket -> {
                val replaced = replace(packet.text)
                if (replaced != null) event.packet = ClientboundSetTitleTextPacket(replaced)
            }

            is ClientboundSetSubtitleTextPacket -> {
                val replaced = replace(packet.text)
                if (replaced != null) event.packet = ClientboundSetSubtitleTextPacket(replaced)
            }

            is ClientboundSetActionBarTextPacket -> {
                val replaced = replace(packet.text)
                if (replaced != null) event.packet = ClientboundSetActionBarTextPacket(replaced)
            }
        }
    }

    private fun replace(component: Component): Component? {
        val flat = component.string
        val matcher = PATTERN.matcher(flat)
        if (!matcher.find()) return null
        val prefix = flat.substring(0, matcher.start()) + matcher.group(1)
        val tail = flat.substring(matcher.end())
        return Component.literal(prefix).append(styledValue()).append(Component.literal(tail))
    }

    private fun styledValue(): Component {
        val text = value
        if (!gradient) {
            return Component.literal(text).withColor(color.rgb and 0xFFFFFF)
        }

        val codePoints = text.codePoints().toArray()
        val result: MutableComponent = Component.empty()
        for (i in codePoints.indices) {
            val progress = if (codePoints.size == 1) 0.0f else i.toFloat() / (codePoints.size - 1)
            val interpolatedColor =
                ColorUtils.interpolateColor(startColor, endColor, progress).rgb and 0xFFFFFF
            result.append(Component.literal(Character.toString(codePoints[i])).withColor(interpolatedColor))
        }
        return result
    }

}
