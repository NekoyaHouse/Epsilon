package com.github.epsilon.utils.render

import com.github.epsilon.Constants.mc
import com.github.epsilon.graphics.LuminRenderSystem
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.logging.LogUtils
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.SharedConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.pip.OversizedItemRenderer
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.*
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import net.minecraft.util.profiling.Profiler
import net.minecraft.util.profiling.ProfilerFiller
import org.apache.commons.lang3.mutable.MutableBoolean
import org.joml.Matrix3x2fc
import org.joml.Matrix4f
import org.joml.Vector4f
import org.joml.Vector4fc
import org.slf4j.Logger
import java.util.*
import java.util.function.Supplier
import kotlin.math.ceil

class EpsilonGuiRenderer(
    private val renderState: GuiRenderState,
    private val featureRenderDispatcher: FeatureRenderDispatcher,
) : AutoCloseable {

    private val oversizedItemRenderers: MutableMap<Any, OversizedItemRenderer> = Object2ObjectOpenHashMap()
    private val draws: MutableList<Draw> = ArrayList()
    private val vertexBuffer = StagedVertexBuffer(Supplier { "GUI Vertex Buffer" }, 786432)
    private var firstDrawIndexAfterBlur: Int = Int.MAX_VALUE
    private val guiProjection = Projection()
    private val guiProjectionMatrixBuffer = ProjectionMatrixBuffer("gui")
    private var itemAtlas: GuiItemAtlas? = null
    private var cachedGuiScale: Double = Double.NaN
    private val cubeMap = CubeMap(Identifier.withDefaultNamespace("textures/gui/title/background/panorama"))
    private var previousScissorArea: ScreenRectangle? = null
    private var previousPipeline: RenderPipeline? = null
    private var previousTextureSetup: TextureSetup? = null
    private var previousDraw: StagedVertexBuffer.Draw? = null

    fun endFrame() {
        val atlas = itemAtlas
        if (atlas != null) {
            atlas.endFrame()
        }
    }

    fun render() {
        val profiler: ProfilerFiller = Profiler.get()
        val panoramaRenderState = renderState.panoramaRenderState
        if (panoramaRenderState != null) {
            cubeMap.render(10.0f, panoramaRenderState.spin())
        }

        profiler.push("prepare")
        prepare()
        profiler.popPush("upload")
        vertexBuffer.upload()
        profiler.popPush("draw")
        draw()
        profiler.popPush("endFrame")
        vertexBuffer.endDraw()
        vertexBuffer.endFrame()
        draws.clear()
        renderState.reset()
        firstDrawIndexAfterBlur = Int.MAX_VALUE
        clearUnusedOversizedItemRenderers()
        if (SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER) {
            RenderPipeline.updateSortKeySeed()
            TextureSetup.updateSortKeySeed()
        }

        profiler.pop()
    }

    private fun clearUnusedOversizedItemRenderers() {
        val oversizedItemRendererIterator = oversizedItemRenderers.entries.iterator()

        while (oversizedItemRendererIterator.hasNext()) {
            val renderer = oversizedItemRendererIterator.next().value
            if (!renderer.usedOnThisFrame()) {
                renderer.close()
                oversizedItemRendererIterator.remove()
            } else {
                renderer.resetUsedOnThisFrame()
            }
        }
    }

    private fun prepare() {
        prepareItemElements()
        prepareText()
        renderState.sortElements(ELEMENT_SORT_COMPARATOR)
        addElementsToMeshes(GuiRenderState.TraverseRange.BEFORE_BLUR)
        firstDrawIndexAfterBlur = draws.size
        addElementsToMeshes(GuiRenderState.TraverseRange.AFTER_BLUR)
    }

    private fun addElementsToMeshes(range: GuiRenderState.TraverseRange) {
        previousScissorArea = null
        previousPipeline = null
        previousTextureSetup = null
        previousDraw = null
        renderState.forEachElement(this::addElementToMesh, range)
    }

    private fun draw() {
        if (!draws.isEmpty()) {
            guiProjection.setupOrtho(
                1000.0f, 11000.0f, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight(), true
            )
            RenderSystem.setProjectionMatrix(
                guiProjectionMatrixBuffer.getBuffer(guiProjection),
                ProjectionType.ORTHOGRAPHIC
            )
            val minecraft = mc
            val mainRenderTarget = minecraft.gameRenderer.mainRenderTarget()
            val dynamicTransforms =
                RenderSystem.getDynamicUniforms().writeTransform(Matrix4f().setTranslation(0.0f, 0.0f, -11000.0f))
            if (firstDrawIndexAfterBlur > 0) {
                executeDrawRange(
                    Supplier { "GUI before blur" },
                    mainRenderTarget,
                    dynamicTransforms,
                    0,
                    minOf(firstDrawIndexAfterBlur, draws.size)
                )
            }

            if (draws.size > firstDrawIndexAfterBlur) {
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainRenderTarget.depthTexture!!, 0.0)
                minecraft.gameRenderer.processBlurEffect()
                executeDrawRange(
                    Supplier { "GUI after blur" },
                    mainRenderTarget,
                    dynamicTransforms,
                    firstDrawIndexAfterBlur,
                    draws.size
                )
            }
        }
    }

    private fun executeDrawRange(
        label: Supplier<String>,
        mainRenderTarget: RenderTarget,
        dynamicTransforms: GpuBufferSlice,
        startIndex: Int,
        endIndex: Int,
    ) {
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                label,
                mainRenderTarget.colorTextureView!!,
                Optional.empty(),
                if (mainRenderTarget.hasDepth()) mainRenderTarget.depthTextureView else null,
                OptionalDouble.empty()
            ).use { renderPass ->
                RenderSystem.bindDefaultUniforms(renderPass)
                renderPass.setUniform("DynamicTransforms", dynamicTransforms)

                for (i in startIndex until endIndex) {
                    val draw = draws[i]
                    executeDraw(draw, renderPass)
                }
            }
    }

    private fun addElementToMesh(elementState: GuiElementRenderState) {
        val pipeline = elementState.pipeline()
        val textureSetup = elementState.textureSetup()
        val scissorArea = elementState.scissorArea()
        if (previousDraw == null
            || pipeline !== previousPipeline
            || scissorChanged(scissorArea, previousScissorArea)
            || textureSetup != previousTextureSetup
        ) {
            previousPipeline = pipeline
            previousTextureSetup = textureSetup
            previousScissorArea = scissorArea
            val appendDraw = vertexBuffer.appendDraw(pipeline.getVertexFormatBinding(0)!!, pipeline.primitiveTopology)
            previousDraw = appendDraw
            draws.add(Draw(appendDraw, pipeline, textureSetup, scissorArea))
        }

        elementState.buildVertices(this.vertexBuffer.getVertexBuilder(checkNotNull(previousDraw)))
    }

    private fun prepareText() {
        renderState.forEachText { text ->
            val pose: Matrix3x2fc = text.pose
            val scissor = text.scissor
            text.ensurePrepared().visit(object : Font.GlyphVisitor {
                override fun acceptRenderable(renderable: TextRenderable) {
                    renderState.addGlyphToCurrentLayer(GlyphRenderState(pose, renderable, scissor))
                }
            })
        }
    }

    private fun prepareItemElements() {
        val itemsInFrame = renderState.itemModelIdentities
        if (!itemsInFrame.isEmpty()) {
            val guiScale = getGuiScaleInvalidatingItemAtlasIfChanged()
            val itemAtlas = prepareItemAtlas(
                itemsInFrame,
                maxOf(1, ceil((DEFAULT_ITEM_SIZE * LuminRenderSystem.getGuiScale())).toInt())
            )
            val hasOversizedItems = MutableBoolean(false)
            renderState.forEachItem { itemState ->
                if (itemState.oversizedItemBounds() != null) {
                    hasOversizedItems.setTrue()
                } else {
                    val slotView = itemAtlas.getOrUpdate(itemState.itemStackRenderState())
                    if (slotView != null) {
                        submitBlitFromItemAtlas(itemState, slotView)
                    }
                }
            }
            if (hasOversizedItems.booleanValue()) {
                renderState.forEachItem { itemState ->
                    val actualItemBounds = itemState.oversizedItemBounds()
                    if (actualItemBounds != null) {
                        val itemStackRenderState: TrackingItemStackRenderState = itemState.itemStackRenderState()
                        val modelIdentity = itemStackRenderState.modelIdentity
                        val existingRenderer = oversizedItemRenderers[modelIdentity]
                        val oversizedItemRenderer = existingRenderer ?: OversizedItemRenderer().also {
                            oversizedItemRenderers[modelIdentity] = it
                        }
                        val oversizedItemRenderState = OversizedItemRenderState(
                            itemState,
                            actualItemBounds.left(),
                            actualItemBounds.top(),
                            actualItemBounds.right(),
                            actualItemBounds.bottom()
                        )
                        oversizedItemRenderer.prepare(
                            oversizedItemRenderState,
                            renderState,
                            featureRenderDispatcher,
                            guiScale
                        )
                    }
                }
            }
        }
    }

    private fun submitBlitFromItemAtlas(itemState: GuiItemRenderState, slotView: GuiItemAtlas.SlotView) {
        renderState.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(
                    slotView.textureView(),
                    RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
                ),
                itemState.pose(),
                itemState.x(),
                itemState.y(),
                itemState.x() + 16,
                itemState.y() + 16,
                slotView.u0(),
                slotView.u1(),
                slotView.v0(),
                slotView.v1(),
                -1,
                itemState.scissorArea(),
                null
            )
        )
    }

    private fun prepareItemAtlas(itemsInFrame: Set<Any>, slotTextureSize: Int): GuiItemAtlas {
        val existingAtlas = itemAtlas
        if (existingAtlas != null && existingAtlas.tryPrepareFor(itemsInFrame)) {
            return existingAtlas
        }

        val newTextureSize = GuiItemAtlas.computeTextureSizeFor(slotTextureSize, itemsInFrame.size)
        if (existingAtlas != null && existingAtlas.textureSize() == newTextureSize) {
            LOGGER.warn(
                "Too many items ({}) in UI, some will be skipped! (Reached maximum texture size {}x{})",
                itemsInFrame.size,
                newTextureSize,
                newTextureSize
            )
            return existingAtlas
        }

        if (existingAtlas != null) {
            existingAtlas.close()
        }

        val createdAtlas = GuiItemAtlas(featureRenderDispatcher, newTextureSize, slotTextureSize)
        itemAtlas = createdAtlas
        return createdAtlas
    }

    private fun getGuiScaleInvalidatingItemAtlasIfChanged(): Int {
        val guiScale = LuminRenderSystem.getGuiScale()
        if (java.lang.Double.compare(guiScale, cachedGuiScale) != 0) {
            invalidateItemAtlas()

            for (renderer in oversizedItemRenderers.values) {
                renderer.invalidateTexture()
            }

            cachedGuiScale = guiScale
        }

        return maxOf(1, ceil(guiScale).toInt())
    }

    private fun invalidateItemAtlas() {
        val atlas = itemAtlas
        if (atlas != null) {
            atlas.close()
            itemAtlas = null
        }
    }

    private fun executeDraw(draw: Draw, renderPass: RenderPass) {
        val executeInfo = vertexBuffer.getExecuteInfo(draw.draw)
        if (executeInfo != null) {
            val pipeline = draw.pipeline
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
            renderPass.setVertexBuffer(0, executeInfo.vertexBuffer().slice())
            val scissorArea = draw.scissorArea
            if (scissorArea != null) {
                if (!enableScissor(scissorArea, renderPass)) {
                    return
                }
            } else {
                renderPass.disableScissor()
            }

            if (draw.textureSetup.texure0() != null) {
                renderPass.setUniform("Sampler0", draw.textureSetup.texure0(), draw.textureSetup.sampler0())
            }

            if (draw.textureSetup.texure1() != null) {
                renderPass.setUniform("Sampler1", draw.textureSetup.texure1(), draw.textureSetup.sampler1())
            }

            if (draw.textureSetup.texure2() != null) {
                renderPass.setUniform("Sampler2", draw.textureSetup.texure2(), draw.textureSetup.sampler2())
            }

            renderPass.setIndexBuffer(executeInfo.indexBuffer(), executeInfo.indexType())
            renderPass.drawIndexed(executeInfo.indexCount(), 1, executeInfo.firstIndex(), executeInfo.baseVertex(), 0)
        }
    }

    private fun scissorChanged(newScissor: ScreenRectangle?, oldScissor: ScreenRectangle?): Boolean {
        return newScissor !== oldScissor && (newScissor == null || newScissor != oldScissor)
    }

    private fun enableScissor(rectangle: ScreenRectangle, renderPass: RenderPass): Boolean {
        val window = mc.gameRenderer.gameRenderState().windowRenderState
        val scissor = ScissorUtils.toFramebufferScissor(
            rectangle.left().toFloat(),
            rectangle.top().toFloat(),
            rectangle.width().toFloat(),
            rectangle.height().toFloat(),
            LuminRenderSystem.getGuiScale(),
            window.height
        )
        return ScissorUtils.enableScissor(renderPass, scissor)
    }

    fun registerPanoramaTextures(textureManager: TextureManager) {
        cubeMap.registerTextures(textureManager)
    }

    override fun close() {
        vertexBuffer.close()
        val atlas = itemAtlas
        if (atlas != null) {
            atlas.close()
            itemAtlas = null
        }

        guiProjectionMatrixBuffer.close()
        oversizedItemRenderers.values.forEach { renderer -> renderer.close() }
        cubeMap.close()
    }

    private data class Draw(
        val draw: StagedVertexBuffer.Draw,
        val pipeline: RenderPipeline,
        val textureSetup: TextureSetup,
        val scissorArea: ScreenRectangle?,
    )

    companion object {

        private val LOGGER: Logger = LogUtils.getLogger()
        private const val MAX_GUI_Z = 10000.0f
        const val MIN_GUI_Z = 0.0f
        private const val GUI_Z_NEAR = 1000.0f
        const val GUI_3D_Z_FAR = 1000
        const val GUI_3D_Z_NEAR = -1000
        const val DEFAULT_ITEM_SIZE = 16
        val CLEAR_COLOR: Vector4fc = Vector4f(0.0f)

        private fun compareScreenRectangle(a: ScreenRectangle, b: ScreenRectangle): Int {
            var result = a.top().compareTo(b.top())
            if (result == 0) {
                result = a.bottom().compareTo(b.bottom())
            }
            if (result == 0) {
                result = a.left().compareTo(b.left())
            }
            if (result == 0) {
                result = a.right().compareTo(b.right())
            }
            return result
        }

        private val SCISSOR_COMPARATOR: Comparator<ScreenRectangle?> = Comparator.nullsFirst(
            Comparator<ScreenRectangle?> { a, b ->
                if (a == null || b == null) 0 else compareScreenRectangle(a, b)
            }
        )

        private val TEXTURE_COMPARATOR: Comparator<TextureSetup?> = Comparator.nullsFirst(
            Comparator<TextureSetup?> { a, b ->
                if (a == null || b == null) 0 else a.sortKey.compareTo(b.sortKey)
            }
        )

        private val ELEMENT_SORT_COMPARATOR: Comparator<GuiElementRenderState> = Comparator { a, b ->
            var result = when {
                a.scissorArea() === b.scissorArea() -> 0
                a.scissorArea() == null -> -1
                b.scissorArea() == null -> 1
                else -> compareScreenRectangle(a.scissorArea()!!, b.scissorArea()!!)
            }
            if (result == 0) {
                result = a.pipeline().sortKey.compareTo(b.pipeline().sortKey)
            }
            if (result == 0) {
                val textureA = a.textureSetup()
                val textureB = b.textureSetup()
                result = when {
                    textureA === textureB -> 0
                    textureA == null -> -1
                    textureB == null -> 1
                    else -> textureA.sortKey.compareTo(textureB.sortKey)
                }
            }
            result
        }
    }

}
