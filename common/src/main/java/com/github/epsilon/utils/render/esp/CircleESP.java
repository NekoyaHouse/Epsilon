package com.github.epsilon.utils.render.esp;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class CircleESP {

    private static final RenderPipeline TRIANGLE_STRIP_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/triangle_strip"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
            .build();

    private static final RenderPipeline TRIANGLE_STRIP_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/triangle_strip"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
            .build();

    private static final RenderPipeline CIRCLE_LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/circle_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderPipeline CIRCLE_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/circle_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /**
     * 在目标周围渲染圆形 ESP。
     *
     * @param poseStack   渲染姿态栈
     * @param target      目标实体
     * @param radius      爆炸或特效半径
     * @param sideColor   填充面颜色
     * @param lineColor   轮廓线颜色
     * @param alphaFactor 透明度系数
     */
    public static void render(PoseStack poseStack, LivingEntity target, float radius, Color sideColor, Color lineColor, float alphaFactor) {
        boolean canSee = mc.player.hasLineOfSight(target);

        float ticks = (float) (System.currentTimeMillis() % 1000000) * 0.004f;
        float alpha = 0.35f + 0.65f * ((Mth.sin(ticks * 1.8f) + 1.0f) * 0.5f) * alphaFactor;

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        double x = Mth.lerp(tickDelta, target.xo, target.getX()) - mc.getEntityRenderDispatcher().camera.position().x;
        double y = Mth.lerp(tickDelta, target.yo, target.getY()) - mc.getEntityRenderDispatcher().camera.position().y + Math.sin(ticks) + 1;
        double z = Mth.lerp(tickDelta, target.zo, target.getZ()) - mc.getEntityRenderDispatcher().camera.position().z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        Matrix4f matrix = poseStack.last().pose();

        RenderPipeline triPipeline = canSee ? TRIANGLE_STRIP_PIPELINE : TRIANGLE_STRIP_NO_DEPTH_PIPELINE;
        LuminImmediateRenderer.PosColorTriangleStrip triBuilder = LuminImmediateRenderer.beginPosColorTriangleStrip(triPipeline);

        for (float i = 0; i <= (Math.PI * 2); i += ((float) Math.PI * 2) / 64.F) {
            float vecX = (float) (radius * Math.cos(i));
            float vecZ = (float) (radius * Math.sin(i));

            triBuilder.vertex(matrix, vecX, (float) (-Math.sin(ticks + 1) / 2.7f), vecZ, new Color(sideColor.getAlpha() / 255.0f, sideColor.getGreen() / 255.0f, sideColor.getBlue() / 255.0f, 0.0f).getRGB());
            triBuilder.vertex(matrix, vecX, 0, vecZ, new Color(sideColor.getAlpha() / 255.0f, sideColor.getGreen() / 255.0f, sideColor.getBlue() / 255.0f, 0.52f * alpha).getRGB());
        }

        triBuilder.end();

        RenderPipeline linePipeline = canSee ? CIRCLE_LINES_PIPELINE : CIRCLE_LINES_NO_DEPTH_PIPELINE;
        LuminImmediateRenderer.Lines lineBuilder = LuminImmediateRenderer.beginLines(linePipeline);
        PoseStack.Pose entry = poseStack.last();

        for (int i = 0; i <= 180; i++) {
            float radAngle = (float) (i * Math.PI * 2 / 90);
            float nextAngle = (float) ((i + 1) * Math.PI * 2 / 90);

            float[] nextPoint = getPoint(nextAngle, radius);
            float[] linePoint = getPoint(radAngle, radius);
            float[] normal = getNormal(radAngle);

            lineBuilder.vertex(matrix, entry, linePoint[0], 0f, linePoint[1], new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), Math.round(lineColor.getAlpha() * alpha)).getRGB(), normal[0], 0f, normal[1], 2f);
            lineBuilder.vertex(matrix, entry, nextPoint[0], 0f, nextPoint[1], new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), Math.round(lineColor.getAlpha() * alpha)).getRGB(), normal[0], 0f, normal[1], 2f);
        }

        lineBuilder.end();

        poseStack.popPose();
    }

    private static float[] getPoint(float radAngle, float radius) {
        return new float[]{(float) (-Math.sin(radAngle) * radius), (float) (Math.cos(radAngle) * radius)};
    }

    private static float[] getNormal(float radAngle) {
        return new float[]{(float) -Math.cos(radAngle), (float) -Math.sin(radAngle)};
    }

}
