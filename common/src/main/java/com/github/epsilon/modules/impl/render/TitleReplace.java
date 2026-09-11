package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.github.epsilon.utils.render.ColorUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitleReplace extends Module {

    public static final TitleReplace INSTANCE = new TitleReplace();

    private TitleReplace() {
        super("Title Replace", Category.PLAYER);
    }

    private final StringSetting value = stringSetting("Value", "跨圈皇帝萌萌刻");
    private final BoolSetting gradient = boolSetting("Gradient", true);
    private final ColorSetting color = colorSetting("Color", new Color(0x55FFFF), false, () -> !gradient.getValue());
    private final ColorSetting startColor = colorSetting("Start Color", new Color(255, 183, 197), false, gradient::getValue);
    private final ColorSetting endColor = colorSetting("End Color", new Color(255, 133, 161), false, gradient::getValue);

    private static final Pattern PATTERN = Pattern.compile("(祝你好运\\s*)[^!！\\n]*");

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSetTitleTextPacket(Component text)) {
            Component replaced = replace(text);
            if (replaced != null) event.setPacket(new ClientboundSetTitleTextPacket(replaced));
        } else if (event.getPacket() instanceof ClientboundSetSubtitleTextPacket(Component text)) {
            Component replaced = replace(text);
            if (replaced != null) event.setPacket(new ClientboundSetSubtitleTextPacket(replaced));
        } else if (event.getPacket() instanceof ClientboundSetActionBarTextPacket(Component text)) {
            Component replaced = replace(text);
            if (replaced != null) event.setPacket(new ClientboundSetActionBarTextPacket(replaced));
        }
    }

    private Component replace(Component component) {
        String flat = component.getString();
        Matcher matcher = PATTERN.matcher(flat);
        if (!matcher.find()) return null;
        String prefix = flat.substring(0, matcher.start()) + matcher.group(1);
        String tail = flat.substring(matcher.end());
        return Component.literal(prefix).append(styledValue()).append(Component.literal(tail));
    }

    private Component styledValue() {
        String text = value.getValue();
        if (!gradient.getValue()) {
            return Component.literal(text).withColor(color.getValue().getRGB() & 0xFFFFFF);
        }

        int[] codePoints = text.codePoints().toArray();
        MutableComponent result = Component.empty();
        for (int i = 0; i < codePoints.length; i++) {
            float progress = codePoints.length == 1 ? 0.0f : (float) i / (codePoints.length - 1);
            int interpolatedColor = ColorUtils.interpolateColor(startColor.getValue(), endColor.getValue(), progress).getRGB() & 0xFFFFFF;
            result.append(Component.literal(Character.toString(codePoints[i])).withColor(interpolatedColor));
        }
        return result;
    }

}
