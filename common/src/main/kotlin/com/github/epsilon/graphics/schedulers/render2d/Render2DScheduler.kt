package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.ArcRenderer
import com.github.epsilon.graphics.renderers.IRenderer
import com.github.epsilon.graphics.renderers.RectRenderer
import com.github.epsilon.graphics.renderers.RoundRectOutlineRenderer
import com.github.epsilon.graphics.renderers.RoundRectRenderer
import com.github.epsilon.graphics.renderers.ShadowRenderer
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.renderers.TextureRenderer
import com.github.epsilon.graphics.renderers.TriangleRenderer
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.modules.impl.ClientSetting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.awt.Color
import java.util.LinkedHashMap
import java.util.Optional
import java.util.OptionalDouble

/**
 * 2D GUI 渲染调度器。
 *
 * GUI 只提交声明式绘制命令；调度器负责 layer、scissor、空间桶、批次规划和 renderer 复用。
 */
class Render2DScheduler : AutoCloseable {

    private val commandStore: CommandStore
    private val layerHandles: Int2ObjectMap<LayerHandle> = Int2ObjectOpenHashMap()
    private val rendererPool: RendererPool = RendererPool()
    private val measureTextRenderer: TextRenderer = TextRenderer.create(256 * 1024L)
    private var sequence: Long = 0

    constructor() : this(DEFAULT_QUADTREE_THRESHOLD)

    constructor(quadtreeThreshold: Int) {
        commandStore = CommandStore(Math.max(1, quadtreeThreshold))
    }

    fun layer(layer: Int): LayerHandle = layerHandles.computeIfAbsent(layer) { LayerHandle(this, layer) }

    fun textMetrics(): TextRenderer = measureTextRenderer

    fun isEmpty(): Boolean = commandStore.isEmpty()

    fun clear() {
        commandStore.clear()
        for (handle in layerHandles.values) {
            handle.clearScissor()
        }
        sequence = 0L
    }

    fun clearLayer(layer: Int) {
        commandStore.clearLayer(layer)
        val handle = layerHandles[layer]
        if (handle != null) {
            handle.clearScissor()
        }
    }

    fun flush() {
        if (commandStore.isEmpty()) {
            return
        }

        flushBatchGroups(FrameBatchPlanner.plan(commandStore.planBatches()))
    }

    fun flushLayer(layer: Int) {
        val bucket = commandStore.layer(layer)
        if (bucket != null) {
            flushLayer(bucket)
        }
    }

    fun flushAndClear() {
        flush()
        clear()
    }

    private fun add(command: Render2DCommand) {
        commandStore.add(command)
    }

    private fun nextSequence(): Long = sequence++

    private fun flushLayer(bucket: LayerBucket) {
        if (bucket.count() == 0) {
            return
        }
        flushBatchGroups(FrameBatchPlanner.plan(bucket.planBatches()))
    }

    private fun flushBatchGroups(groups: List<BatchGroup>) {
        if (groups.isEmpty()) {
            return
        }
        val prepared = ArrayList<PreparedBatch>(groups.size)
        for (group in groups) {
            val batch = prepareBatchGroup(group)
            if (batch != null) {
                prepared.add(batch)
            }
        }
        flushPreparedBatches(prepared)
    }

    private fun prepareBatchGroup(group: BatchGroup): PreparedBatch? {
        val scissor = group.scissor
        if (group.commands.isEmpty() || scissor != null && !scissor.visible()) {
            return null
        }
        val renderers = rendererPool.acquire(group.kind, group.scissor)
        for (command in group.commands) {
            emit(command, renderers)
        }
        return PreparedBatch(pipelineFor(group.kind), renderers)
    }

    private fun flushPreparedBatches(batches: List<PreparedBatch>) {
        if (batches.isEmpty()) {
            return
        }

        LuminRenderSystem.applyOrthoProjection()
        val drawable = ArrayList<PreparedBatch>(batches.size)
        for (batch in batches) {
            if (batch.renderers.prepareSharedDraw()) {
                drawable.add(batch)
            }
        }
        if (drawable.isEmpty()) {
            return
        }

        var index = 0
        while (index < drawable.size) {
            val pipeline = drawable[index].pipeline
            var end = index + 1
            while (end < drawable.size && drawable[end].pipeline === pipeline) {
                end++
            }
            flushPipelineRun(pipeline, drawable, index, end)
            index = end
        }
    }

