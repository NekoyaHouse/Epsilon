package com.github.epsilon.graphics.text

import com.github.epsilon.graphics.LuminTexture
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * @author 06789
 * 我也不知道这他妈是个什么 78 东西。
 */
object SystemEmojiAtlas : AutoCloseable {

    private const val ATLAS_SIZE = 512
    private const val CELL_SIZE = 128
    private const val CELLS_PER_ROW = ATLAS_SIZE / CELL_SIZE
    private const val FONT_SIZE = 96

    private val glyphs = LinkedHashMap<String, EmojiGlyph>()
    private val emojiFont: Font
    private val colorGlyphFont: ColorGlyphFont?
    private var atlasImage: BufferedImage? = BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB)
    private var texture: LuminTexture? = null
    private var nextCell = 0

    init {
        val fontPath = systemEmojiFontPath()
        emojiFont = loadFont(fontPath).deriveFont(Font.PLAIN, FONT_SIZE.toFloat())
        colorGlyphFont = ColorGlyphFont.load(fontPath)
    }

    fun get(emoji: String): EmojiGlyph {
        val cached = glyphs[emoji]
        if (cached != null) {
            return cached
        }

        if (nextCell >= CELLS_PER_ROW * CELLS_PER_ROW) {
            clearAtlas()
        }

        val cell = nextCell++
        val cellX = cell % CELLS_PER_ROW
        val cellY = cell / CELLS_PER_ROW
        val x = cellX * CELL_SIZE
        val y = cellY * CELL_SIZE
        drawEmojiToAtlas(emoji, x, y)
        uploadAtlas()

        val u0 = x / ATLAS_SIZE.toFloat()
        val v0 = y / ATLAS_SIZE.toFloat()
        val u1 = (x + CELL_SIZE) / ATLAS_SIZE.toFloat()
        val v1 = (y + CELL_SIZE) / ATLAS_SIZE.toFloat()
        val glyph = EmojiGlyph(texture!!, u0, v0, u1, v1)
        glyphs[emoji] = glyph
        return glyph
    }

    private fun clearAtlas() {
        glyphs.clear()
        nextCell = 0
        val graphics = atlasImage!!.createGraphics()
        try {
            graphics.composite = AlphaComposite.Clear
            graphics.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE)
        } finally {
            graphics.dispose()
        }
    }

    private fun drawEmojiToAtlas(emoji: String, x: Int, y: Int) {
        val graphics = atlasImage!!.createGraphics()
        try {
            graphics.composite = AlphaComposite.Clear
            graphics.fillRect(x, y, CELL_SIZE, CELL_SIZE)
            graphics.composite = AlphaComposite.SrcOver
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.font = emojiFont

            val context = graphics.fontRenderContext
            val glyphVector = emojiFont.createGlyphVector(context, emoji)
            val bounds = glyphVector.visualBounds
            val drawX = (x + (CELL_SIZE - bounds.width) / 2.0 - bounds.x).toFloat()
            val drawY = (y + (CELL_SIZE - bounds.height) / 2.0 - bounds.y).toFloat()
            val glyphId = if (glyphVector.numGlyphs > 0) glyphVector.getGlyphCode(0) and 0xFFFF else -1

            if (!drawColorGlyph(graphics, context, glyphId, drawX, drawY)) {
                graphics.color = Color.WHITE
                graphics.drawString(emoji, drawX, drawY)
            }
        } finally {
            graphics.dispose()
        }
    }

    private fun drawColorGlyph(graphics: Graphics2D, context: FontRenderContext, glyphId: Int, x: Float, y: Float): Boolean {
        if (colorGlyphFont == null || glyphId < 0) {
            return false
        }

        val layers = colorGlyphFont.layersFor(glyphId)
        if (layers == null || layers.isEmpty()) {
            return false
        }

        for (layer in layers) {
            val color = colorGlyphFont.color(layer.paletteIndex)
            if (color == null || color.alpha == 0) {
                continue
            }
            val vector = emojiFont.createGlyphVector(context, intArrayOf(layer.glyphId))
            graphics.color = color
            graphics.drawGlyphVector(vector, x, y)
        }
        return true
    }

    private fun uploadAtlas() {
        toNativeImage(atlasImage!!).use { image ->
            if (texture == null) {
                val device = RenderSystem.getDevice()
                val gpuTexture = device.createTexture(
                    "epsilon/system_emoji_atlas",
                    GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM,
                    ATLAS_SIZE,
                    ATLAS_SIZE,
                    1,
                    1
                )
                val view = device.createTextureView(gpuTexture)
                val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                texture = LuminTexture(gpuTexture, view, sampler, true, false)
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture!!.getTexture(), image)
        }
    }

    private fun systemEmojiFontPath(): Path {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        if (osName.contains("win")) {
            val windowsDir = System.getenv("WINDIR")
            return if (windowsDir == null) Path.of("C:\\Windows\\Fonts\\seguiemj.ttf") else Path.of(windowsDir, "Fonts", "seguiemj.ttf")
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Path.of("/System/Library/Fonts/Apple Color Emoji.ttc")
        }
        val noto = Path.of("/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf")
        return if (Files.isRegularFile(noto)) noto else Path.of("/usr/share/fonts/opentype/noto/NotoColorEmoji.ttf")
    }

    private fun loadFont(path: Path): Font {
        try {
            if (Files.isRegularFile(path)) {
                return Font.createFont(Font.TRUETYPE_FONT, path.toFile())
            }
        } catch (ignored: FontFormatException) {
        } catch (ignored: IOException) {
        }
        return Font(systemEmojiFamily(), Font.PLAIN, FONT_SIZE)
    }

    private fun systemEmojiFamily(): String {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        if (osName.contains("win")) {
            return "Segoe UI Emoji"
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "Apple Color Emoji"
        }
        return "Noto Color Emoji"
    }

    private fun toNativeImage(bufferedImage: BufferedImage): NativeImage {
        val image = NativeImage(bufferedImage.width, bufferedImage.height, true)
        for (y in 0 until bufferedImage.height) {
            for (x in 0 until bufferedImage.width) {
                image.setPixel(x, y, bufferedImage.getRGB(x, y))
            }
        }
        return image
    }

    override fun close() {
        glyphs.clear()
        atlasImage = null
        if (texture != null) {
            texture!!.close()
            texture = null
        }
    }

    data class EmojiGlyph(val texture: LuminTexture, val u0: Float, val v0: Float, val u1: Float, val v1: Float)

    private data class ColorGlyphLayer(val glyphId: Int, val paletteIndex: Int)

    private data class ColorGlyphFont(val layers: Map<Int, Array<ColorGlyphLayer>>, val colors: Array<Color>) {

        // 类内已有属性 layers，方法名加 For 后缀避免与属性同名。
        fun layersFor(glyphId: Int): Array<ColorGlyphLayer>? {
            return layers[glyphId]
        }

        fun color(paletteIndex: Int): Color? {
            if (paletteIndex == 0xFFFF || paletteIndex < 0 || paletteIndex >= colors.size) {
                return Color.WHITE
            }
            return colors[paletteIndex]
        }

        companion object {

            fun load(path: Path): ColorGlyphFont? {
                if (!Files.isRegularFile(path)) {
                    return null
                }
                try {
                    val data = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.BIG_ENDIAN)
                    val colr = table(data, "COLR")
                    val cpal = table(data, "CPAL")
                    if (colr == null || cpal == null) {
                        return null
                    }
                    val colors = readCpal(data, cpal)
                    val layers = readColr(data, colr)
                    return if (colors.isEmpty() || layers.isEmpty()) null else ColorGlyphFont(layers, colors)
                } catch (ignored: RuntimeException) {
                    return null
                } catch (ignored: IOException) {
                    return null
                }
            }

            private fun readColr(data: ByteBuffer, table: Table): Map<Int, Array<ColorGlyphLayer>> {
                val base = table.offset
                val numBaseGlyphRecords = u16(data, base + 2)
                val baseGlyphRecordsOffset = i32(data, base + 4)
                val layerRecordsOffset = i32(data, base + 8)
                val numLayerRecords = u16(data, base + 12)
                val result = LinkedHashMap<Int, Array<ColorGlyphLayer>>()
                for (i in 0 until numBaseGlyphRecords) {
                    val record = base + baseGlyphRecordsOffset + i * 6
                    val glyphId = u16(data, record)
                    val firstLayerIndex = u16(data, record + 2)
                    val numLayers = u16(data, record + 4)
                    if (firstLayerIndex + numLayers > numLayerRecords) {
                        continue
                    }
                    val layers = Array(numLayers) { ColorGlyphLayer(0, 0) }
                    for (layerIndex in 0 until numLayers) {
                        val layerRecord = base + layerRecordsOffset + (firstLayerIndex + layerIndex) * 4
                        layers[layerIndex] = ColorGlyphLayer(u16(data, layerRecord), u16(data, layerRecord + 2))
                    }
                    result[glyphId] = layers
                }
                return result
            }

            private fun readCpal(data: ByteBuffer, table: Table): Array<Color> {
                val base = table.offset
                val numPaletteEntries = u16(data, base + 2)
                val numColorRecords = u16(data, base + 6)
                val offsetFirstColorRecord = i32(data, base + 8)
                val firstPaletteIndex = u16(data, base + 12)
                val colorsToRead = minOf(numPaletteEntries, maxOf(0, numColorRecords - firstPaletteIndex))
                val colors = Array(colorsToRead) { Color.WHITE }
                for (i in colors.indices) {
                    val colorRecord = base + offsetFirstColorRecord + (firstPaletteIndex + i) * 4
                    val blue = u8(data, colorRecord)
                    val green = u8(data, colorRecord + 1)
                    val red = u8(data, colorRecord + 2)
                    val alpha = u8(data, colorRecord + 3)
                    colors[i] = Color(red, green, blue, alpha)
                }
                return colors
            }

            private fun table(data: ByteBuffer, tag: String): Table? {
                val numTables = u16(data, 4)
                for (i in 0 until numTables) {
                    val record = 12 + i * 16
                    if (tag == tag(data, record)) {
                        return Table(i32(data, record + 8), i32(data, record + 12))
                    }
                }
                return null
            }

            private fun tag(data: ByteBuffer, offset: Int): String {
                return String(
                    charArrayOf(
                        u8(data, offset).toChar(),
                        u8(data, offset + 1).toChar(),
                        u8(data, offset + 2).toChar(),
                        u8(data, offset + 3).toChar()
                    )
                )
            }

            private fun u8(data: ByteBuffer, offset: Int): Int {
                return data.get(offset).toInt() and 0xFF
            }

            private fun u16(data: ByteBuffer, offset: Int): Int {
                return data.getShort(offset).toInt() and 0xFFFF
            }

            private fun i32(data: ByteBuffer, offset: Int): Int {
                return data.getInt(offset)
            }
        }
    }

    private data class Table(val offset: Int, val length: Int)
}
