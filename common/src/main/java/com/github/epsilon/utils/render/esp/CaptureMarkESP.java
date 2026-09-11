package com.github.epsilon.utils.render.esp;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.github.epsilon.utils.render.ColorUtils;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class CaptureMarkESP {

    private static final Identifier CAPTUREMARK_TEX = ResourceLocationUtils.getIdentifier("textures/particles/target.png");

    private static final RenderPipeline TARGET_ICON_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_target_icon")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    /**
     * 为目标渲染旋转波形捕获标记。
     *
     * @param poseStack 渲染姿态栈
     * @param target    目标实体
     * @param espSize   特效尺寸
     * @param rotSpeed  旋转速度
     * @param waveSpeed 波形动画速度
     * @param color1    第一种颜色
     * @param color2    第二种颜色
     */
    public static void render(PoseStack poseStack, LivingEntity target, double espSize, double rotSpeed, double waveSpeed, Color color1, Color color2) {
        double timeSeconds = System.nanoTime() * 1.0E-9;
        float rotation = (float) (-((timeSeconds * rotSpeed * 60.0) % 360.0));

        Vec3 cameraPos = mc.getEntityRenderDispatcher().camera.position();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double ex = Mth.lerp(tickDelta, target.xOld, target.getX()) - cameraPos.x;
        double ey = Mth.lerp(tickDelta, target.yOld, target.getY()) - cameraPos.y;
        double ez = Mth.lerp(tickDelta, target.zOld, target.getZ()) - cameraPos.z;

        float size = (float) espSize * 0.5f;

        poseStack.pushPose();
        poseStack.translate(ex, ey + target.getBbHeight() * 0.5, ez);

        Camera camera = mc.gameRenderer.mainCamera();
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));

        LuminImmediateRenderer.PosTexColorQuads renderer = LuminImmediateRenderer.beginPosTexColorQuads(TARGET_ICON_PIPELINE, CAPTUREMARK_TEX);

        Matrix4f matrix = poseStack.last().pose();

        Color c1 = getColorForProgress(0f, waveSpeed, color1, color2, timeSeconds);
        Color c2 = getColorForProgress(0.25f, waveSpeed, color1, color2, timeSeconds);
        Color c3 = getColorForProgress(0.5f, waveSpeed, color1, color2, timeSeconds);
        Color c4 = getColorForProgress(0.75f, waveSpeed, color1, color2, timeSeconds);

        renderer.vertex(matrix, -size, -size, 0, 0, 0, c1.getRGB());
        renderer.vertex(matrix, -size, size, 0, 0, 1, c2.getRGB());
        renderer.vertex(matrix, size, size, 0, 1, 1, c3.getRGB());
        renderer.vertex(matrix, size, -size, 0, 1, 0, c4.getRGB());
        renderer.end();

        poseStack.popPose();
    }

    private static Color getColorForProgress(float progress, double waveSpeed, Color color1, Color color2, double timeSeconds) {
        float wave = (float) Math.sin((progress * Math.PI * 2.0) + (timeSeconds * waveSpeed));
        wave = (wave + 1f) / 2f;
        return ColorUtils.interpolateColor(color1, color2, wave);
    }

}