    private fun flushPipelineRun(pipeline: RenderPipeline, batches: List<PreparedBatch>, start: Int, end: Int) {
        val colorView = LuminRenderSystem.resolveColorView() ?: return

        val depthView = if (pipeline === LuminRenderPipelines.TEXTURE) null else LuminRenderSystem.resolveDepthView()
        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(
                { "Lumin 2D Pipeline Run" },
                colorView, Optional.empty(),
                depthView, OptionalDouble.empty()
            ).use { pass ->
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
                RenderSystem.bindDefaultUniforms(pass)
                for (i in start until end) {
                    batches[i].renderers.draw(pass)
                }
            }
    }

    private fun pipelineFor(kind: Render2DCommandKind): RenderPipeline = when (kind) {
        Render2DCommandKind.SHADOW -> LuminRenderPipelines.SHADOW
        Render2DCommandKind.ROUND_RECT -> LuminRenderPipelines.ROUND_RECT
        Render2DCommandKind.ROUND_RECT_OUTLINE -> LuminRenderPipelines.ROUND_RECT_OUTLINE
        Render2DCommandKind.RECT -> LuminRenderPipelines.RECTANGLE
        Render2DCommandKind.TRIANGLE -> LuminRenderPipelines.TRIANGLE
        Render2DCommandKind.ARC -> LuminRenderPipelines.ARC
        Render2DCommandKind.TEXTURE -> LuminRenderPipelines.TEXTURE
        Render2DCommandKind.BLUR_TEXT -> LuminRenderPipelines.TTF_FONT_BLUR
        Render2DCommandKind.GLITCH_TEXT -> LuminRenderPipelines.TTF_FONT_GLITCH
        Render2DCommandKind.TEXT -> if (ClientSetting.INSTANCE.fontAntiAliasing.getValue()) {
            LuminRenderPipelines.TTF_FONT_AA
        } else {
            LuminRenderPipelines.TTF_FONT_NO_AA
        }
    }

