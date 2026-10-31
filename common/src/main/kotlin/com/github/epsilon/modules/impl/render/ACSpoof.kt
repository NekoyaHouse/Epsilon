package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

object ACSpoof : Module("AC Spoof", Category.RENDER) {

    private const val ORIGINAL = "Vanilla"
    private const val REPLACEMENT = "GrimAC"

    fun text(text: FormattedCharSequence, color: Int): FormattedCharSequence {
        if (!isEnabled) return text

        val characters = ArrayList<StyledCharacter>()
        text.accept { index, style, codePoint ->
            characters.add(StyledCharacter(style, codePoint))
            true
        }

        var changed = false
        var start = 0
        while (start <= characters.size - ORIGINAL.length) {
            if (!matches(characters, start, color)) {
                start++
                continue
            }

            for (offset in 0 until REPLACEMENT.length) {
                val style = characters[start + offset].style.withColor(ChatFormatting.AQUA)
                characters[start + offset] = StyledCharacter(style, REPLACEMENT[offset].code)
            }
            characters.subList(start + REPLACEMENT.length, start + ORIGINAL.length).clear()
            start += REPLACEMENT.length - 1
            changed = true
            start++
        }

        if (!changed) return text

        return FormattedCharSequence { sink ->
            var index = 0
            var accepted = true
            for (character in characters) {
                if (!sink.accept(index, character.style, character.codePoint)) {
                    accepted = false
                    break
                }
                index += Character.charCount(character.codePoint)
            }
            accepted
        }
    }

    private fun matches(characters: List<StyledCharacter>, start: Int, color: Int): Boolean {
        for (offset in 0 until ORIGINAL.length) {
            val character = characters[start + offset]
            if (character.codePoint != ORIGINAL[offset].code) return false

            val styleColor = character.style.color
            val rgb = (styleColor?.value ?: color) and 0xFFFFFF
            if (rgb != 0x55FF55 && rgb != 0x00AA00 && rgb != 0x00FF00) return false
        }
        return true
    }

    private data class StyledCharacter(val style: Style, val codePoint: Int)

}
