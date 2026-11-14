package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.Constants
import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.buffer.GeometryArena
import com.github.epsilon.graphics.renderers.*
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.managers.RendererManager
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import java.lang.Float as JavaFloat

/** 持久 display list 与当帧 canvas 共用执行后端；flush 消费尚未提交的记录。 */
class Render2DScheduler : IRenderer {
    val stats = Render2DStats()
    private val arena = GeometryArena(stats)
    private val executor = Render2DExecutor(arena, stats)
    var measureGpu: Boolean
        get() = executor.measureGpu
        set(value) {
            executor.measureGpu = value
        }
    private val layerHandles = HashMap<Int, Canvas>()
    private val measureTextRenderer = TextRenderer.create(256 * 1024L)
    private var itemAtlas: CanvasItemAtlas? = null
    private val pending = OrderedSubmissionQueue<Queued>({ it.layer }, { it.sequence })
    private val transient = ArrayList<Render2DCommand>()
    private val retained = LinkedHashMap<RecordKey, DisplayList>(64, 0.75f, true)
    private var capture: MutableList<Render2DCommand>? = null
    private var captureX = 0f
    private var captureY = 0f
    private var sequence = 0L
    private var closed = false
    private val deferred = ArrayList<DisplayList>()
    private var frame = Long.MIN_VALUE

    constructor() {
        RendererManager.unregister(measureTextRenderer); RendererManager.register(this)
    }

    @Deprecated("空间桶已由有序 display list 替代")
    constructor(quadtreeThreshold: Int) : this() {
        require(quadtreeThreshold > 0)
    }

    fun canvas(layer: Int = 0): Canvas = layerHandles.getOrPut(layer) { Canvas(this, layer) }
    fun textMetrics(): TextRenderer = measureTextRenderer
    fun isEmpty() = pending.isEmpty() && transient.isEmpty()
    override fun draw() = flush()

    fun beginFrame() {
        check(!closed)
        val next = LuminRenderSystem.getRenderFrameId()
        if (frame == next) return
        frame = next
        arena.seal()
        arena.collect()
        val iterator = retained.values.iterator()
        while (iterator.hasNext()) {
            val list = iterator.next()
            if (list.lastFrame < next - 1) {
                retireList(list); iterator.remove()
            }
        }
        arena.seal()
    }

    fun endFrame() {
        val iterator = retained.values.iterator()
        while (iterator.hasNext()) {
            val list = iterator.next()
            if (list.lastFrame != frame) {
                retireList(list); iterator.remove()
            }
        }
        releaseDeferred()
        arena.seal()
        arena.collect()
    }

    /** 清除当帧记录，不销毁跨帧几何。 */
    override fun clear() {
        pending.filter { it.batch.temporary }.forEach { it.batch.renderers.close() }
        pending.clear()
        releaseDeferred()
        transient.clear()
        layerHandles.values.forEach { it.clearScissor() }
        sequence = 0
    }

    fun clearLayer(layer: Int) {
        pending.filter { it.layer == layer && it.batch.temporary }.forEach { it.batch.renderers.close() }
        pending.removeAll { it.layer == layer }
        releaseDeferred()
        transient.removeAll { it.layer == layer }
        layerHandles[layer]?.clearScissor()
    }

    fun resetCache() {
        retained.values.forEach { retireList(it) }
        retained.clear()
        executor.resetCache()
        arena.seal()
    }

