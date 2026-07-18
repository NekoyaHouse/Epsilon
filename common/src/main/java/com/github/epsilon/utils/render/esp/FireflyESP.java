package com.github.epsilon.utils.render.esp;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.utils.render.ColorUtils;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.function.Function;

import static com.github.epsilon.Constants.mc;

public class FireflyESP {

    public enum ColorMode {
        Solid,
        Blend,
        Rainbow
    }

    private static final Identifier FIREFLY_TEX = ResourceLocationUtils.getIdentifier("textures/particles/firefly.png");

    private static final RenderPipeline TARGET_ICON_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_target_icon")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderPipeline TARGET_ICON_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_target_icon")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final Function<RenderPipeline, RenderType> TARGET_ICON_LAYER = Util.memoize(
            renderPipeline -> RenderType.create("epsilon_target_icon", RenderSetup.builder(renderPipeline)
                    .withTexture("Sampler0", FIREFLY_TEX)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup())
    );

    public static void render(PoseStack stack, LivingEntity target, int espLength, int factor, double shaking, double amplitude, Color color, ColorMode colorMode, Color secondColor, double colorMix, double colorSpeed, double rainbowSpeed, double rainbowSaturation, double rainbowBrightness) {
        boolean canSee = mc.player.hasLineOfSight(target);

        Camera camera = mc.gameRenderer.getMainCamera();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        double tPosX = Mth.lerp(tickDelta, target.xOld, target.getX()) - camera.position().x;
        double tPosY = Mth.lerp(tickDelta, target.yOld, target.getY()) - camera.position().y;
        double tPosZ = Mth.lerp(tickDelta, target.zOld, target.getZ()) - camera.position().z;
        float iAge = (float) (target.tickCount - 1) + tickDelta;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= espLength; i++) {
                double radians = Math.toRadians((((float) i / 1.5f + iAge) * factor + (j * 120)) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(iAge * 2.5f + i * (j + 1)) * amplitude) / shaking;

                float offset = (float) i / (float) espLength;

                stack.pushPose();
                stack.translate(tPosX + Math.cos(radians) * target.getBbWidth(), tPosY + target.getBbHeight() * 0.5 + sinQuad, tPosZ + Math.sin(radians) * target.getBbWidth());
                stack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
                stack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));

                Matrix4f matrix = stack.last().pose();

                float scale = Math.max(0.24f * (offset), 0.2f);

                int renderColor = resolveColor(
                        iAge,
                        i,
                        j,
                        espLength,
                        colorMode,
                        color,
                        secondColor,
                        (float) colorMix,
                        (float) colorSpeed,
                        (float) rainbowSpeed,
                        (float) rainbowSaturation,
                        (float) rainbowBrightness
                ).getRGB();

                buffer.addVertex(matrix, -scale, scale, 0).setUv(0f, 1f).setColor(renderColor);
                buffer.addVertex(matrix, scale, scale, 0).setUv(1f, 1f).setColor(renderColor);
                buffer.addVertex(matrix, scale, -scale, 0).setUv(1f, 0f).setColor(renderColor);
                buffer.addVertex(matrix, -scale, -scale, 0).setUv(0f, 0f).setColor(renderColor);

                stack.popPose();
            }
        }

        TARGET_ICON_LAYER.apply(canSee ? TARGET_ICON_PIPELINE : TARGET_ICON_NO_DEPTH_PIPELINE).draw(buffer.buildOrThrow());
    }

    private static Color resolveColor(float age, int index, int ringIndex, int espLength, ColorMode mode, Color primaryColor, Color secondaryColor, float mixAmount, float blendSpeed, float rainbowSpeed, float rainbowSaturation, float rainbowBrightness) {
        float clampedMix = Mth.clamp(mixAmount, 0.0f, 1.0f);
        float progress = espLength <= 0 ? 0.0f : (float) index / (float) espLength;

        return switch (mode) {
            case Blend -> {
                float wave = (Mth.sin((age * blendSpeed * 0.25f) + (progress * 6.2831855f) + ringIndex) + 1.0f) * 0.5f;
                float blendProgress = Mth.clamp(wave * clampedMix, 0.0f, 1.0f);
                yield ColorUtils.interpolateColor(primaryColor, secondaryColor, blendProgress);
            }
            case Rainbow -> {
                float hue = Mth.frac((age * 0.01f * rainbowSpeed) + progress + ringIndex * 0.17f);
                Color rainbow = Color.getHSBColor(hue, Mth.clamp(rainbowSaturation, 0.0f, 1.0f), Mth.clamp(rainbowBrightness, 0.0f, 1.0f));
                yield new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), primaryColor.getAlpha());
            }
            case Solid -> primaryColor;
        };
    }

}
