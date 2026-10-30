package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class ACSpoof extends Module {

    public static final ACSpoof INSTANCE = new ACSpoof();

    private ACSpoof() {
        super("AC Spoof", Category.RENDER);
    }

    private static final String ORIGINAL = "Vanilla";
    private static final String REPLACEMENT = "GrimAC";

    public FormattedCharSequence text(FormattedCharSequence text, int color) {
        if (!isEnabled()) return text;

        List<StyledCharacter> characters = new ArrayList<>();
        text.accept((index, style, codePoint) -> {
            characters.add(new StyledCharacter(style, codePoint));
            return true;
        });

        boolean changed = false;
        for (int start = 0; start <= characters.size() - ORIGINAL.length(); start++) {
            if (!matches(characters, start, color)) continue;

            for (int offset = 0; offset < REPLACEMENT.length(); offset++) {
                Style style = characters.get(start + offset).style().withColor(ChatFormatting.AQUA);
                characters.set(start + offset, new StyledCharacter(style, REPLACEMENT.charAt(offset)));
            }
            characters.subList(start + REPLACEMENT.length(), start + ORIGINAL.length()).clear();
            start += REPLACEMENT.length() - 1;
            changed = true;
        }

        if (!changed) return text;

        return sink -> {
            int index = 0;
            for (StyledCharacter character : characters) {
                if (!sink.accept(index, character.style(), character.codePoint())) return false;
                index += Character.charCount(character.codePoint());
            }
            return true;
        };
    }

    public Component text(Component text, int color) {
        if (!isEnabled()) return text;

        FormattedCharSequence original = text.getVisualOrderText();
        FormattedCharSequence replacement = text(original, color);
        if (replacement == original) return text;

        List<StyledCharacter> characters = new ArrayList<>();
        replacement.accept((index, style, codePoint) -> {
            characters.add(new StyledCharacter(style, codePoint));
            return true;
        });

        MutableComponent result = Component.empty();
        StringBuilder builder = new StringBuilder();
        Style currentStyle = null;
        for (StyledCharacter character : characters) {
            if (currentStyle != null && !currentStyle.equals(character.style())) {
                result.append(Component.literal(builder.toString()).withStyle(currentStyle));
                builder.setLength(0);
            }
            currentStyle = character.style();
            builder.appendCodePoint(character.codePoint());
        }
        if (!builder.isEmpty()) {
            result.append(Component.literal(builder.toString()).withStyle(currentStyle == null ? Style.EMPTY : currentStyle));
        }
        return result;
    }

    private static boolean matches(List<StyledCharacter> characters, int start, int color) {
        for (int offset = 0; offset < ORIGINAL.length(); offset++) {
            StyledCharacter character = characters.get(start + offset);
            if (character.codePoint() != ORIGINAL.charAt(offset)) return false;

            TextColor styleColor = character.style().getColor();
            int rgb = (styleColor == null ? color : styleColor.getValue()) & 0xFFFFFF;
            if (rgb != 0x55FF55 && rgb != 0x00AA00 && rgb != 0x00FF00) return false;
        }
        return true;
    }

    private record StyledCharacter(Style style, int codePoint) {
    }

}
