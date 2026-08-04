package com.github.epsilon.utils.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class WireframeEntityRenderer {

    private static final PoseStack modelPoseStack = new PoseStack();
    private static final WireframeSubmitNodeStorage submitNodeStorage = new WireframeSubmitNodeStorage();
    private static final WireframeVertexConsumer vertexConsumer = new WireframeVertexConsumer();

    private static final RenderPipeline SIDES_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/wireframe_entity_sides"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/wireframe_entity_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static Color sideColor = Color.WHITE;
    private static Color lineColor = Color.WHITE;
    private static float lineWidth = 1.0f;

    private static LuminImmediateRenderer.PosColorQuads sidesBuilder;
    private static LuminImmediateRenderer.Lines linesBuilder;

    private static Matrix4f drawMatrix;
    private static PoseStack.Pose linePose;

    private static double offsetX;
    private static double offsetY;
    private static double offsetZ;

    private WireframeEntityRenderer() {
    }

    public static void beginBatch(PoseStack renderStack) {
        if (isBatching()) {
            throw new IllegalStateException("Wireframe entity renderer is already batching");
        }
        beginDraw(renderStack);
    }

    public static void endBatch() {
        if (isBatching()) endDraw();
    }

    public static void render(PoseStack renderStack, Entity entity, double scale, Color sideColor, Color lineColor, float lineWidth) {
        boolean startedBatch = false;
        if (!isBatching()) {
            beginBatch(renderStack);
            startedBatch = true;
        }

        renderEntity(entity, scale, sideColor, lineColor, lineWidth);

        if (startedBatch) {
            endBatch();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderEntity(Entity entity, double scale, Color sideColor, Color lineColor, float lineWidth) {
        WireframeEntityRenderer.sideColor = sideColor;
        WireframeEntityRenderer.lineColor = lineColor;
        WireframeEntityRenderer.lineWidth = lineWidth;

        float tickDelta = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        offsetX = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        offsetY = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        offsetZ = Mth.lerp(tickDelta, entity.zOld, entity.getZ());

        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState state = renderer.createRenderState(entity, tickDelta);

        Vec3 renderOffset = renderer.getRenderOffset(state);
        offsetX += renderOffset.x;
        offsetY += renderOffset.y;
        offsetZ += renderOffset.z;

        modelPoseStack.pushPose();
        modelPoseStack.scale((float) scale, (float) scale, (float) scale);
        renderer.submit(state, modelPoseStack, submitNodeStorage, mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState);
        modelPoseStack.popPose();
        vertexConsumer.reset();
        submitNodeStorage.getSubmitsPerOrder().clear();
    }

    private static boolean isBatching() {
        return sidesBuilder != null && linesBuilder != null;
    }

    private static void beginDraw(PoseStack renderStack) {
        PoseStack.Pose pose = renderStack.last();
        drawMatrix = pose.pose();
        linePose = pose;
        sidesBuilder = LuminImmediateRenderer.beginPosColorQuads(SIDES_PIPELINE);
        linesBuilder = LuminImmediateRenderer.beginLines(LINES_PIPELINE);
    }

    private static void endDraw() {
        if (sidesBuilder != null) {
            sidesBuilder.end();
        }
        if (linesBuilder != null) {
            linesBuilder.end();
        }

        sidesBuilder = null;
        linesBuilder = null;
        drawMatrix = null;
        linePose = null;
    }

    private static void drawQuad(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        Vec3 cameraPos = mc.getEntityRenderDispatcher().camera.position();
        float rx1 = (float) (offsetX + x1 - cameraPos.x);
        float ry1 = (float) (offsetY + y1 - cameraPos.y);
        float rz1 = (float) (offsetZ + z1 - cameraPos.z);
        float rx2 = (float) (offsetX + x2 - cameraPos.x);
        float ry2 = (float) (offsetY + y2 - cameraPos.y);
        float rz2 = (float) (offsetZ + z2 - cameraPos.z);
        float rx3 = (float) (offsetX + x3 - cameraPos.x);
        float ry3 = (float) (offsetY + y3 - cameraPos.y);
        float rz3 = (float) (offsetZ + z3 - cameraPos.z);
        float rx4 = (float) (offsetX + x4 - cameraPos.x);
        float ry4 = (float) (offsetY + y4 - cameraPos.y);
        float rz4 = (float) (offsetZ + z4 - cameraPos.z);

        if (sidesBuilder != null) {
            sidesBuilder.vertex(drawMatrix, rx1, ry1, rz1, sideColor.getRGB());
            sidesBuilder.vertex(drawMatrix, rx2, ry2, rz2, sideColor.getRGB());
            sidesBuilder.vertex(drawMatrix, rx3, ry3, rz3, sideColor.getRGB());
            sidesBuilder.vertex(drawMatrix, rx4, ry4, rz4, sideColor.getRGB());
        }

        if (linesBuilder != null) {
            drawLine(rx1, ry1, rz1, rx2, ry2, rz2);
            drawLine(rx2, ry2, rz2, rx3, ry3, rz3);
            drawLine(rx3, ry3, rz3, rx4, ry4, rz4);
            drawLine(rx4, ry4, rz4, rx1, ry1, rz1);
        }
    }

    private static void drawLine(float x1, float y1, float z1, float x2, float y2, float z2) {
        Vector3f normal = getNormal(x1, y1, z1, x2, y2, z2);
        linesBuilder.vertex(drawMatrix, linePose, x1, y1, z1, lineColor.getRGB(), normal.x, normal.y, normal.z, lineWidth);
        linesBuilder.vertex(drawMatrix, linePose, x2, y2, z2, lineColor.getRGB(), normal.x, normal.y, normal.z, lineWidth);
    }

    private static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        if (normalSqrt <= 1.0E-5f) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    private static final class WireframeSubmitNodeStorage extends SubmitNodeStorage {
        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor, TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
            model.setupAnim(state);
            model.renderToBuffer(poseStack, vertexConsumer, lightCoords, overlayCoords, tintedColor);
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
            customGeometryRenderer.render(poseStack.last(), vertexConsumer);
        }
    }

    private static final class WireframeVertexConsumer implements VertexConsumer {
        private final float[] xs = new float[4];
        private final float[] ys = new float[4];
        private final float[] zs = new float[4];
        private int index;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            xs[index] = x;
            ys[index] = y;
            zs[index] = z;
            index++;

            if (index == 4) {
                drawQuad(
                        xs[0], ys[0], zs[0],
                        xs[1], ys[1], zs[1],
                        xs[2], ys[2], zs[2],
                        xs[3], ys[3], zs[3]
                );
                index = 0;
            }

            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        private void reset() {
            index = 0;
        }
    }

}