    /** identity 使用引用身份；几何与外部属性分别缓存。 */
    fun retain(
        identity: Any, part: Int, layer: Int, properties: Render2DProperties,
        clip: Render2DScissor?, originX: Float = 0f, originY: Float = 0f, record: () -> Unit
    ) {
        beginFrame()
        drainTransient()
        val key = RecordKey(identity, part, layer)
        var list = retained[key]
        if (list == null || !list.resourcesCurrent()) {
            val commands = ArrayList<Render2DCommand>()
            val previous = capture
            val oldX = captureX;
            val oldY = captureY
            captureX = originX; captureY = originY
            capture = commands
            val start = System.nanoTime()
            try {
                record()
            } finally {
                capture = previous; captureX = oldX; captureY = oldY; stats.recordingNanos += System.nanoTime() - start
            }
            stats.recordings++
            val compiled = compile(commands)
            val replacement = DisplayList(compiled, resources(commands), frame)
            retained.put(key, replacement)?.let { retireList(it) }
            list = replacement
        } else stats.cacheHits++
        list.lastFrame = frame
        for (batch in list.batches) {
            val bounds = batch.bounds
            // 物品可能超出 16x16 槽位，实际模型边界在提交前解析，不能按槽位提前剔除。
            if (clip != null && batch.items.isEmpty()) {
                val transform = properties.transform
                val xs = floatArrayOf(
                    transform.mapX(bounds.x, bounds.y),
                    transform.mapX(bounds.x + bounds.width, bounds.y),
                    transform.mapX(bounds.x, bounds.y + bounds.height),
                    transform.mapX(bounds.x + bounds.width, bounds.y + bounds.height)
                )
                val ys = floatArrayOf(
                    transform.mapY(bounds.x, bounds.y),
                    transform.mapY(bounds.x + bounds.width, bounds.y),
                    transform.mapY(bounds.x, bounds.y + bounds.height),
                    transform.mapY(bounds.x + bounds.width, bounds.y + bounds.height)
                )
                val area =
                    LuminRenderSystem.toFramebufferScissor(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
                if (area.x + area.width <= clip.x || area.y + area.height <= clip.y ||
                    area.x >= clip.x + clip.width || area.y >= clip.y + clip.height
                ) continue
            }
            pending.add(Queued(layer, nextSequence(), batch, properties, clip))
        }
        // 活跃的大页面可超过条目数预算；只淘汰未参与当前提交的缓存。
        val iterator = retained.values.iterator()
        while ((retained.size > 2048 || stats.liveBytes > 32L * 1024 * 1024) && iterator.hasNext()) {
            val candidate = iterator.next()
            if (candidate.lastFrame != frame) {
                retireList(candidate); iterator.remove()
            }
        }
        arena.seal()
    }

    fun recordTransient(layer: Int, properties: Render2DProperties, clip: Render2DScissor?, record: () -> Unit) {
        beginFrame()
        drainTransient()
        val commands = ArrayList<Render2DCommand>()
        val previous = capture
        capture = commands
        try {
            record()
        } finally {
            capture = previous
        }
        for (batch in compile(commands, temporary = true)) pending.add(
            Queued(
                layer,
                nextSequence(),
                batch,
                properties,
                clip
            )
        )
    }

    fun flush() = flushSelected(null)
    fun flushLayer(layer: Int) = flushSelected(layer)
    fun flushAndClear() {
        flush(); clear()
    }

    private fun flushSelected(layer: Int?) {
        beginFrame()
        drainTransient()
        val selected = pending.consume(layer)
        if (selected.isEmpty()) return
        val start = System.nanoTime()
        val dynamic = ArrayList<CompiledBatch>()
        try {
            val items = selected.flatMap { it.batch.items }
            val itemCommands = if (items.isEmpty()) emptyMap() else {
                val atlas = itemAtlas ?: CanvasItemAtlas(measureTextRenderer).also { itemAtlas = it }
                atlas.prepare(items)
            }
            val expanded = selected.flatMap { queued ->
                if (queued.batch.items.isEmpty()) listOf(queued)
                else {
                    val commands = queued.batch.items.flatMap { itemCommands.getValue(it) }
                    compile(commands, temporary = true).map { batch ->
                        dynamic.add(batch)
                        queued.copy(batch = batch)
                    }
                }
            }
            executor.execute(expanded.map { queued ->
                val clip = intersect(queued.batch.scissor, queued.clip)
                Render2DExecutor.Draw(
                    pipelineFor(queued.batch.kind), queued.properties,
                    { queued.batch.renderers.applyScissor(clip); queued.batch.renderers.prepareSharedDraw() },
                    { pass -> queued.batch.renderers.applyScissor(clip); queued.batch.renderers.draw(pass) })
            })
        } finally {
            dynamic.forEach { it.renderers.close() }
            selected.filter { it.batch.temporary }.forEach { it.batch.renderers.close() }
            releaseDeferred()
            arena.seal()
            stats.submissionNanos += System.nanoTime() - start
        }
    }

    private fun retireList(list: DisplayList) {
        if (pending.any { queued -> list.batches.any { it === queued.batch } }) deferred.add(list) else list.close()
    }

    private fun releaseDeferred() {
        val iterator = deferred.iterator()
        while (iterator.hasNext()) {
            val list = iterator.next()
            if (pending.none { queued -> list.batches.any { it === queued.batch } }) {
                list.close(); iterator.remove()
            }
        }
    }

    private fun add(command: Render2DCommand) {
        if (capture == null) transient.add(command)
        else capture!!.add(command.local(captureX, captureY))
    }

    private fun Render2DCommand.local(ox: Float, oy: Float): Render2DCommand = when (this) {
        is Render2DCommand.Item -> copy(bounds = bounds.copy(x = bounds.x - ox, y = bounds.y - oy), scissor = null)
        is Render2DCommand.Shadow -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null
        )

        is Render2DCommand.SegmentedShadow -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ),
            scissor = null,
            segmentRects = segmentRects.copyOf().also { values ->
                for (i in 0 until minOf(segmentCount, values.size / 4)) {
                    values[i * 4] -= ox; values[i * 4 + 1] -= oy
                }
            })

        is Render2DCommand.RoundRect -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null
        )

        is Render2DCommand.RoundRectOutline -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null
        )

        is Render2DCommand.Rect -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null
        )

        is Render2DCommand.Triangle -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, centerX = centerX - ox, centerY = centerY - oy
        )

        is Render2DCommand.Arc -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, centerX = centerX - ox, centerY = centerY - oy
        )

        is Render2DCommand.Texture -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, originX = originX - ox, originY = originY - oy
        )

        is Render2DCommand.Text -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, x = x - ox, y = y - oy, originX = originX - ox, originY = originY - oy
        )

        is Render2DCommand.GradientText -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, x = x - ox, y = y - oy
        )

        is Render2DCommand.BlurText -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, x = x - ox, y = y - oy
        )

        is Render2DCommand.GlitchText -> copy(
            bounds = Render2DBounds.of(
                bounds.x - ox,
                bounds.y - oy,
                bounds.width,
                bounds.height
            ), scissor = null, x = x - ox, y = y - oy
        )
    }

    private fun nextSequence(): Long = sequence++
    private fun drainTransient() {
        if (transient.isEmpty()) return
        val commands = transient.toList()
        transient.clear()
        for ((layer, values) in commands.groupBy { it.layer }) {
            val compiled = compile(values, temporary = true)
            for (batch in compiled) pending.add(Queued(layer, batch.sequence, batch, Render2DProperties.DEFAULT, null))
        }
    }

    private fun compile(commands: List<Render2DCommand>, temporary: Boolean = false): List<CompiledBatch> {
        val start = System.nanoTime()
        val groups = AdjacentBatchPlanner.plan(commands)
        stats.planBuilds++
        stats.planningNanos += System.nanoTime() - start
        val result = ArrayList<CompiledBatch>()
        try {
            for (group in groups) {
                val first = group.commands.first()
                val bundle = arena.recording { RendererBundle.create(first.kind()) }
                try {
                    val buildStart = System.nanoTime()
                    arena.recording { group.commands.forEach { emit(it, bundle) } }
                    stats.geometryBuilds++
                    stats.geometryNanos += System.nanoTime() - buildStart
                    val bounds = group.commands.map { it.orderingBounds() }.reduce { a, b -> a.include(b) }
                    result.add(CompiledBatch(
                        first.kind(), first.scissor, first.sequence, bundle, temporary, bounds,
                        group.commands.filterIsInstance<Render2DCommand.Item>()
                    ))
                } catch (failure: Throwable) {
                    bundle.close(); throw failure
                }
            }
            return result
        } catch (failure: Throwable) {
            result.forEach { it.renderers.close() }
            Constants.LOGGER.error("Lumin 2D 编译失败，保留已提交的 display list", failure)
            throw failure
        }
    }

    private fun resources(commands: List<Render2DCommand>): List<FontStamp> = commands.mapNotNull { command ->
        val text: String
        val explicit: TtfFontLoader?
        when (command) {
            is Render2DCommand.Text -> {
                text = command.text; explicit = command.fontLoader
            }

            is Render2DCommand.GradientText -> {
                text = command.text; explicit = command.fontLoader
            }

            is Render2DCommand.BlurText -> {
                text = command.text; explicit = command.fontLoader
            }

            is Render2DCommand.GlitchText -> {
                text = command.text; explicit = command.fontLoader
            }

            else -> return@mapNotNull null
        }
        val loader = explicit ?: StaticFontLoader.defaultFont()
        FontStamp(
            loader,
            text,
            explicit == null,
            loader.getGlyphRevision(),
            loader.getAtlasRevision(),
            loader.getRenderScale()
        )
    }

    private data class FontStamp(
        val loader: TtfFontLoader, val text: String, val default: Boolean,
        val glyph: Long, val atlas: Long, val scale: Float
    ) {
        fun current(): Boolean {
            if (default && loader !== StaticFontLoader.defaultFont()) return false
            loader.prepareChars(text)
            return glyph == loader.getGlyphRevision() && atlas == loader.getAtlasRevision() && scale == loader.getRenderScale()
        }
    }

    private class RecordKey(val identity: Any, val part: Int, val layer: Int) {
        override fun hashCode() = (System.identityHashCode(identity) * 31 + part) * 31 + layer
        override fun equals(other: Any?) =
            other is RecordKey && identity === other.identity && part == other.part && layer == other.layer
    }

    private class DisplayList(val batches: List<CompiledBatch>, val fonts: List<FontStamp>, var lastFrame: Long) :
        AutoCloseable {
        private val resources = Render2DResources.generation
        fun resourcesCurrent() = resources == Render2DResources.generation && fonts.all { it.current() }
        override fun close() {
            batches.forEach { it.renderers.close() }
        }
    }

    private data class CompiledBatch(
        val kind: Render2DCommandKind, val scissor: Render2DScissor?, val sequence: Long,
        val renderers: RendererBundle, val temporary: Boolean, val bounds: Render2DBounds,
        val items: List<Render2DCommand.Item> = emptyList(),
    )

    private data class Queued(
        val layer: Int, val sequence: Long, val batch: CompiledBatch,
        val properties: Render2DProperties, val clip: Render2DScissor?
    )

    override fun close() {
        if (closed) return
        clear()
        resetCache()
        itemAtlas?.close()
        itemAtlas = null
        measureTextRenderer.close()
        executor.close()
        arena.close()
        layerHandles.clear()
        RendererManager.unregister(this)
        closed = true
    }

    private fun pipelineFor(kind: Render2DCommandKind): RenderPipeline = when (kind) {
        Render2DCommandKind.SHADOW -> LuminRenderPipelines.SHADOW
        Render2DCommandKind.ROUND_RECT -> LuminRenderPipelines.UI_SHAPE
        Render2DCommandKind.ROUND_RECT_OUTLINE -> LuminRenderPipelines.UI_SHAPE
        Render2DCommandKind.RECT -> LuminRenderPipelines.UI_SHAPE
        Render2DCommandKind.TRIANGLE -> LuminRenderPipelines.UI_SHAPE
        Render2DCommandKind.ARC -> LuminRenderPipelines.ARC
        Render2DCommandKind.TEXTURE -> LuminRenderPipelines.TEXTURE
        Render2DCommandKind.PREMULTIPLIED_TEXTURE -> LuminRenderPipelines.ITEM_TEXTURE
        Render2DCommandKind.ITEM -> error("Item commands must be resolved before drawing")
        Render2DCommandKind.BLUR_TEXT -> LuminRenderPipelines.TTF_FONT_BLUR
        Render2DCommandKind.GLITCH_TEXT -> LuminRenderPipelines.TTF_FONT_GLITCH
        Render2DCommandKind.TEXT -> LuminRenderPipelines.TTF_FONT
    }

    private fun emit(command: Render2DCommand, renderers: RendererBundle) {
        when (command) {
            is Render2DCommand.Item -> Unit // 模型及动画在 flush 时准备，不写入静态 display list。
            is Render2DCommand.Shadow -> renderers.shadowRenderer().addShadow(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                command.blurRadius, command.color
            )

            is Render2DCommand.SegmentedShadow -> renderers.shadowRenderer().addShadow(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                command.blurRadius, command.color,
                command.segmentRects, command.segmentRadii, command.segmentColors, command.segmentCount
            )

            is Render2DCommand.RoundRect -> renderers.shapeRenderer().addOutlineGradient(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                -1f, command.topLeft, command.bottomLeft, command.bottomRight, command.topRight
            )

            is Render2DCommand.RoundRectOutline -> renderers.shapeRenderer().addOutline(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                command.outlineWidth, command.color
            )

            is Render2DCommand.Rect -> renderers.shapeRenderer().addOutlineGradient(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                0f, 0f, 0f, 0f, -1f, command.topLeft, command.bottomLeft, command.bottomRight, command.topRight
            )

            is Render2DCommand.Triangle -> renderers.shapeRenderer().addChevronTriangle(
                command.centerX, command.centerY, command.size, command.progress, command.color
            )

            is Render2DCommand.Arc -> renderers.arcRenderer().addGradientArc(
                command.centerX, command.centerY, command.radius, command.strokeWidth,
                command.startDegrees, command.sweepDegrees, command.roundCap, command.gradientRotationDegrees,
                command.startColor, command.middleColor, command.endColor
            )

            is Render2DCommand.Texture -> {
                val texture = command.texture
                if (texture is Render2DTexture.IdentifierRef) {
                    if (command.rotationDegrees == 0.0f) {
                        renderers.textureRenderer().addRoundedTexture(
                            texture.identifier,
                            command.bounds.x,
                            command.bounds.y,
                            command.bounds.width,
                            command.bounds.height,
                            command.radiusTopLeft,
                            command.radiusTopRight,
                            command.radiusBottomRight,
                            command.radiusBottomLeft,
                            command.u0,
                            command.v0,
                            command.u1,
                            command.v1,
                            command.color,
                            texture.linearFilter
                        )
                    } else {
                        renderers.textureRenderer().addRotatedRoundedTexture(
                            texture.identifier,
                            command.bounds.x,
                            command.bounds.y,
                            command.bounds.width,
                            command.bounds.height,
                            command.radiusTopLeft,
                            command.radiusTopRight,
                            command.radiusBottomRight,
                            command.radiusBottomLeft,
                            command.u0,
                            command.v0,
                            command.u1,
                            command.v1,
                            command.color,
                            command.originX,
                            command.originY,
                            command.rotationDegrees,
                            texture.linearFilter
                        )
                    }
                } else if (texture is Render2DTexture.LuminRef) {
                    if (command.rotationDegrees == 0.0f) {
                        renderers.textureRenderer().addRoundedTexture(
                            texture.texture,
                            command.bounds.x,
                            command.bounds.y,
                            command.bounds.width,
                            command.bounds.height,
                            command.radiusTopLeft,
                            command.radiusTopRight,
                            command.radiusBottomRight,
                            command.radiusBottomLeft,
                            command.u0,
                            command.v0,
                            command.u1,
                            command.v1,
                            command.color
                        )
                    } else {
                        renderers.textureRenderer().addRotatedRoundedTexture(
                            texture.texture,
                            command.bounds.x,
                            command.bounds.y,
                            command.bounds.width,
                            command.bounds.height,
                            command.radiusTopLeft,
                            command.radiusTopRight,
                            command.radiusBottomRight,
                            command.radiusBottomLeft,
                            command.u0,
                            command.v0,
                            command.u1,
                            command.v1,
                            command.color,
                            command.originX,
                            command.originY,
                            command.rotationDegrees
                        )
                    }
                }
            }

            is Render2DCommand.Text -> {
                val fontLoader = command.fontLoader
                if (fontLoader != null) {
                    if (command.rotationDegrees == 0.0f) {
                        renderers.textRenderer().addText(
                            command.text, command.x, command.y, command.scale, command.color, fontLoader
                        )
                    } else {
                        renderers.textRenderer().addRotatedText(
                            command.text, command.x, command.y, command.scale, command.color, fontLoader,
                            command.originX, command.originY, command.rotationDegrees
                        )
                    }
                } else {
                    if (command.rotationDegrees == 0.0f) {
                        renderers.textRenderer().addText(
                            command.text, command.x, command.y, command.scale, command.color
                        )
                    } else {
                        renderers.textRenderer().addRotatedText(
                            command.text, command.x, command.y, command.scale, command.color,
                            command.originX, command.originY, command.rotationDegrees
                        )
                    }
                }
            }

            is Render2DCommand.GradientText -> {
                val fontLoader = command.fontLoader
                if (fontLoader != null) {
                    renderers.textRenderer().addGradientText(
                        command.text, command.x, command.y, command.scale,
                        command.startColor, command.endColor, fontLoader
                    )
                } else {
                    renderers.textRenderer().addGradientText(
                        command.text, command.x, command.y, command.scale,
                        command.startColor, command.endColor
                    )
                }
            }

            is Render2DCommand.BlurText -> {
                val fontLoader = command.fontLoader
                if (fontLoader != null) {
                    renderers.textRenderer().addBlurredText(
                        command.text, command.x, command.y, command.scale, command.color,
                        command.blurRadius, command.intensity, fontLoader
                    )
                } else {
                    renderers.textRenderer().addBlurredText(
                        command.text, command.x, command.y, command.scale, command.color,
                        command.blurRadius, command.intensity
                    )
                }
            }

            is Render2DCommand.GlitchText -> {
                val fontLoader = command.fontLoader
                if (fontLoader != null) {
                    renderers.textRenderer().addGlitchText(
                        command.text, command.x, command.y, command.scale,
                        command.color, command.effect, fontLoader
                    )
                } else {
                    renderers.textRenderer().addGlitchText(
                        command.text, command.x, command.y, command.scale,
                        command.color, command.effect
                    )
                }
            }
        }
    }

    class Canvas internal constructor(
        private val scheduler: Render2DScheduler,
        private val layerIndex: Int,
    ) {
        private var currentScissor: Render2DScissor? = null

        fun layer(): Int = layerIndex

        fun scissor(): Render2DScissor? = currentScissor

        fun setScissor(x: Int, y: Int, width: Int, height: Int) {
            // 裁剪状态跟随 layer handle 被采样进命令，后续命令会带着当时的 scissor 进入批次规划。
            currentScissor = Render2DScissor(x, y, width, height)
        }

        fun clearScissor() {
            currentScissor = null
        }

        /** 物品是 16x16 GUI 模型；size 使用 Canvas 坐标，提交时采样原版模型与动画。 */
        fun addItem(
            stack: ItemStack, x: Float, y: Float, size: Float = 16f, color: Color = Color.WHITE,
            owner: LivingEntity? = null, seed: Int = 0, decorations: Boolean = false, countText: String? = null,
        ) {
            if (stack.isEmpty || !size.isFinite() || size <= 0f || color.alpha == 0) return
            scheduler.add(Render2DCommand.Item(
                layerIndex, scheduler.nextSequence(), Render2DBounds.of(x, y, size, size), currentScissor,
                stack.copy(), color, owner, seed, decorations, countText
            ))
        }

        /** 方块的背包模型；无物品形态的方块不绘制。 */
        fun addBlock(block: Block, x: Float, y: Float, size: Float = 16f, color: Color = Color.WHITE) {
            addItem(block.asItem().defaultInstance, x, y, size, color)
        }

        fun addShadow(x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color) {
            addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color)
        }

        fun addShadow(
            x: Float, y: Float, width: Float, height: Float, topLeft: Float, topRight: Float,
            bottomRight: Float, bottomLeft: Float, blurRadius: Float, color: Color,
        ) {
            scheduler.add(
                Render2DCommand.Shadow(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor,
                    topLeft, topRight, bottomRight, bottomLeft, blurRadius, color
                )
            )
        }

        fun addShadow(
            x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
            segmentRects: FloatArray, segmentRadii: FloatArray, segmentCount: Int,
        ) {
            addShadow(
                x, y, width, height, radius, radius, radius, radius, blurRadius, color,
                segmentRects, segmentRadii, null, segmentCount
            )
        }

        fun addShadow(
            x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
            segmentRects: FloatArray, segmentRadii: FloatArray, segmentColors: FloatArray?, segmentCount: Int,
        ) {
            addShadow(
                x, y, width, height, radius, radius, radius, radius, blurRadius, color,
                segmentRects, segmentRadii, segmentColors, segmentCount
            )
        }

        fun addShadow(
            x: Float, y: Float, width: Float, height: Float,
            topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float,
            blurRadius: Float, color: Color,
            segmentRects: FloatArray, segmentRadii: FloatArray, segmentCount: Int,
        ) {
            addShadow(
                x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, color,
                segmentRects, segmentRadii, null, segmentCount
            )
        }

        fun addShadow(
            x: Float, y: Float, width: Float, height: Float,
            topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float,
            blurRadius: Float, color: Color,
            segmentRects: FloatArray, segmentRadii: FloatArray, segmentColors: FloatArray?, segmentCount: Int,
        ) {
            scheduler.add(
                Render2DCommand.SegmentedShadow(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor,
                    topLeft, topRight, bottomRight, bottomLeft, blurRadius, color,
                    segmentRects, segmentRadii, segmentColors, segmentCount
                )
            )
        }

        fun addRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
            addRoundRect(x, y, width, height, radius, radius, radius, radius, color)
        }

        fun addRoundRect(
            x: Float, y: Float, width: Float, height: Float, topLeft: Float, topRight: Float,
            bottomRight: Float, bottomLeft: Float, color: Color,
        ) {
            addRoundRectGradient(
                x,
                y,
                width,
                height,
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                color,
                color,
                color,
                color
            )
        }

        fun addRoundRectGradient(
            x: Float, y: Float, width: Float, height: Float, topLeftRadius: Float, topRightRadius: Float,
            bottomRightRadius: Float, bottomLeftRadius: Float, topLeft: Color, bottomLeft: Color,
            bottomRight: Color, topRight: Color,
        ) {
            scheduler.add(
                Render2DCommand.RoundRect(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor,
                    topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                    topLeft, bottomLeft, bottomRight, topRight
                )
            )
        }

        fun addOutline(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            radius: Float,
            outlineWidth: Float,
            color: Color
        ) {
            addOutline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color)
        }

        fun addOutline(
            x: Float, y: Float, width: Float, height: Float, topLeft: Float, topRight: Float,
            bottomRight: Float, bottomLeft: Float, outlineWidth: Float, color: Color,
        ) {
            scheduler.add(
                Render2DCommand.RoundRectOutline(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor,
                    topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color
                )
            )
        }

        fun addRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
            addRectGradient(x, y, width, height, color, color, color, color)
        }

        fun addRectGradient(
            x: Float, y: Float, width: Float, height: Float,
            topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color,
        ) {
            scheduler.add(
                Render2DCommand.Rect(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor,
                    topLeft, bottomLeft, bottomRight, topRight
                )
            )
        }

        fun addRectOutline(x: Float, y: Float, width: Float, height: Float, outline: Float, color: Color) {
            val sideHeight = height - outline * 2.0f
            addRect(x, y, width, outline, color)
            addRect(x, y + height - outline, width, outline, color)
            addRect(x, y + outline, outline, sideHeight, color)
            addRect(x + width - outline, y + outline, outline, sideHeight, color)
        }

        fun addChevronTriangle(centerX: Float, centerY: Float, size: Float, progress: Float, color: Color) {
            scheduler.add(
                Render2DCommand.Triangle(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(centerX - size, centerY - size, size * 2.0f, size * 2.0f), currentScissor,
                    centerX, centerY, size, progress, color
                )
            )
        }

        fun addArc(
            centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
            startDegrees: Float, sweepDegrees: Float, roundCap: Boolean, color: Color,
        ) {
            addGradientArc(
                centerX, centerY, radius, strokeWidth, startDegrees, sweepDegrees, roundCap,
                0.0f, color, color, color
            )
        }

        fun addGradientArc(
            centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
            startDegrees: Float, sweepDegrees: Float, roundCap: Boolean,
            gradientRotationDegrees: Float,
            startColor: Color, middleColor: Color, endColor: Color,
        ) {
            val extent = radius + strokeWidth * 0.5f + 1.0f
            scheduler.add(
                Render2DCommand.Arc(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(centerX - extent, centerY - extent, extent * 2.0f, extent * 2.0f), currentScissor,
                    centerX, centerY, radius, strokeWidth, startDegrees, sweepDegrees, roundCap,
                    gradientRotationDegrees, startColor, middleColor, endColor
                )
            )
        }

        fun addTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        ) {
            addRoundedTexture(texture, x, y, width, height, 0.0f, u0, v0, u1, v1, color)
        }

        fun addRoundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            radius: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        ) {
            addRoundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color)
        }

        fun addRoundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        ) {
            // Texture command 只保存轻量资源引用；纹理解析和 GPU 提交由 TextureRenderer 在 flush 时处理。
            scheduler.add(
                Render2DCommand.Texture(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor, texture,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    u0, v0, u1, v1, color, x, y, 0.0f
                )
            )
        }

        fun addRotatedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float,
        ) {
            addRotatedRoundedTexture(
                texture, x, y, width, height, 0.0f, 0.0f, 0.0f, 0.0f,
                u0, v0, u1, v1, color, originX, originY, rotationDegrees
            )
        }

        fun addRotatedRoundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float,
            radiusBottomRight: Float, radiusBottomLeft: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float,
        ) {
            scheduler.add(
                Render2DCommand.Texture(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x, y, width, height), currentScissor, texture,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    u0, v0, u1, v1, color, originX, originY, rotationDegrees
                )
            )
        }

        fun addText(text: String, x: Float, y: Float, scale: Float, color: Color) {
            addText(text, x, y, scale, color, null)
        }

        fun addText(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?) {
            addText(text, x, y, scale, color, fontLoader, x, y, 0.0f)
        }

        fun addGradientText(text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color) {
            addGradientText(text, x, y, scale, startColor, endColor, null)
        }

        fun addGradientText(
            text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color,
            fontLoader: TtfFontLoader?,
        ) {
            val metrics = scheduler.textMetrics()
            val width =
                if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
            val height = if (fontLoader != null) metrics.getHeight(scale, fontLoader) else metrics.getHeight(scale)
            scheduler.add(
                Render2DCommand.GradientText(
                    layerIndex, scheduler.nextSequence(), Render2DBounds.of(x, y, width, height), currentScissor,
                    text, x, y, scale, startColor, endColor, fontLoader
                )
            )
        }

        fun addBlurredText(
            text: String, x: Float, y: Float, scale: Float, color: Color,
            blurRadius: Float, intensity: Int,
        ) {
            addBlurredText(text, x, y, scale, color, blurRadius, intensity, null)
        }

        fun addBlurredText(
            text: String, x: Float, y: Float, scale: Float, color: Color,
            blurRadius: Float, intensity: Int, fontLoader: TtfFontLoader?,
        ) {
            if (!JavaFloat.isFinite(blurRadius) || blurRadius <= 0.0f) {
                return
            }
            val clampedBlur = minOf(blurRadius, MAX_FONT_BLUR_RADIUS)
            val metrics = scheduler.textMetrics()
            val width =
                if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
            val height = if (fontLoader != null) metrics.getHeight(scale, fontLoader) else metrics.getHeight(scale)
            val padding = clampedBlur + 2.0f
            scheduler.add(
                Render2DCommand.BlurText(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(x - padding, y - padding, width + padding * 2.0f, height + padding * 2.0f),
                    currentScissor, text, x, y, scale, color, clampedBlur, intensity, fontLoader
                )
            )
        }

        fun addGlitchText(
            text: String, x: Float, y: Float, scale: Float, color: Color,
            effect: TextGlitchEffect?,
        ) {
            addGlitchText(text, x, y, scale, color, effect, null)
        }

        fun addGlitchText(
            text: String, x: Float, y: Float, scale: Float, color: Color,
            effect: TextGlitchEffect?, fontLoader: TtfFontLoader?,
        ) {
            if (text.isEmpty() || color.alpha == 0 || effect == null) {
                return
            }
            val metrics = scheduler.textMetrics()
            val width =
                if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
            val height = if (fontLoader != null) metrics.getHeight(scale, fontLoader) else metrics.getHeight(scale)
            val padding = effect.requiredPadding()
            scheduler.add(
                Render2DCommand.GlitchText(
                    layerIndex, scheduler.nextSequence(),
                    Render2DBounds.of(
                        x - padding, y - padding,
                        width + padding * 2.0f, height + padding * 2.0f
                    ),
                    currentScissor, text, x, y, scale, color, effect, fontLoader
                )
            )
        }

        fun addRotatedText(
            text: String, x: Float, y: Float, scale: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float,
        ) {
            addText(text, x, y, scale, color, null, originX, originY, rotationDegrees)
        }

        fun addRotatedText(
            text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?,
            originX: Float, originY: Float, rotationDegrees: Float,
        ) {
            addText(text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees)
        }

        private fun addText(
            text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?,
            originX: Float, originY: Float, rotationDegrees: Float,
        ) {
            val metrics = scheduler.textMetrics()
            val width =
                if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
            val height = if (fontLoader != null) metrics.getHeight(scale, fontLoader) else metrics.getHeight(scale)
            val bounds = if (rotationDegrees == 0.0f) {
                Render2DBounds.of(x, y, width, height)
            } else {
                rotatedBounds(x, y, width, height, originX, originY, rotationDegrees)
            }
            scheduler.add(
                Render2DCommand.Text(
                    layerIndex, scheduler.nextSequence(),
                    bounds, currentScissor,
                    text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees
                )
            )
        }

        private companion object {
            fun rotatedBounds(
                x: Float, y: Float, width: Float, height: Float,
                originX: Float, originY: Float, rotationDegrees: Float,
            ): Render2DBounds {
                val radians = Math.toRadians(rotationDegrees.toDouble()).toFloat()
                val cos = cos(radians.toDouble()).toFloat()
                val sin = sin(radians.toDouble()).toFloat()
                val x1 = rotateX(x, y, originX, originY, cos, sin)
                val y1 = rotateY(x, y, originX, originY, cos, sin)
                val x2 = rotateX(x, y + height, originX, originY, cos, sin)
                val y2 = rotateY(x, y + height, originX, originY, cos, sin)
                val x3 = rotateX(x + width, y + height, originX, originY, cos, sin)
                val y3 = rotateY(x + width, y + height, originX, originY, cos, sin)
                val x4 = rotateX(x + width, y, originX, originY, cos, sin)
                val y4 = rotateY(x + width, y, originX, originY, cos, sin)
                val minX = minOf(minOf(x1, x2), minOf(x3, x4))
                val minY = minOf(minOf(y1, y2), minOf(y3, y4))
                val maxX = maxOf(maxOf(x1, x2), maxOf(x3, x4))
                val maxY = maxOf(maxOf(y1, y2), maxOf(y3, y4))
                return Render2DBounds.of(minX, minY, maxX - minX, maxY - minY)
            }

            fun rotateX(x: Float, y: Float, originX: Float, originY: Float, cos: Float, sin: Float): Float {
                val dx = x - originX
                val dy = y - originY
                return originX + dx * cos - dy * sin
            }

            fun rotateY(x: Float, y: Float, originX: Float, originY: Float, cos: Float, sin: Float): Float {
                val dx = x - originX
                val dy = y - originY
                return originY + dx * sin + dy * cos
            }
        }
    }

    private class RendererBundle(
        val kind: Render2DCommandKind,
        private val renderer: IRenderer,
    ) : AutoCloseable {
        var scissor: Render2DScissor? = null

        fun shapeRenderer(): RoundRectOutlineRenderer = renderer as RoundRectOutlineRenderer

        fun shadowRenderer(): ShadowRenderer = renderer as ShadowRenderer

        fun arcRenderer(): ArcRenderer = renderer as ArcRenderer

        fun textureRenderer(): TextureRenderer = renderer as TextureRenderer

        fun textRenderer(): TextRenderer = renderer as TextRenderer

        fun applyScissor(scissor: Render2DScissor?) {
            this.scissor = scissor
            clearScissor()
            if (scissor == null) {
                return
            }
            when (renderer) {
                is ShadowRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is RoundRectRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is RoundRectOutlineRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is RectRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is TriangleRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is ArcRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is TextureRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                is TextRenderer -> renderer.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                else -> {
                }
            }
        }

        fun clearScissor() {
            when (renderer) {
                is ShadowRenderer -> renderer.clearScissor()
                is RoundRectRenderer -> renderer.clearScissor()
                is RoundRectOutlineRenderer -> renderer.clearScissor()
                is RectRenderer -> renderer.clearScissor()
                is TriangleRenderer -> renderer.clearScissor()
                is ArcRenderer -> renderer.clearScissor()
                is TextureRenderer -> renderer.clearScissor()
                is TextRenderer -> renderer.clearScissor()
                else -> {
                }
            }
        }

        fun prepareSharedDraw(): Boolean = renderer.prepareSharedDraw()

        fun draw(pass: RenderPass) {
            renderer.draw(pass)
        }

        override fun close() {
            renderer.close()
        }

        companion object {
            fun create(kind: Render2DCommandKind): RendererBundle = (when (kind) {
                Render2DCommandKind.SHADOW -> RendererBundle(kind, ShadowRenderer.create())
                Render2DCommandKind.ROUND_RECT -> RendererBundle(kind, RoundRectOutlineRenderer.createShape())
                Render2DCommandKind.ROUND_RECT_OUTLINE -> RendererBundle(kind, RoundRectOutlineRenderer.createShape())
                Render2DCommandKind.RECT -> RendererBundle(kind, RoundRectOutlineRenderer.createShape())
                Render2DCommandKind.TRIANGLE -> RendererBundle(kind, RoundRectOutlineRenderer.createShape())
                Render2DCommandKind.ARC -> RendererBundle(kind, ArcRenderer.create())
                Render2DCommandKind.TEXTURE -> RendererBundle(kind, TextureRenderer.create())
                Render2DCommandKind.PREMULTIPLIED_TEXTURE -> RendererBundle(kind, TextureRenderer.create())
                Render2DCommandKind.ITEM -> RendererBundle(kind, TextureRenderer.create())
                Render2DCommandKind.BLUR_TEXT -> RendererBundle(kind, TextRenderer.createFontBlur())
                Render2DCommandKind.GLITCH_TEXT -> RendererBundle(kind, TextRenderer.createGlitch())
                Render2DCommandKind.TEXT -> RendererBundle(kind, TextRenderer.create())
            }).also { RendererManager.unregister(it.renderer) }
        }
    }

    companion object {
        private const val MAX_FONT_BLUR_RADIUS = 20.0f
        private fun intersect(a: Render2DScissor?, b: Render2DScissor?): Render2DScissor? {
            if (a == null) return b
            if (b == null) return a
            val x = maxOf(a.x, b.x);
            val y = maxOf(a.y, b.y)
            return Render2DScissor(
                x, y, maxOf(0, minOf(a.x + a.width, b.x + b.width) - x),
                maxOf(0, minOf(a.y + a.height, b.y + b.height) - y)
            )
        }
    }
}
