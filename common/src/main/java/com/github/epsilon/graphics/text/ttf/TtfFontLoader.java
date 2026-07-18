package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.IFontLoader;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTruetype;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfFontLoader implements IFontLoader {

    private static final int ASCII_LIMIT = 128;
    private static final int ADVANCE_UNSET = Integer.MIN_VALUE;
    private static final int DEFAULT_MAX_GLYPH_UPLOADS_PER_FRAME = 8;
    private static final AtomicInteger WORKER_ID = new AtomicInteger();
    private static final ExecutorService GLYPH_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Epsilon TTF Glyph Worker " + WORKER_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    public final TtfFontFile fontFile;
    private float renderScale = 1.0f;

    // ASCII 是 GUI/HUD 文本的主路径，用数组避免 Character 装箱和 HashMap 查找。
    private final GlyphDescriptor[] asciiGlyphMap = new GlyphDescriptor[ASCII_LIMIT];
    private final int[] asciiAdvanceMap = new int[ASCII_LIMIT];
    private final boolean[] asciiPendingGlyphs = new boolean[ASCII_LIMIT];
    private final HashMap<Integer, GlyphDescriptor> glyphMap = new HashMap<>();
    private final HashMap<Integer, Integer> advanceMap = new HashMap<>();
    private final HashMap<Integer, CompletableFuture<TtfGlyph>> pendingGlyphs = new HashMap<>();
    private final Set<Integer> deferredReadyGlyphs = new LinkedHashSet<>();
    private final List<TtfGlyphAtlas> atlases = new ArrayList<>();

    private TtfGlyphAtlas currentAtlas;
    private int atlasId = 0;
    // glyphRevision 驱动未完成布局重建，atlasRevision 驱动已释放 atlas 的布局失效。
    private long glyphRevision;
    private long atlasRevision;
    private static int maxGlyphUploadsPerFrame = DEFAULT_MAX_GLYPH_UPLOADS_PER_FRAME;
    private static long GLOBAL_RENDER_FRAME_ID;
    private static long uploadFrameId = Long.MIN_VALUE;
    private static int glyphUploadsThisFrame;

    public TtfFontLoader(Identifier ttfFile) {
        this.fontFile = new TtfFontFile(ttfFile, 48, 4);
        Arrays.fill(asciiAdvanceMap, ADVANCE_UNSET);
    }

    public TtfFontLoader(Path ttfFile) {
        this.fontFile = new TtfFontFile(ttfFile, 48, 4);
        Arrays.fill(asciiAdvanceMap, ADVANCE_UNSET);
    }

    public float getRenderScale() {
        return renderScale;
    }

    public void setRenderScale(float renderScale) {
        if (Float.isFinite(renderScale) && renderScale > 0.0f) {
            this.renderScale = renderScale;
        } else {
            this.renderScale = 1.0f;
        }
    }

    @Override
    public void checkAndLoadChar(char ch) {
        loadCharImmediately(ch);
    }

    public void loadCharImmediately(char ch) {
        loadCodepointImmediately(ch);
    }

    public void loadCodepointImmediately(int codepoint) {
        if (hasGlyph(codepoint)) return;

        CompletableFuture<TtfGlyph> pending = removePendingGlyph(codepoint);
        deferredReadyGlyphs.remove(codepoint);
        TtfGlyph glyph;
        try {
            glyph = pending != null ? pending.join() : fontFile.generateGlyph(codepoint);
        } catch (RuntimeException ignored) {
            glyph = fontFile.generateGlyph(codepoint);
        }
        appendGlyph(codepoint, glyph);
    }

    public int getAdvance(char ch) {
        return getAdvance((int) ch);
    }

    public int getAdvance(int codepoint) {
        GlyphDescriptor glyph = getGlyph(codepoint);
        if (glyph != null) {
            return glyph.advance();
        }

        if (isAscii(codepoint)) {
            int advance = asciiAdvanceMap[codepoint];
            if (advance != ADVANCE_UNSET) {
                return advance;
            }

            advance = fontFile.getAdvance(codepoint);
            asciiAdvanceMap[codepoint] = advance;
            return advance;
        }

        return advanceMap.computeIfAbsent(codepoint, fontFile::getAdvance);
    }

    public void requestChars(String chars) {
        drainReadyGlyphs();
        requestMissingChars(chars);
    }

    // addText 热路径先请求缺失字形，再统一上传已完成字形，避免一次调用里重复 drain。
    public void prepareChars(String chars) {
        requestMissingChars(chars);
        drainReadyGlyphs();
    }

    private void requestMissingChars(String chars) {

        for (int i = 0; i < chars.length(); ) {
            int codepoint = chars.codePointAt(i);
            i += Character.charCount(codepoint);
            if (codepoint == ' ' || codepoint == '\n' || hasGlyph(codepoint) || hasPendingGlyph(codepoint)) {
                continue;
            }

            putPendingGlyph(codepoint, CompletableFuture.supplyAsync(() -> fontFile.generateGlyph(codepoint), GLYPH_WORKER));
        }
    }

    public void drainReadyGlyphs() {
        beginUploadFrameIfNeeded();
        if (pendingGlyphs.isEmpty() && deferredReadyGlyphs.isEmpty()) {
            return;
        }

        List<Integer> newlyReadyChars = null;
        for (Map.Entry<Integer, CompletableFuture<TtfGlyph>> entry : pendingGlyphs.entrySet()) {
            if (entry.getValue().isDone()) {
                if (newlyReadyChars == null) {
                    newlyReadyChars = new ArrayList<>();
                }
                newlyReadyChars.add(entry.getKey());
            }
        }

        if (newlyReadyChars != null) {
            deferredReadyGlyphs.addAll(newlyReadyChars);
        }

        if (deferredReadyGlyphs.isEmpty()) {
            return;
        }

        Iterator<Integer> iterator = deferredReadyGlyphs.iterator();
        while (iterator.hasNext() && canUploadMoreGlyphsThisFrame()) {
            int codepoint = iterator.next();
            iterator.remove();
            CompletableFuture<TtfGlyph> future = removePendingGlyph(codepoint);
            if (future != null && !future.isCompletedExceptionally() && !future.isCancelled()) {
                appendGlyph(codepoint, future.join());
                glyphUploadsThisFrame++;
            }
        }
    }

    public static int getMaxGlyphUploadsPerFrame() {
        return maxGlyphUploadsPerFrame;
    }

    public static void setMaxGlyphUploadsPerFrame(int maxGlyphUploadsPerFrame) {
        TtfFontLoader.maxGlyphUploadsPerFrame = Math.max(1, maxGlyphUploadsPerFrame);
    }

    public static void beginRenderFrame() {
        GLOBAL_RENDER_FRAME_ID++;
    }

    public long getGlyphRevision() {
        return glyphRevision;
    }

    public long getAtlasRevision() {
        return atlasRevision;
    }

    private void beginUploadFrameIfNeeded() {
        long frameId = GLOBAL_RENDER_FRAME_ID;
        if (frameId == uploadFrameId) {
            return;
        }
        uploadFrameId = frameId;
        glyphUploadsThisFrame = 0;
    }

    private boolean canUploadMoreGlyphsThisFrame() {
        return glyphUploadsThisFrame < maxGlyphUploadsPerFrame;
    }

    private void appendGlyph(int codepoint, TtfGlyph glyph) {
        if (glyph == null || glyph.glyphData() == null) return;

        if (currentAtlas == null) {
            createNewAtlas();
        }

        TtfGlyphAtlas.GlyphUV uv = currentAtlas.appendGlyph(glyph);

        if (uv == null) {
            createNewAtlas();
            uv = currentAtlas.appendGlyph(glyph);
        }

        if (uv != null) {
            GlyphDescriptor descriptor = new GlyphDescriptor(
                    currentAtlas, uv,
                    glyph.width(), glyph.height(),
                    glyph.xOffset(), glyph.yOffset(),
                    glyph.advance()
            );
            putGlyph(codepoint, descriptor);
            glyphRevision++;
        }

        freeGlyph(glyph);
    }

    private void createNewAtlas() {
        currentAtlas = new TtfGlyphAtlas(atlasId);
        atlases.add(currentAtlas);
        atlasId++;
    }

    @Override
    public void checkAndLoadChars(String chars) {
        for (int i = 0; i < chars.length(); ) {
            int codepoint = chars.codePointAt(i);
            i += Character.charCount(codepoint);
            loadCodepointImmediately(codepoint);
        }
    }


    @Override
    public void destroy() {
        fontFile.destroy();
        for (TtfGlyphAtlas atlas : atlases) {
            atlas.destroy();
        }
        atlases.clear();
        glyphMap.clear();
        advanceMap.clear();
        Arrays.fill(asciiGlyphMap, null);
        Arrays.fill(asciiAdvanceMap, ADVANCE_UNSET);
        Arrays.fill(asciiPendingGlyphs, false);
        for (CompletableFuture<TtfGlyph> future : pendingGlyphs.values()) {
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                freeGlyph(future.join());
            } else {
                future.cancel(false);
            }
        }
        pendingGlyphs.clear();
        deferredReadyGlyphs.clear();
        glyphRevision++;
        // atlas 已被释放，任何缓存布局里保存的 atlas 引用都必须失效。
        atlasRevision++;
    }

    private void freeGlyph(TtfGlyph glyph) {
        if (glyph != null && glyph.glyphData() != null) {
            STBTruetype.stbtt_FreeSDF(glyph.glyphData());
        }
    }

    @Override
    public GlyphDescriptor getGlyph(char ch) {
        return getGlyph((int) ch);
    }

    public GlyphDescriptor getGlyph(int codepoint) {
        if (isAscii(codepoint)) {
            return asciiGlyphMap[codepoint];
        }
        return glyphMap.get(codepoint);
    }

    private void putGlyph(int codepoint, GlyphDescriptor glyph) {
        if (isAscii(codepoint)) {
            asciiGlyphMap[codepoint] = glyph;
        } else {
            glyphMap.put(codepoint, glyph);
        }
    }

    private boolean hasGlyph(int codepoint) {
        return getGlyph(codepoint) != null;
    }

    private void putPendingGlyph(int codepoint, CompletableFuture<TtfGlyph> future) {
        pendingGlyphs.put(codepoint, future);
        if (isAscii(codepoint)) {
            asciiPendingGlyphs[codepoint] = true;
        }
    }

    private CompletableFuture<TtfGlyph> removePendingGlyph(int codepoint) {
        CompletableFuture<TtfGlyph> future = pendingGlyphs.remove(codepoint);
        if (isAscii(codepoint)) {
            asciiPendingGlyphs[codepoint] = false;
        }
        return future;
    }

    private boolean hasPendingGlyph(int codepoint) {
        if (isAscii(codepoint)) {
            return asciiPendingGlyphs[codepoint];
        }
        return pendingGlyphs.containsKey(codepoint);
    }

    private static boolean isAscii(int codepoint) {
        return codepoint >= 0 && codepoint < ASCII_LIMIT;
    }
}