    private fun emit(command: Render2DCommand, renderers: RendererBundle) {
        when (command) {
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

            is Render2DCommand.RoundRect -> renderers.roundRectRenderer().addRoundRectGradient(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                command.topLeft, command.bottomLeft, command.bottomRight, command.topRight
            )

            is Render2DCommand.RoundRectOutline -> renderers.roundRectOutlineRenderer().addOutline(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                command.outlineWidth, command.color
            )

            is Render2DCommand.Rect -> renderers.rectRenderer().addRectGradient(
                command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                command.topLeft, command.bottomLeft, command.bottomRight, command.topRight
            )

            is Render2DCommand.Triangle -> renderers.triangleRenderer().addChevronTriangle(
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
                            command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                            command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                            command.u0, command.v0, command.u1, command.v1, command.color, texture.linearFilter
                        )
                    } else {
                        renderers.textureRenderer().addRotatedRoundedTexture(
                            texture.identifier,
                            command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                            command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                            command.u0, command.v0, command.u1, command.v1, command.color,
                            command.originX, command.originY, command.rotationDegrees, texture.linearFilter
                        )
                    }
                } else if (texture is Render2DTexture.LuminRef) {
                    if (command.rotationDegrees == 0.0f) {
                        renderers.textureRenderer().addRoundedTexture(
                            texture.texture,
                            command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                            command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                            command.u0, command.v0, command.u1, command.v1, command.color
                        )
                    } else {
                        renderers.textureRenderer().addRotatedRoundedTexture(
                            texture.texture,
                            command.bounds.x, command.bounds.y, command.bounds.width, command.bounds.height,
                            command.radiusTopLeft, command.radiusTopRight, command.radiusBottomRight, command.radiusBottomLeft,
                            command.u0, command.v0, command.u1, command.v1, command.color,
                            command.originX, command.originY, command.rotationDegrees
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

    override fun close() {
        clear()
        rendererPool.close()
        measureTextRenderer.close()
        layerHandles.clear()
    }

    class LayerHandle internal constructor(
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
            addRoundRectGradient(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color, color, color, color)
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

        fun addOutline(x: Float, y: Float, width: Float, height: Float, radius: Float, outlineWidth: Float, color: Color) {
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
            val width = if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
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
            if (!java.lang.Float.isFinite(blurRadius) || blurRadius <= 0.0f) {
                return
            }
            val clampedBlur = Math.min(blurRadius, MAX_FONT_BLUR_RADIUS)
            val metrics = scheduler.textMetrics()
            val width = if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
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
            val width = if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
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
            val width = if (fontLoader != null) metrics.getWidth(text, scale, fontLoader) else metrics.getWidth(text, scale)
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
                val cos = Math.cos(radians.toDouble()).toFloat()
                val sin = Math.sin(radians.toDouble()).toFloat()
                val x1 = rotateX(x, y, originX, originY, cos, sin)
                val y1 = rotateY(x, y, originX, originY, cos, sin)
                val x2 = rotateX(x, y + height, originX, originY, cos, sin)
                val y2 = rotateY(x, y + height, originX, originY, cos, sin)
                val x3 = rotateX(x + width, y + height, originX, originY, cos, sin)
                val y3 = rotateY(x + width, y + height, originX, originY, cos, sin)
                val x4 = rotateX(x + width, y, originX, originY, cos, sin)
                val y4 = rotateY(x + width, y, originX, originY, cos, sin)
                val minX = Math.min(Math.min(x1, x2), Math.min(x3, x4))
                val minY = Math.min(Math.min(y1, y2), Math.min(y3, y4))
                val maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4))
                val maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4))
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

    private class CommandStore(private val quadtreeThreshold: Int) {
        private val layerBuckets: Int2ObjectMap<LayerBucket> = Int2ObjectOpenHashMap()

        fun add(command: Render2DCommand) {
            // 先按整数 layer 隔离命令；每个 layer 内部再决定使用线性数组还是四叉树。
            layerBuckets.computeIfAbsent(command.layer) { LayerBucket(quadtreeThreshold) }.add(command)
        }

        fun isEmpty(): Boolean = layerBuckets.isEmpty()

        private fun layers(): Set<Int> = layerBuckets.keys

        fun layer(layer: Int): LayerBucket? = layerBuckets[layer]

        fun planBatches(): List<BatchGroup> {
            val orderedLayers: MutableList<Int> = ArrayList(layerBuckets.keys)
            java.util.Collections.sort(orderedLayers)

            val result = ArrayList<BatchGroup>()
            for (layer in orderedLayers) {
                val bucket = layerBuckets[layer]
                if (bucket == null || bucket.count() == 0) {
                    continue
                }
                for (group in bucket.planBatches()) {
                    if (group.visible()) {
                        result.add(group)
                    }
                }
            }
            return result
        }

        fun clear() {
            layerBuckets.clear()
        }

        fun clearLayer(layer: Int) {
            layerBuckets.remove(layer)
        }
    }

    private class LayerBucket(private val quadtreeThreshold: Int) {
        private val flatCommands: MutableList<Render2DCommand> = ArrayList()
        private var quadTree: QuadTreeBucket? = null
        private var bounds: Render2DBounds? = null

        fun add(command: Render2DCommand) {
            val currentBounds = bounds
            bounds = if (currentBounds == null) command.bounds else currentBounds.include(command.bounds)
            if (quadTree == null && flatCommands.size + 1 < quadtreeThreshold) {
                // 小批量 GUI 使用紧凑数组，避免四叉树节点分配带来的固定成本。
                flatCommands.add(command)
                return
            }
            if (quadTree == null) {
                promoteToQuadTree()
            }
            quadTree!!.add(command)
        }

        fun count(): Int = quadTree?.count() ?: flatCommands.size

        fun planBatches(): List<BatchGroup> {
            val ordered = ArrayList<Render2DCommand>(count())
            val tree = quadTree
            if (tree == null) {
                ordered.addAll(flatCommands)
            } else {
                tree.collect(ordered)
            }
            ordered.sortWith(STABLE_SEQUENCE_ORDER)
            return BatchPlanner.plan(ordered)
        }

        private fun promoteToQuadTree() {
            // 命令数量超过阈值后才升级为空间桶，为后续可见性裁剪和脏区重建预留结构。
            val currentBounds = bounds
            val rootBounds = if (currentBounds == null) Render2DBounds.of(0.0f, 0.0f, 1.0f, 1.0f) else padded(currentBounds)
            val tree = QuadTreeBucket(rootBounds, 0, DEFAULT_QUADTREE_MAX_DEPTH)
            quadTree = tree
            for (command in flatCommands) {
                tree.add(command)
            }
            flatCommands.clear()
        }

        private companion object {
            fun padded(bounds: Render2DBounds): Render2DBounds {
                val size = Math.max(Math.max(bounds.width, bounds.height), 1.0f)
                return Render2DBounds.of(bounds.x - 1.0f, bounds.y - 1.0f, size + 2.0f, size + 2.0f)
            }
        }
    }

    private class QuadTreeBucket(
        private val bounds: Render2DBounds,
        private val depth: Int,
        private val maxDepth: Int,
    ) {
        private val commands: MutableList<Render2DCommand> = ArrayList()
        private var children: Array<QuadTreeBucket>? = null
        private var nodeCount: Int = 0

        fun add(command: Render2DCommand) {
            nodeCount++
            val currentChildren = children
            if (currentChildren != null && insertIntoChild(command)) {
                return
            }
            commands.add(command)
            if (commands.size > NODE_CAPACITY && depth < maxDepth) {
                split()
            }
        }

        fun count(): Int = nodeCount

        fun collect(out: MutableList<Render2DCommand>) {
            out.addAll(commands)
            val currentChildren = children
            if (currentChildren == null) {
                return
            }
            for (child in currentChildren) {
                child.collect(out)
            }
        }

        private fun insertIntoChild(command: Render2DCommand): Boolean {
            val currentChildren = children ?: return false
            for (child in currentChildren) {
                if (child.bounds.contains(command.bounds)) {
                    child.add(command)
                    return true
                }
            }
            return false
        }

        private fun split() {
            if (children != null) {
                return
            }
            // 只把完全落入子象限的命令下沉，跨象限的大图元留在父节点以避免重复提交。
            val halfW = bounds.width * 0.5f
            val halfH = bounds.height * 0.5f
            children = arrayOf(
                QuadTreeBucket(Render2DBounds.of(bounds.x, bounds.y, halfW, halfH), depth + 1, maxDepth),
                QuadTreeBucket(Render2DBounds.of(bounds.x + halfW, bounds.y, halfW, halfH), depth + 1, maxDepth),
                QuadTreeBucket(Render2DBounds.of(bounds.x, bounds.y + halfH, halfW, halfH), depth + 1, maxDepth),
                QuadTreeBucket(Render2DBounds.of(bounds.x + halfW, bounds.y + halfH, halfW, halfH), depth + 1, maxDepth)
            )
            val iterator = commands.iterator()
            while (iterator.hasNext()) {
                val command = iterator.next()
                if (insertIntoChild(command)) {
                    iterator.remove()
                }
            }
        }

        private companion object {
            const val NODE_CAPACITY = 24
        }
    }

    private object BatchPlanner {
        fun plan(commands: List<Render2DCommand>): List<BatchGroup> {
            val groups = LinkedHashMap<BatchKey, BatchGroup>()
            for (command in commands) {
                val key = BatchKey(command.kind(), command.scissor)
                groups.computeIfAbsent(key) { BatchGroup(command.kind(), command.scissor) }.add(command)
            }

            val result = ArrayList<BatchGroup>(groups.values)
            // 同 layer 内按 renderer 类型合批；需要精确保序的图元应通过更细的 layer 表达遮挡关系。
            result.sortWith(
                Comparator
                    .comparingInt { group: BatchGroup -> KIND_FLUSH_ORDER.indexOf(group.kind) }
                    .thenComparingLong { group: BatchGroup -> group.firstSequence() }
            )
            for (group in result) {
                group.commands.sortWith(STABLE_SEQUENCE_ORDER)
            }
            return result
        }
    }

    private object FrameBatchPlanner {
        fun plan(groups: List<BatchGroup>): List<BatchGroup> {
            val count = groups.size
            if (count <= 1) {
                return groups
            }

            val outgoing = ArrayList<MutableList<Int>>(count)
            for (i in 0 until count) {
                outgoing.add(ArrayList())
            }
            val incomingCounts = IntArray(count)

            for (i in 0 until count) {
                val before = groups[i]
                for (j in i + 1 until count) {
                    val after = groups[j]
                    if (mustKeepOrder(before, after)) {
                        outgoing[i].add(j)
                        incomingCounts[j]++
                    }
                }
            }

            val scheduled = BooleanArray(count)
            val result = ArrayList<BatchGroup>(count)
            var preferredKind: Render2DCommandKind? = null
            var scheduledCount = 0

            while (scheduledCount < count) {
                var next = selectReady(groups, scheduled, incomingCounts, preferredKind)
                if (next < 0) {
                    next = selectFirstUnscheduled(scheduled)
                }

                val merged = groups[next]
                scheduledCount += schedule(next, outgoing, incomingCounts, scheduled)

                val mergeKey = merged.key()
                while (true) {
                    val sameKey = selectReady(groups, scheduled, incomingCounts, mergeKey)
                    if (sameKey < 0) {
                        break
                    }
                    merged.append(groups[sameKey])
                    scheduledCount += schedule(sameKey, outgoing, incomingCounts, scheduled)
                }

                result.add(merged)
                preferredKind = merged.kind
            }

            return result
        }

        private fun mustKeepOrder(before: BatchGroup, after: BatchGroup): Boolean =
            before.bounds!!.intersects(after.bounds!!)

        private fun selectReady(
            groups: List<BatchGroup>, scheduled: BooleanArray, incomingCounts: IntArray,
            preferredKind: Render2DCommandKind?,
        ): Int {
            var fallback = -1
            for (i in groups.indices) {
                if (scheduled[i] || incomingCounts[i] > 0) {
                    continue
                }
                if (fallback < 0) {
                    fallback = i
                }
                if (preferredKind != null && groups[i].kind == preferredKind) {
                    return i
                }
            }
            return fallback
        }

        private fun selectReady(
            groups: List<BatchGroup>, scheduled: BooleanArray, incomingCounts: IntArray,
            key: BatchKey,
        ): Int {
            for (i in groups.indices) {
                if (!scheduled[i] && incomingCounts[i] == 0 && groups[i].key().equals(key)) {
                    return i
                }
            }
            return -1
        }

        private fun selectFirstUnscheduled(scheduled: BooleanArray): Int {
            for (i in scheduled.indices) {
                if (!scheduled[i]) {
                    return i
                }
            }
            return -1
        }

        private fun schedule(
            index: Int, outgoing: List<List<Int>>, incomingCounts: IntArray, scheduled: BooleanArray,
        ): Int {
            if (index < 0 || scheduled[index]) {
                return 0
            }
            scheduled[index] = true
            for (target in outgoing[index]) {
                incomingCounts[target]--
            }
            return 1
        }
    }

    @JvmRecord
    private data class BatchKey(val kind: Render2DCommandKind, val scissor: Render2DScissor?)

    private class BatchGroup(val kind: Render2DCommandKind, val scissor: Render2DScissor?) {
        val commands: MutableList<Render2DCommand> = ArrayList()
        var bounds: Render2DBounds? = null

        fun add(command: Render2DCommand) {
            commands.add(command)
            val orderingBounds = command.orderingBounds()
            val currentBounds = bounds
            bounds = if (currentBounds == null) orderingBounds else currentBounds.include(orderingBounds)
        }

        fun append(other: BatchGroup) {
            if (other.commands.isEmpty()) {
                return
            }
            commands.addAll(other.commands)
            val otherBounds = other.bounds
            val currentBounds = bounds
            bounds = if (currentBounds == null) otherBounds else currentBounds.include(otherBounds!!)
        }

        fun visible(): Boolean {
            val currentScissor = scissor
            return commands.isNotEmpty() && (currentScissor == null || currentScissor.visible())
        }

        fun firstSequence(): Long = if (commands.isEmpty()) Long.MAX_VALUE else commands.first().sequence

        fun key(): BatchKey = BatchKey(kind, scissor)
    }

    private class RendererPool : AutoCloseable {
        private val available: MutableMap<Render2DCommandKind, java.util.Deque<RendererBundle>> =
            Object2ObjectOpenHashMap()
        private val used: MutableList<RendererBundle> = ArrayList()
        private var frameId: Long = Long.MIN_VALUE

        fun acquire(kind: Render2DCommandKind, scissor: Render2DScissor?): RendererBundle {
            val currentFrameId = LuminRenderSystem.getRenderFrameId()
            if (frameId != currentFrameId) {
                clear()
                frameId = currentFrameId
            }
            val bundles = available.computeIfAbsent(kind) { java.util.ArrayDeque<RendererBundle>() }
            val bundle = bundles.pollFirst() ?: RendererBundle.create(kind)
            bundle.applyScissor(scissor)
            used.add(bundle)
            return bundle
        }

        fun clear() {
            for (bundle in used) {
                bundle.clear()
                available.computeIfAbsent(bundle.kind) { java.util.ArrayDeque<RendererBundle>() }.addLast(bundle)
            }
            used.clear()
        }

        override fun close() {
            clear()
            for (bundles in available.values) {
                for (bundle in bundles) {
                    bundle.close()
                }
            }
            available.clear()
        }
    }

    @JvmRecord
    private data class PreparedBatch(val pipeline: RenderPipeline, val renderers: RendererBundle)

    private class RendererBundle(
        val kind: Render2DCommandKind,
        private val renderer: IRenderer,
    ) : AutoCloseable {
        var scissor: Render2DScissor? = null

        fun shadowRenderer(): ShadowRenderer = renderer as ShadowRenderer

        fun roundRectRenderer(): RoundRectRenderer = renderer as RoundRectRenderer

        fun roundRectOutlineRenderer(): RoundRectOutlineRenderer = renderer as RoundRectOutlineRenderer

        fun rectRenderer(): RectRenderer = renderer as RectRenderer

        fun triangleRenderer(): TriangleRenderer = renderer as TriangleRenderer

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

        fun draw() {
            renderer.draw()
        }

        fun prepareSharedDraw(): Boolean = renderer.prepareSharedDraw()

        fun draw(pass: RenderPass) {
            renderer.draw(pass)
        }

        fun clear() {
            clearScissor()
            renderer.clear()
        }

        override fun close() {
            renderer.close()
        }

        companion object {
            fun create(kind: Render2DCommandKind): RendererBundle = when (kind) {
                Render2DCommandKind.SHADOW -> RendererBundle(kind, ShadowRenderer.create())
                Render2DCommandKind.ROUND_RECT -> RendererBundle(kind, RoundRectRenderer.create())
                Render2DCommandKind.ROUND_RECT_OUTLINE -> RendererBundle(kind, RoundRectOutlineRenderer.create())
                Render2DCommandKind.RECT -> RendererBundle(kind, RectRenderer.create())
                Render2DCommandKind.TRIANGLE -> RendererBundle(kind, TriangleRenderer.create())
                Render2DCommandKind.ARC -> RendererBundle(kind, ArcRenderer.create())
                Render2DCommandKind.TEXTURE -> RendererBundle(kind, TextureRenderer.create())
                Render2DCommandKind.BLUR_TEXT -> RendererBundle(kind, TextRenderer.createFontBlur())
                Render2DCommandKind.GLITCH_TEXT -> RendererBundle(kind, TextRenderer.createGlitch())
                Render2DCommandKind.TEXT -> RendererBundle(kind, TextRenderer.create())
            }
        }
    }

    companion object {
        const val DEFAULT_QUADTREE_THRESHOLD = 192
        private const val MAX_FONT_BLUR_RADIUS = 20.0f
        private const val DEFAULT_QUADTREE_MAX_DEPTH = 5
        private val STABLE_SEQUENCE_ORDER: Comparator<Render2DCommand> =
            Comparator.comparingLong { command: Render2DCommand -> command.sequence }
        private val KIND_FLUSH_ORDER: List<Render2DCommandKind> = listOf(
            Render2DCommandKind.SHADOW,
            Render2DCommandKind.ROUND_RECT,
            Render2DCommandKind.ROUND_RECT_OUTLINE,
            Render2DCommandKind.RECT,
            Render2DCommandKind.TRIANGLE,
            Render2DCommandKind.ARC,
            Render2DCommandKind.TEXTURE,
            Render2DCommandKind.BLUR_TEXT,
            Render2DCommandKind.GLITCH_TEXT,
            Render2DCommandKind.TEXT
        )
    }

}
