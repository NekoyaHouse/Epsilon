package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.graphics.text.GlyphDescriptor
import com.github.epsilon.graphics.text.IFontLoader
import net.minecraft.resources.Identifier
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryUtil
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TtfFontLoader : IFontLoader {

    val fontFile: TtfFontFile

    private var renderScale = 1.0f

    // ASCII 是 GUI/HUD 文本的主路径，用数组避免 Character 装箱和 HashMap 查找。
    private val asciiGlyphMap = arrayOfNulls<GlyphDescriptor>(ASCII_LIMIT)
    private val asciiAdvanceMap = IntArray(ASCII_LIMIT)
    private val asciiPendingGlyphs = BooleanArray(ASCII_LIMIT)
    private val glyphMap = HashMap<Int, GlyphDescriptor>()
    private val advanceMap = HashMap<Int, Int>()
    private val pendingGlyphs = HashMap<Int, CompletableFuture<TtfGlyph>>()
    private val deferredReadyGlyphs = LinkedHashSet<Int>()
    private val atlases = ArrayList<TtfGlyphAtlas>()

    private var currentAtlas: TtfGlyphAtlas? = null
    private var atlasId = 0

    // 缺字占位字形（口字形方框）与 atlas 同生命周期：首次需要时生成并上传，之后所有缺字共用一个 atlas 单元。
    private var fallbackAtlas: TtfGlyphAtlas? = null
    private var fallbackUv: TtfGlyphAtlas.GlyphUV? = null
    private var fallbackWidth = 0
    private var fallbackHeight = 0
    private var fallbackYOffset = 0

    // glyphRevision 驱动未完成布局重建，atlasRevision 驱动已释放 atlas 的布局失效。
    private var glyphRevision = 0L
    private var atlasRevision = 0L

    constructor(ttfFile: Identifier) {
        this.fontFile = TtfFontFile(ttfFile, FONT_PIXEL_HEIGHT + SDF_PADDING * 2, SDF_PADDING)
        asciiAdvanceMap.fill(ADVANCE_UNSET)
    }

    constructor(ttfFile: Path) {
        this.fontFile = TtfFontFile(ttfFile, FONT_PIXEL_HEIGHT + SDF_PADDING * 2, SDF_PADDING)
        asciiAdvanceMap.fill(ADVANCE_UNSET)
    }

    fun getRenderScale(): Float {
        return renderScale
    }

    fun setRenderScale(renderScale: Float) {
        if (renderScale.isFinite() && renderScale > 0.0f) {
            this.renderScale = renderScale
        } else {
            this.renderScale = 1.0f
        }
    }

    override fun checkAndLoadChar(ch: Char) {
        loadCharImmediately(ch)
    }

    fun loadCharImmediately(ch: Char) {
        loadCodepointImmediately(ch.code)
    }

    fun loadCodepointImmediately(codepoint: Int) {
        if (hasGlyph(codepoint)) return

        val pending = removePendingGlyph(codepoint)
        deferredReadyGlyphs.remove(codepoint)
        var glyph: TtfGlyph? = null
        try {
            glyph = if (pending != null) pending.join() else fontFile.generateGlyph(codepoint)
        } catch (ignored: RuntimeException) {
            glyph = fontFile.generateGlyph(codepoint)
        }
        appendGlyph(codepoint, glyph)
    }

    fun getAdvance(ch: Char): Int {
        return getAdvance(ch.code)
    }

    fun getAdvance(codepoint: Int): Int {
        val glyph = getGlyph(codepoint)
        if (glyph != null) {
            return glyph.advance
        }

        if (isAscii(codepoint)) {
            var advance = asciiAdvanceMap[codepoint]
            if (advance != ADVANCE_UNSET) {
                return advance
            }

            advance = fontFile.getAdvance(codepoint)
            asciiAdvanceMap[codepoint] = advance
            return advance
        }

        val cached = advanceMap[codepoint]
        if (cached != null) {
            return cached
        }
        val advance = fontFile.getAdvance(codepoint)
        advanceMap[codepoint] = advance
        return advance
    }

    fun requestChars(chars: String) {
        drainReadyGlyphs()
        requestMissingChars(chars)
    }

    // addText 热路径先请求缺失字形，再统一上传已完成字形，避免一次调用里重复 drain。
    fun prepareChars(chars: String) {
        requestMissingChars(chars)
        drainReadyGlyphs()
    }

    private fun requestMissingChars(chars: String) {
        var i = 0
        while (i < chars.length) {
            val codepoint = Character.codePointAt(chars, i)
            i += Character.charCount(codepoint)
            if (codepoint == ' '.code || codepoint == '\n'.code || hasGlyph(codepoint) || hasPendingGlyph(codepoint)) {
                continue
            }

            putPendingGlyph(
                codepoint,
                CompletableFuture.supplyAsync({ fontFile.generateGlyph(codepoint) }, GLYPH_WORKER)
            )
        }
    }

    fun drainReadyGlyphs() {
        beginUploadFrameIfNeeded()
        if (pendingGlyphs.isEmpty() && deferredReadyGlyphs.isEmpty()) {
            return
        }

        var newlyReadyChars: MutableList<Int>? = null
        for (entry in pendingGlyphs.entries) {
            if (entry.value.isDone) {
                if (newlyReadyChars == null) {
                    newlyReadyChars = ArrayList()
                }
                newlyReadyChars.add(entry.key)
            }
        }

        if (newlyReadyChars != null) {
            deferredReadyGlyphs.addAll(newlyReadyChars)
        }

        if (deferredReadyGlyphs.isEmpty()) {
            return
        }

        val iterator = deferredReadyGlyphs.iterator()
        while (iterator.hasNext() && canUploadMoreGlyphsThisFrame()) {
            val codepoint = iterator.next()
            iterator.remove()
            val future = removePendingGlyph(codepoint)
            if (future != null && !future.isCompletedExceptionally && !future.isCancelled) {
                appendGlyph(codepoint, future.join())
                glyphUploadsThisFrame++
            }
        }
    }

    fun getGlyphRevision(): Long {
        return glyphRevision
    }

    fun getAtlasRevision(): Long {
        return atlasRevision
    }

    private fun beginUploadFrameIfNeeded() {
        val frameId = GLOBAL_RENDER_FRAME_ID
        if (frameId == uploadFrameId) {
            return
        }
        uploadFrameId = frameId
        glyphUploadsThisFrame = 0
    }

    private fun canUploadMoreGlyphsThisFrame(): Boolean {
        return glyphUploadsThisFrame < maxGlyphUploadsPerFrame
    }

    private fun appendGlyph(codepoint: Int, glyph: TtfGlyph?) {
        if (glyph == null) return
        if (glyph.glyphData == null || glyph.alphaData == null) {
            freeGlyph(glyph)
            return
        }

        val uv = appendToAtlas(glyph)
        if (uv != null) {
            val descriptor = GlyphDescriptor(
                currentAtlas!!, uv,
                glyph.width, glyph.height,
                glyph.xOffset, glyph.yOffset,
                glyph.advance
            )
            putGlyph(codepoint, descriptor)
            glyphRevision++
        }

        freeGlyph(glyph)
    }

    /**
     * 返回缺字占位字形：字形尚未上传或字体根本没有该字形时使用。
     *
     * 占位框尺寸固定，advance 取目标字符自身的步进宽度，与 [getAdvance]、文本测量保持一致，
     * 因此真实字形上传后布局不会跳动。首次调用会按字体 metrics 生成位图并写入 atlas，必须在渲染线程调用；
     * 空白字符没有墨迹，调用方应自行跳过。
     */
    fun getFallbackGlyph(codepoint: Int): GlyphDescriptor? {
        if (!ensureFallbackGlyph()) {
            return null
        }

        val advance = getAdvance(codepoint)
        return GlyphDescriptor(
            fallbackAtlas!!, fallbackUv!!, fallbackWidth, fallbackHeight,
            (advance - fallbackWidth).floorDiv(2), fallbackYOffset, advance
        )
    }

    private fun ensureFallbackGlyph(): Boolean {
        if (fallbackAtlas != null) {
            return true
        }

        val glyph = fontFile.generateFallbackGlyph()
        try {
            val uv = appendToAtlas(glyph)
            if (uv == null) {
                return false
            }

            fallbackAtlas = currentAtlas
            fallbackUv = uv
            fallbackWidth = glyph.width
            fallbackHeight = glyph.height
            fallbackYOffset = glyph.yOffset
            return true
        } finally {
            // 占位字形由 MemoryUtil 分配，不是 stb 内存，不能交给 stbtt_FreeSDF 释放。
            MemoryUtil.memFree(glyph.glyphData)
            MemoryUtil.memFree(glyph.alphaData)
        }
    }

    /**
     * 写入当前 atlas，写不下时新开一张重试；返回 null 表示两张都写不下。
     */
    private fun appendToAtlas(glyph: TtfGlyph): TtfGlyphAtlas.GlyphUV? {
        if (currentAtlas == null) {
            createNewAtlas()
        }

        var uv = currentAtlas!!.appendGlyph(glyph)
        if (uv == null) {
            createNewAtlas()
            uv = currentAtlas!!.appendGlyph(glyph)
        }
        return uv
    }

    private fun createNewAtlas() {
        val atlas = TtfGlyphAtlas(atlasId)
        currentAtlas = atlas
        atlases.add(atlas)
        atlasId++
    }

    override fun checkAndLoadChars(chars: String) {
        var i = 0
        while (i < chars.length) {
            val codepoint = Character.codePointAt(chars, i)
            i += Character.charCount(codepoint)
            loadCodepointImmediately(codepoint)
        }
    }

    override fun destroy() {
        fontFile.destroy()
        for (atlas in atlases) {
            atlas.destroy()
        }
        atlases.clear()
        glyphMap.clear()
        advanceMap.clear()
        asciiGlyphMap.fill(null)
        asciiAdvanceMap.fill(ADVANCE_UNSET)
        asciiPendingGlyphs.fill(false)
        fallbackAtlas = null
        fallbackUv = null
        fallbackWidth = 0
        fallbackHeight = 0
        fallbackYOffset = 0
        for (future in pendingGlyphs.values) {
            if (future.isDone && !future.isCompletedExceptionally && !future.isCancelled) {
                freeGlyph(future.join())
            } else {
                future.cancel(false)
            }
        }
        pendingGlyphs.clear()
        deferredReadyGlyphs.clear()
        glyphRevision++
        // atlas 已被释放，任何缓存布局里保存的 atlas 引用都必须失效。
        atlasRevision++
    }

    private fun freeGlyph(glyph: TtfGlyph?) {
        if (glyph != null && glyph.glyphData != null) {
            STBTruetype.stbtt_FreeSDF(glyph.glyphData)
        }
        if (glyph != null && glyph.alphaData != null) {
            MemoryUtil.memFree(glyph.alphaData)
        }
    }

    override fun getGlyph(ch: Char): GlyphDescriptor? {
        return getGlyph(ch.code)
    }

    fun getGlyph(codepoint: Int): GlyphDescriptor? {
        if (isAscii(codepoint)) {
            return asciiGlyphMap[codepoint]
        }
        return glyphMap[codepoint]
    }

    private fun putGlyph(codepoint: Int, glyph: GlyphDescriptor) {
        if (isAscii(codepoint)) {
            asciiGlyphMap[codepoint] = glyph
        } else {
            glyphMap[codepoint] = glyph
        }
    }

    private fun hasGlyph(codepoint: Int): Boolean {
        return getGlyph(codepoint) != null
    }

    private fun putPendingGlyph(codepoint: Int, future: CompletableFuture<TtfGlyph>) {
        pendingGlyphs[codepoint] = future
        if (isAscii(codepoint)) {
            asciiPendingGlyphs[codepoint] = true
        }
    }

    private fun removePendingGlyph(codepoint: Int): CompletableFuture<TtfGlyph>? {
        val future = pendingGlyphs.remove(codepoint)
        if (isAscii(codepoint)) {
            asciiPendingGlyphs[codepoint] = false
        }
        return future
    }

    private fun hasPendingGlyph(codepoint: Int): Boolean {
        if (isAscii(codepoint)) {
            return asciiPendingGlyphs[codepoint]
        }
        return pendingGlyphs.containsKey(codepoint)
    }

    companion object {

        private const val ASCII_LIMIT = 128
        private const val FONT_PIXEL_HEIGHT = 40
        private const val SDF_PADDING = 16
        private const val ADVANCE_UNSET = Int.MIN_VALUE
        private const val DEFAULT_MAX_GLYPH_UPLOADS_PER_FRAME = 8
        private val WORKER_ID = AtomicInteger()
        private val GLYPH_WORKER: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            val thread = Thread(runnable, "Epsilon TTF Glyph Worker " + WORKER_ID.incrementAndGet())
            thread.isDaemon = true
            thread
        }

        private var maxGlyphUploadsPerFrame = DEFAULT_MAX_GLYPH_UPLOADS_PER_FRAME
        private var GLOBAL_RENDER_FRAME_ID = 0L
        private var uploadFrameId = Long.MIN_VALUE
        private var glyphUploadsThisFrame = 0

        fun getMaxGlyphUploadsPerFrame(): Int {
            return maxGlyphUploadsPerFrame
        }

        fun setMaxGlyphUploadsPerFrame(maxGlyphUploadsPerFrame: Int) {
            TtfFontLoader.maxGlyphUploadsPerFrame = maxOf(1, maxGlyphUploadsPerFrame)
        }

        fun beginRenderFrame() {
            GLOBAL_RENDER_FRAME_ID++
        }

        private fun isAscii(codepoint: Int): Boolean {
            return codepoint >= 0 && codepoint < ASCII_LIMIT
        }
    }
}
