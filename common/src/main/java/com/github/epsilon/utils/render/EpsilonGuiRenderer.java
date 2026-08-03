package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
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
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

public class EpsilonGuiRenderer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float MAX_GUI_Z = 10000.0F;
    public static final float MIN_GUI_Z = 0.0F;
    private static final float GUI_Z_NEAR = 1000.0F;
    public static final int GUI_3D_Z_FAR = 1000;
    public static final int GUI_3D_Z_NEAR = -1000;
    public static final int DEFAULT_ITEM_SIZE = 16;
    public static final Vector4fc CLEAR_COLOR = new Vector4f(0.0F);
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
    private final List<Draw> draws = new ArrayList<>();
    private final StagedVertexBuffer vertexBuffer = new StagedVertexBuffer(() -> "GUI Vertex Buffer", 786432);
    private int firstDrawIndexAfterBlur = Integer.MAX_VALUE;
    private final Projection guiProjection = new Projection();
    private final ProjectionMatrixBuffer guiProjectionMatrixBuffer = new ProjectionMatrixBuffer("gui");
    private final FeatureRenderDispatcher featureRenderDispatcher;
    private GuiItemAtlas itemAtlas;
    private double cachedGuiScale = Double.NaN;
    private final CubeMap cubeMap = new CubeMap(Identifier.withDefaultNamespace("textures/gui/title/background/panorama"));
    private ScreenRectangle previousScissorArea = null;
    private RenderPipeline previousPipeline = null;
    private TextureSetup previousTextureSetup = null;
    private StagedVertexBuffer.Draw previousDraw;

    public EpsilonGuiRenderer(GuiRenderState renderState, FeatureRenderDispatcher featureRenderDispatcher) {
        this.renderState = renderState;
        this.featureRenderDispatcher = featureRenderDispatcher;
    }

    public void endFrame() {
        if (this.itemAtlas != null) {
            this.itemAtlas.endFrame();
        }
    }

    public void render() {
        ProfilerFiller profiler = Profiler.get();
        if (this.renderState.panoramaRenderState != null) {
            this.cubeMap.render(10.0F, this.renderState.panoramaRenderState.spin());
        }

        profiler.push("prepare");
        this.prepare();
        profiler.popPush("upload");
        this.vertexBuffer.upload();
        profiler.popPush("draw");
        this.draw();
        profiler.popPush("endFrame");
        this.vertexBuffer.endDraw();
        this.vertexBuffer.endFrame();
        this.draws.clear();
        this.renderState.reset();
        this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
        this.clearUnusedOversizedItemRenderers();
        if (SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER) {
            RenderPipeline.updateSortKeySeed();
            TextureSetup.updateSortKeySeed();
        }

        profiler.pop();
    }

    private void clearUnusedOversizedItemRenderers() {
        Iterator<Map.Entry<Object, OversizedItemRenderer>> oversizedItemRendererIterator = this.oversizedItemRenderers.entrySet().iterator();

        while (oversizedItemRendererIterator.hasNext()) {
            Map.Entry<Object, OversizedItemRenderer> next = oversizedItemRendererIterator.next();
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
        this.prepareItemElements();
        this.prepareText();
        this.renderState.sortElements(ELEMENT_SORT_COMPARATOR);
        this.addElementsToMeshes(GuiRenderState.TraverseRange.BEFORE_BLUR);
        this.firstDrawIndexAfterBlur = this.draws.size();
        this.addElementsToMeshes(GuiRenderState.TraverseRange.AFTER_BLUR);
    }

    private void addElementsToMeshes(GuiRenderState.TraverseRange range) {
        this.previousScissorArea = null;
        this.previousPipeline = null;
        this.previousTextureSetup = null;
        this.previousDraw = null;
        this.renderState.forEachElement(this::addElementToMesh, range);
    }

    private void draw() {
        if (!this.draws.isEmpty()) {
            this.guiProjection
                    .setupOrtho(1000.0F, 11000.0F, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight(), true);
            RenderSystem.setProjectionMatrix(this.guiProjectionMatrixBuffer.getBuffer(this.guiProjection), ProjectionType.ORTHOGRAPHIC);
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget mainRenderTarget = minecraft.gameRenderer.mainRenderTarget();
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F));
            if (this.firstDrawIndexAfterBlur > 0) {
                this.executeDrawRange(
                        () -> "GUI before blur", mainRenderTarget, dynamicTransforms, 0, Math.min(this.firstDrawIndexAfterBlur, this.draws.size())
                );
            }

            if (this.draws.size() > this.firstDrawIndexAfterBlur) {
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainRenderTarget.getDepthTexture(), 0.0);
                minecraft.gameRenderer.processBlurEffect();
                this.executeDrawRange(() -> "GUI after blur", mainRenderTarget, dynamicTransforms, this.firstDrawIndexAfterBlur, this.draws.size());
            }
        }
    }

    private void executeDrawRange(Supplier<String> label, RenderTarget mainRenderTarget, GpuBufferSlice dynamicTransforms, int startIndex, int endIndex) {
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        label,
                        mainRenderTarget.getColorTextureView(),
                        Optional.empty(),
                        mainRenderTarget.useDepth ? mainRenderTarget.getDepthTextureView() : null,
                        OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            for (int i = startIndex; i < endIndex; i++) {
                Draw draw = this.draws.get(i);
                this.executeDraw(draw, renderPass);
            }
        }
    }

    private void addElementToMesh(GuiElementRenderState elementState) {
        RenderPipeline pipeline = elementState.pipeline();
        TextureSetup textureSetup = elementState.textureSetup();
        ScreenRectangle scissorArea = elementState.scissorArea();
        if (this.previousDraw == null
                || pipeline != this.previousPipeline
                || this.scissorChanged(scissorArea, this.previousScissorArea)
                || !textureSetup.equals(this.previousTextureSetup)) {
            this.previousPipeline = pipeline;
            this.previousTextureSetup = textureSetup;
            this.previousScissorArea = scissorArea;
            this.previousDraw = this.vertexBuffer.appendDraw(pipeline.getVertexFormatBinding(0), pipeline.getPrimitiveTopology());
            this.draws.add(new Draw(this.previousDraw, pipeline, textureSetup, scissorArea));
        }

        elementState.buildVertices(this.vertexBuffer.getVertexBuilder(Objects.requireNonNull(this.previousDraw)));
    }

    private void prepareText() {
        this.renderState.forEachText(text -> {
            final Matrix3x2fc pose = text.pose;
            final ScreenRectangle scissor = text.scissor;
            text.ensurePrepared().visit(new Font.GlyphVisitor() {
                @Override
                public void acceptRenderable(TextRenderable renderable) {
                    renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, renderable, scissor));
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
                                                .computeIfAbsent(itemStackRenderState.getModelIdentity(), var0 -> new OversizedItemRenderer());
                                        ScreenRectangle actualItemBounds = itemState.oversizedItemBounds();
                                        OversizedItemRenderState oversizedItemRenderState = new OversizedItemRenderState(
                                                itemState, actualItemBounds.left(), actualItemBounds.top(), actualItemBounds.right(), actualItemBounds.bottom()
                                        );
                                        oversizedItemRenderer.prepare(oversizedItemRenderState, this.renderState, this.featureRenderDispatcher, guiScale);
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
        }

        int newTextureSize = GuiItemAtlas.computeTextureSizeFor(slotTextureSize, itemsInFrame.size());
        if (this.itemAtlas != null && this.itemAtlas.textureSize() == newTextureSize) {
            LOGGER.warn(
                    "Too many items ({}) in UI, some will be skipped! (Reached maximum texture size {}x{})", itemsInFrame.size(), newTextureSize, newTextureSize
            );
            return this.itemAtlas;
        }

        if (this.itemAtlas != null) {
            this.itemAtlas.close();
        }

        this.itemAtlas = new GuiItemAtlas(this.featureRenderDispatcher, newTextureSize, slotTextureSize);
        return this.itemAtlas;
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

    private void executeDraw(Draw draw, RenderPass renderPass) {
        StagedVertexBuffer.ExecuteInfo executeInfo = this.vertexBuffer.getExecuteInfo(draw.draw);
        if (executeInfo != null) {
            RenderPipeline pipeline = draw.pipeline();
            renderPass.setPipeline(pipeline);
            renderPass.setVertexBuffer(0, executeInfo.vertexBuffer().slice());
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

            renderPass.setIndexBuffer(executeInfo.indexBuffer(), executeInfo.indexType());
            renderPass.drawIndexed(executeInfo.indexCount(), 1, executeInfo.firstIndex(), executeInfo.baseVertex(), 0);
        }
    }

    private boolean scissorChanged(ScreenRectangle newScissor, ScreenRectangle oldScissor) {
        if (newScissor == oldScissor) {
            return false;
        } else {
            return newScissor == null || !newScissor.equals(oldScissor);
        }
    }

    private boolean enableScissor(ScreenRectangle rectangle, RenderPass renderPass) {
        WindowRenderState window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.toFramebufferScissor(
                rectangle.left(),
                rectangle.top(),
                rectangle.width(),
                rectangle.height(),
                LuminRenderSystem.getGuiScale(),
                window.height
        );
        return ScissorUtils.enableScissor(renderPass, scissor);
    }

    public void registerPanoramaTextures(TextureManager textureManager) {
        this.cubeMap.registerTextures(textureManager);
    }

    @Override
    public void close() {
        this.vertexBuffer.close();
        if (this.itemAtlas != null) {
            this.itemAtlas.close();
            this.itemAtlas = null;
        }

        this.guiProjectionMatrixBuffer.close();
        this.oversizedItemRenderers.values().forEach(PictureInPictureRenderer::close);
        this.cubeMap.close();
    }

    private record Draw(
            StagedVertexBuffer.Draw draw,
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            ScreenRectangle scissorArea
    ) {
    }

}
