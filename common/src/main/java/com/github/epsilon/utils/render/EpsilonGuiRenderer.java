package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;

import static com.github.epsilon.Constants.mc;

public class EpsilonGuiRenderer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float MAX_GUI_Z = 10000.0F;
    public static final float MIN_GUI_Z = 0.0F;
    private static final float GUI_Z_NEAR = 1000.0F;
    public static final int GUI_3D_Z_FAR = 1000;
    public static final int GUI_3D_Z_NEAR = -1000;
    public static final int DEFAULT_ITEM_SIZE = 16;
    public static final int CLEAR_COLOR = 0;
    private static final Comparator<ScreenRectangle> SCISSOR_COMPARATOR = Comparator.nullsFirst(
            Comparator.comparing(ScreenRectangle::top)
                    .thenComparing(ScreenRectangle::bottom)
                    .thenComparing(ScreenRectangle::left)
                    .thenComparing(ScreenRectangle::right)
    );
    private static final Comparator<TextureSetup> TEXTURE_COMPARATOR = Comparator.nullsFirst(Comparator.comparing(TextureSetup::getSortKey));
    private static final Comparator<GuiElementRenderState> ELEMENT_SORT_COMPARATOR = Comparator.comparing(
                    GuiElementRenderState::scissorArea, SCISSOR_COMPARATOR
            )
            .thenComparing(GuiElementRenderState::pipeline, Comparator.comparing(RenderPipeline::getSortKey))
            .thenComparing(GuiElementRenderState::textureSetup, TEXTURE_COMPARATOR);
    private final Map<Object, OversizedItemRenderer> oversizedItemRenderers = new Object2ObjectOpenHashMap<>();
    private final GuiRenderState renderState;
    private final List<EpsilonGuiRenderer.Draw> draws = new ArrayList<>();
    private final List<EpsilonGuiRenderer.MeshToDraw> meshesToDraw = new ArrayList<>();
    private final ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432);
    private final Map<VertexFormat, LuminRingBuffer> vertexBuffers = new Object2ObjectOpenHashMap<>();
    private int firstDrawIndexAfterBlur = Integer.MAX_VALUE;
    private final Projection guiProjection = new Projection();
    private final ProjectionMatrixBuffer guiProjectionMatrixBuffer = new ProjectionMatrixBuffer("gui");
    private final MultiBufferSource.BufferSource bufferSource;
    private final SubmitNodeCollector submitNodeCollector;
    private final FeatureRenderDispatcher featureRenderDispatcher;
    private GuiItemAtlas itemAtlas;
    private double cachedGuiScale = Double.NaN;
    private final CubeMap cubeMap = new CubeMap(Identifier.withDefaultNamespace("textures/gui/title/background/panorama"));
    private ScreenRectangle previousScissorArea = null;
    private RenderPipeline previousPipeline = null;
    private TextureSetup previousTextureSetup = null;
    private BufferBuilder bufferBuilder = null;

    public EpsilonGuiRenderer(
            GuiRenderState renderState,
            MultiBufferSource.BufferSource bufferSource,
            SubmitNodeCollector submitNodeCollector,
            FeatureRenderDispatcher featureRenderDispatcher
    ) {
        this.renderState = renderState;
        this.bufferSource = bufferSource;
        this.submitNodeCollector = submitNodeCollector;
        this.featureRenderDispatcher = featureRenderDispatcher;
    }

    public void endFrame() {
        if (this.itemAtlas != null) {
            this.itemAtlas.endFrame();
        }
    }

    public void render(GpuBufferSlice fogBuffer) {
        ProfilerFiller profiler = Profiler.get();
        if (this.renderState.panoramaRenderState != null) {
            this.cubeMap.render(10.0F, this.renderState.panoramaRenderState.spin());
        }

        profiler.push("prepare");
        this.prepare();
        profiler.popPush("draw");
        this.draw(fogBuffer);
        profiler.popPush("vertexBufferRotate");

        for (LuminRingBuffer buffer : this.vertexBuffers.values()) {
            buffer.rotate();
        }

        profiler.pop();
        this.draws.clear();
        this.meshesToDraw.clear();
        this.renderState.reset();
        this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
        this.clearUnusedOversizedItemRenderers();
        if (SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER) {
            RenderPipeline.updateSortKeySeed();
            TextureSetup.updateSortKeySeed();
        }
    }

    private void clearUnusedOversizedItemRenderers() {
        Iterator<Entry<Object, OversizedItemRenderer>> oversizedItemRendererIterator = this.oversizedItemRenderers.entrySet().iterator();

        while (oversizedItemRendererIterator.hasNext()) {
            Entry<Object, OversizedItemRenderer> next = oversizedItemRendererIterator.next();
            OversizedItemRenderer renderer = next.getValue();
            if (!renderer.usedOnThisFrame()) {
                renderer.close();
                oversizedItemRendererIterator.remove();
            } else {
                renderer.resetUsedOnThisFrame();
            }
        }
    }

    private void prepare() {
        this.bufferSource.endBatch();
        this.prepareItemElements();
        this.prepareText();
        this.renderState.sortElements(ELEMENT_SORT_COMPARATOR);
        this.addElementsToMeshes(GuiRenderState.TraverseRange.BEFORE_BLUR);
        this.firstDrawIndexAfterBlur = this.meshesToDraw.size();
        this.addElementsToMeshes(GuiRenderState.TraverseRange.AFTER_BLUR);
        this.recordDraws();
    }

    private void addElementsToMeshes(GuiRenderState.TraverseRange range) {
        this.previousScissorArea = null;
        this.previousPipeline = null;
        this.previousTextureSetup = null;
        this.bufferBuilder = null;
        this.renderState.forEachElement(this::addElementToMesh, range);
        if (this.bufferBuilder != null) {
            this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea);
        }
    }

    private void draw(GpuBufferSlice fogBuffer) {
        if (!this.draws.isEmpty()) {
            this.guiProjection
                    .setupOrtho(1000.0F, 11000.0F, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight(), true);
            RenderSystem.setProjectionMatrix(this.guiProjectionMatrixBuffer.getBuffer(this.guiProjection), ProjectionType.ORTHOGRAPHIC);
            RenderTarget mainRenderTarget = mc.getMainRenderTarget();
            int maxIndexCount = 0;

            for (EpsilonGuiRenderer.Draw draw : this.draws) {
                if (draw.indexCount > maxIndexCount) {
                    maxIndexCount = draw.indexCount;
                }
            }

            GpuBuffer indexBuffer = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount);
            VertexFormat.IndexType indexType = LuminRenderSystem.getQuadIndexType();
            GpuBufferSlice dynamicTransforms = LuminRenderSystem.writeTransform(
                    new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
            if (this.firstDrawIndexAfterBlur > 0) {
                this.executeDrawRange(
                        () -> "GUI before blur",
                        mainRenderTarget,
                        fogBuffer,
                        dynamicTransforms,
                        indexBuffer,
                        indexType,
                        0,
                        Math.min(this.firstDrawIndexAfterBlur, this.draws.size())
                );
            }

            if (this.draws.size() > this.firstDrawIndexAfterBlur) {
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainRenderTarget.getDepthTexture(), 1.0);
                mc.gameRenderer.processBlurEffect();
                this.executeDrawRange(
                        () -> "GUI after blur",
                        mainRenderTarget,
                        fogBuffer,
                        dynamicTransforms,
                        indexBuffer,
                        indexType,
                        this.firstDrawIndexAfterBlur,
                        this.draws.size()
                );
            }
        }
    }

    private void executeDrawRange(
            Supplier<String> label,
            RenderTarget mainRenderTarget,
            GpuBufferSlice fogBuffer,
            GpuBufferSlice dynamicTransforms,
            GpuBuffer indexBuffer,
            VertexFormat.IndexType indexType,
            int startIndex,
            int endIndex
    ) {
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        label,
                        mainRenderTarget.getColorTextureView(),
                        OptionalInt.empty(),
                        mainRenderTarget.useDepth ? mainRenderTarget.getDepthTextureView() : null,
                        OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("Fog", fogBuffer);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            for (int i = startIndex; i < endIndex; i++) {
                EpsilonGuiRenderer.Draw draw = this.draws.get(i);
                this.executeDraw(draw, renderPass, indexBuffer, indexType);
            }
        }
    }

    private void addElementToMesh(GuiElementRenderState elementState) {
        RenderPipeline pipeline = elementState.pipeline();
        TextureSetup textureSetup = elementState.textureSetup();
        ScreenRectangle scissorArea = elementState.scissorArea();
        if (pipeline != this.previousPipeline || this.scissorChanged(scissorArea, this.previousScissorArea) || !textureSetup.equals(this.previousTextureSetup)) {
            if (this.bufferBuilder != null) {
                this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea);
            }

            this.bufferBuilder = this.getBufferBuilder(pipeline);
            this.previousPipeline = pipeline;
            this.previousTextureSetup = textureSetup;
            this.previousScissorArea = scissorArea;
        }

        elementState.buildVertices(this.bufferBuilder);
    }

    private void prepareText() {
        this.renderState.forEachText(text -> {
            final Matrix3x2fc pose = text.pose;
            final ScreenRectangle scissor = text.scissor;
            text.ensurePrepared().visit(new Font.GlyphVisitor() {
                {
                    Objects.requireNonNull(EpsilonGuiRenderer.this);
                }

                @Override
                public void acceptGlyph(TextRenderable.Styled glyph) {
                    this.accept(glyph);
                }

                @Override
                public void acceptEffect(TextRenderable effect) {
                    this.accept(effect);
                }

                private void accept(TextRenderable glyph) {
                    EpsilonGuiRenderer.this.renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, glyph, scissor));
                }
            });
        });
    }

    private void prepareItemElements() {
        Set<Object> itemsInFrame = this.renderState.getItemModelIdentities();
        if (!itemsInFrame.isEmpty()) {
            int guiScale = this.getGuiScaleInvalidatingItemAtlasIfChanged();
            GuiItemAtlas itemAtlas = this.prepareItemAtlas(itemsInFrame, Math.max(1, (int) Math.ceil(DEFAULT_ITEM_SIZE * LuminRenderSystem.getGuiScale())));
            MutableBoolean hasOversizedItems = new MutableBoolean(false);
            this.renderState.forEachItem(itemState -> {
                if (itemState.oversizedItemBounds() != null) {
                    hasOversizedItems.setTrue();
                } else {
                    GuiItemAtlas.SlotView slotView = itemAtlas.getOrUpdate(itemState.itemStackRenderState());
                    if (slotView != null) {
                        this.submitBlitFromItemAtlas(itemState, slotView);
                    }
                }
            });
            if (hasOversizedItems.booleanValue()) {
                this.renderState
                        .forEachItem(
                                itemState -> {
                                    if (itemState.oversizedItemBounds() != null) {
                                        TrackingItemStackRenderState itemStackRenderState = itemState.itemStackRenderState();
                                        OversizedItemRenderer oversizedItemRenderer = this.oversizedItemRenderers
                                                .computeIfAbsent(itemStackRenderState.getModelIdentity(), key -> new OversizedItemRenderer(this.bufferSource));
                                        ScreenRectangle actualItemBounds = itemState.oversizedItemBounds();
                                        OversizedItemRenderState oversizedItemRenderState = new OversizedItemRenderState(
                                                itemState, actualItemBounds.left(), actualItemBounds.top(), actualItemBounds.right(), actualItemBounds.bottom()
                                        );
                                        oversizedItemRenderer.prepare(oversizedItemRenderState, this.renderState, guiScale);
                                    }
                                }
                        );
            }
        }
    }

    private void submitBlitFromItemAtlas(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView) {
        this.renderState
                .addBlitToCurrentLayer(
                        new BlitRenderState(
                                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                                TextureSetup.singleTexture(slotView.textureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
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
                );
    }

    private GuiItemAtlas prepareItemAtlas(Set<Object> itemsInFrame, int slotTextureSize) {
        if (this.itemAtlas != null && this.itemAtlas.tryPrepareFor(itemsInFrame)) {
            return this.itemAtlas;
        } else {
            int newTextureSize = GuiItemAtlas.computeTextureSizeFor(slotTextureSize, itemsInFrame.size());
            if (this.itemAtlas != null && this.itemAtlas.textureSize() == newTextureSize) {
                LOGGER.warn(
                        "Too many items ({}) in UI, some will be skipped! (Reached maximum texture size {}x{})",
                        itemsInFrame.size(),
                        newTextureSize,
                        newTextureSize
                );
                return this.itemAtlas;
            } else {
                if (this.itemAtlas != null) {
                    this.itemAtlas.close();
                }

                this.itemAtlas = new GuiItemAtlas(this.submitNodeCollector, this.featureRenderDispatcher, this.bufferSource, newTextureSize, slotTextureSize);
                return this.itemAtlas;
            }
        }
    }

    private int getGuiScaleInvalidatingItemAtlasIfChanged() {
        double guiScale = LuminRenderSystem.getGuiScale();
        if (Double.compare(guiScale, this.cachedGuiScale) != 0) {
            this.invalidateItemAtlas();

            for (OversizedItemRenderer renderer : this.oversizedItemRenderers.values()) {
                renderer.invalidateTexture();
            }

            this.cachedGuiScale = guiScale;
        }

        return Math.max(1, (int) Math.ceil(guiScale));
    }

    private void invalidateItemAtlas() {
        if (this.itemAtlas != null) {
            this.itemAtlas.close();
            this.itemAtlas = null;
        }
    }

    private void recordMesh(BufferBuilder bufferBuilder, RenderPipeline pipeline, TextureSetup textureSetup, @Nullable ScreenRectangle scissorArea) {
        MeshData mesh = bufferBuilder.build();
        if (mesh != null) {
            this.meshesToDraw.add(new EpsilonGuiRenderer.MeshToDraw(mesh, pipeline, textureSetup, scissorArea));
        }
    }

    private void recordDraws() {
        this.ensureVertexBufferSizes();
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        Object2IntMap<VertexFormat> offsets = new Object2IntOpenHashMap<>();

        for (EpsilonGuiRenderer.MeshToDraw meshToDraw : this.meshesToDraw) {
            MeshData mesh = meshToDraw.mesh;
            MeshData.DrawState drawState = mesh.drawState();
            VertexFormat format = drawState.format();
            LuminRingBuffer vertexBuffer = this.vertexBuffers.get(format);
            if (!offsets.containsKey(format)) {
                offsets.put(format, 0);
            }

            ByteBuffer meshVertexBuffer = mesh.vertexBuffer();
            int meshBufferSize = meshVertexBuffer.remaining();
            int offset = offsets.getInt(format);

            vertexBuffer.write(commandEncoder, offset, meshVertexBuffer);

            offsets.put(format, offset + meshBufferSize);
            this.draws
                    .add(
                            new EpsilonGuiRenderer.Draw(
                                    vertexBuffer.getGpuBuffer(),
                                    offset / format.getVertexSize(),
                                    drawState.mode(),
                                    drawState.indexCount(),
                                    meshToDraw.pipeline,
                                    meshToDraw.textureSetup,
                                    meshToDraw.scissorArea
                            )
                    );
            meshToDraw.close();
        }
    }

    private void ensureVertexBufferSizes() {
        Object2IntMap<VertexFormat> requiredSizes = this.calculatedRequiredVertexBufferSizes();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<VertexFormat> entry : requiredSizes.object2IntEntrySet()) {
            VertexFormat vertexFormat = entry.getKey();
            int requiredSize = entry.getIntValue();
            LuminRingBuffer vertexBuffer = this.vertexBuffers.get(vertexFormat);
            if (vertexBuffer == null || vertexBuffer.size() < requiredSize) {
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }

                this.vertexBuffers.put(vertexFormat, new LuminRingBuffer(requiredSize, GpuBuffer.USAGE_VERTEX));
            }
        }
    }

    private Object2IntMap<VertexFormat> calculatedRequiredVertexBufferSizes() {
        Object2IntMap<VertexFormat> requiredVertexBufferSizes = new Object2IntOpenHashMap<>();

        for (EpsilonGuiRenderer.MeshToDraw meshToDraw : this.meshesToDraw) {
            MeshData.DrawState drawState = meshToDraw.mesh.drawState();
            VertexFormat format = drawState.format();
            if (!requiredVertexBufferSizes.containsKey(format)) {
                requiredVertexBufferSizes.put(format, 0);
            }

            requiredVertexBufferSizes.put(format, requiredVertexBufferSizes.getInt(format) + drawState.vertexCount() * format.getVertexSize());
        }

        return requiredVertexBufferSizes;
    }

    private void executeDraw(EpsilonGuiRenderer.Draw draw, RenderPass renderPass, GpuBuffer indexBuffer, VertexFormat.IndexType indexType) {
        RenderPipeline pipeline = draw.pipeline();
        renderPass.setPipeline(pipeline);
        renderPass.setVertexBuffer(0, draw.vertexBuffer);
        ScreenRectangle scissorArea = draw.scissorArea();
        if (scissorArea != null) {
            if (!this.enableScissor(scissorArea, renderPass)) {
                return;
            }
        } else {
            renderPass.disableScissor();
        }

        if (draw.textureSetup.texure0() != null) {
            renderPass.bindTexture("Sampler0", draw.textureSetup.texure0(), draw.textureSetup.sampler0());
        }

        if (draw.textureSetup.texure1() != null) {
            renderPass.bindTexture("Sampler1", draw.textureSetup.texure1(), draw.textureSetup.sampler1());
        }

        if (draw.textureSetup.texure2() != null) {
            renderPass.bindTexture("Sampler2", draw.textureSetup.texure2(), draw.textureSetup.sampler2());
        }

        renderPass.setIndexBuffer(indexBuffer, indexType);
        renderPass.drawIndexed(draw.baseVertex, 0, draw.indexCount, 1);
    }

    private BufferBuilder getBufferBuilder(RenderPipeline pipeline) {
        return new BufferBuilder(this.byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
    }

    private boolean scissorChanged(@Nullable ScreenRectangle newScissor, @Nullable ScreenRectangle oldScissor) {
        if (newScissor == oldScissor) {
            return false;
        } else {
            return newScissor != null ? !newScissor.equals(oldScissor) : true;
        }
    }

    private boolean enableScissor(ScreenRectangle rectangle, RenderPass renderPass) {
        WindowRenderState window = mc.gameRenderer.getGameRenderState().windowRenderState;
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.toFramebufferScissor(
                rectangle.left(),
                rectangle.top(),
                rectangle.width(),
                rectangle.height(),
                window.guiScale,
                window.height
        );
        return ScissorUtils.enableScissor(renderPass, scissor);
    }

    public void registerPanoramaTextures(TextureManager textureManager) {
        this.cubeMap.registerTextures(textureManager);
    }

    @Override
    public void close() {
        this.byteBufferBuilder.close();
        if (this.itemAtlas != null) {
            this.itemAtlas.close();
            this.itemAtlas = null;
        }

        this.guiProjectionMatrixBuffer.close();

        for (LuminRingBuffer buffer : this.vertexBuffers.values()) {
            buffer.close();
        }

        this.oversizedItemRenderers.values().forEach(PictureInPictureRenderer::close);
        this.cubeMap.close();
    }

    private record Draw(
            GpuBuffer vertexBuffer,
            int baseVertex,
            VertexFormat.Mode mode,
            int indexCount,
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            ScreenRectangle scissorArea
    ) {
    }

    private record MeshToDraw(MeshData mesh, RenderPipeline pipeline, TextureSetup textureSetup,
                              ScreenRectangle scissorArea) implements AutoCloseable {
        @Override
        public void close() {
            this.mesh.close();
        }
    }

}
