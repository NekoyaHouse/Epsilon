package com.github.epsilon.graphics.text

interface IFontLoader {

    fun checkAndLoadChar(ch: Char)

    fun checkAndLoadChars(chars: String)

    fun destroy()

    fun getGlyph(ch: Char): GlyphDescriptor?

}
