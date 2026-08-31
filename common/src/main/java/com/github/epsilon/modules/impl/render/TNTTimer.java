package com.github.epsilon.modules.impl.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

public class TNTTimer extends Module {

    public static final TNTTimer INSTANCE = new TNTTimer();

    private TNTTimer() {
        super("TNT Timer", Category.RENDER);
    }

    private final DoubleSetting dangerRadius = doubleSetting("Danger Radius", 7.0, 0.5, 12.0, 0.1);
    private final DoubleSetting lethalRadius = doubleSetting("Lethal Radius", 4.0, 0.5, 12.0, 0.1);
    private final DoubleSetting outerThickness = doubleSetting("Outer Thickness", 0.12, 0.01, 1.0, 0.01);
    private final DoubleSetting innerThickness = doubleSetting("Inner Thickness", 0.16, 0.01, 1.0, 0.01);
    private final IntSetting alphaSetting = intSetting("Alpha", 16, 16, 255, 1);
    private final IntSetting segmentsSetting = intSetting("Segments", 360, 16, 360, 1);
    private final IntSetting ringsSetting = intSetting("Sphere Rings", 10, 2, 64, 1);
    private final BoolSetting filledSphere = boolSetting("Filled Sphere", true);
    private final BoolSetting wireframeSphere = boolSetting("Wireframe Sphere", false);
    private final BoolSetting pulse = boolSetting("Pulse Near Explosion", true);
    private final DoubleSetting pulseThresholdSec = doubleSetting("Pulse Threshold (s)", 2.0, 0.2, 10.0, 0.1);
    private final DoubleSetting pulseAmplitude = doubleSetting("Pulse Amplitude", 0.12, 0.0, 0.5, 0.01);
    private final DoubleSetting pulseSpeedHz = doubleSetting("Pulse Speed (Hz)", 2.0, 0.2, 8.0, 0.1);

    private final Supplier<Render2DScheduler> scheduler = Suppliers.memoize(Render2DScheduler::new);

    private static final RenderPipeline FILLED_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/tnt_timer_filled"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/tnt_timer_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck()) return;
        Render2DScheduler render = scheduler.get();
        render.clear();
        Render2DScheduler.LayerHandle layer = render.layer(0);
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;
            Vec3 anchor = tnt.getPosition(partial).add(0.0, tnt.getBbHeight() + 0.4, 0.0);
            org.joml.Vector3f screen = WorldToScreen.calcWorld2Screen(anchor);
            if (screen == null || !Float.isFinite(screen.x) || !Float.isFinite(screen.y)) continue;
            String label = String.format(Locale.US, "%.1fs", Math.max(0, tnt.getFuse()) / 20.0f);
            float scale = Math.max(0.45f, WorldToScreen.calcScale(anchor) * 0.7f);
            float width = render.textMetrics().getWidth(label, scale);
            layer.addText(label, screen.x - width * 0.5f, screen.y, scale, Color.WHITE, StaticFontLoader.defaultFont());
        }
        render.flushAndClear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        PoseStack stack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int segments = segmentsSetting.getValue();
        int rings = ringsSetting.getValue();
        int alpha = Mth.clamp(alphaSetting.getValue(), 0, 255);
        LuminImmediateRenderer.Lines lines = LuminImmediateRenderer.beginLines(LINES_PIPELINE);
        LuminImmediateRenderer.PosColorQuads quads = filledSphere.getValue() ? LuminImmediateRenderer.beginPosColorQuads(FILLED_PIPELINE) : null;
        Matrix4f matrix = stack.last().pose();
        PoseStack.Pose pose = stack.last();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;
            float fuse = tnt.getFuse();
            float norm = Mth.clamp(fuse / 80.0f, 0.0f, 1.0f);
            int color = fuseColor(norm, alpha);
            float scale = pulseScale(fuse, pt);
            double x = Mth.lerp(pt, tnt.xOld, tnt.getX()) - camera.x;
            double y = Mth.lerp(pt, tnt.yOld, tnt.getY()) - camera.y;
            double z = Mth.lerp(pt, tnt.zOld, tnt.getZ()) - camera.z;
            drawRing(lines, matrix, pose, x, y + 0.01, z, lethalRadius.getValue().floatValue(), innerThickness.getValue().floatValue(), segments, (alpha << 24) | 0xFF301F);
            drawRing(lines, matrix, pose, x, y + 0.01, z, dangerRadius.getValue().floatValue() * scale, outerThickness.getValue().floatValue(), segments, color);
            if (quads != null) {
                float radius = dangerRadius.getValue().floatValue() * scale;
                drawSphere(quads, matrix, x, y + tnt.getBbHeight() * 0.5, z, radius, rings, segments, color);
                drawSphere(quads, matrix, x, y + tnt.getBbHeight() * 0.5, z, radius * 0.96f, rings, segments, withAlpha(color, Mth.clamp((int) (alpha * 0.6f), 0, 255)));
                drawSphere(quads, matrix, x, y + tnt.getBbHeight() * 0.5, z, radius * 0.92f, rings, segments, withAlpha(color, Mth.clamp((int) (alpha * 0.35f), 0, 255)));
            }
            if (wireframeSphere.getValue())
                drawWireSphere(lines, matrix, pose, x, y + tnt.getBbHeight() * 0.5, z, dangerRadius.getValue().floatValue() * scale, rings, segments, color);
        }

        lines.end();
        if (quads != null) quads.end();
    }

    private float pulseScale(float fuse, float pt) {
        if (!pulse.getValue()) return 1.0f;
        float fade = Mth.clamp(1.0f - (fuse / 20.0f) / pulseThresholdSec.getValue().floatValue(), 0.0f, 1.0f);
        float phase = (mc.level.getGameTime() + pt) / 20.0f * pulseSpeedHz.getValue().floatValue() * Mth.TWO_PI;
        return 1.0f + Mth.sin(phase) * pulseAmplitude.getValue().floatValue() * fade;
    }

    private static int fuseColor(float norm, int alpha) {
        int red = (alpha << 24) | 0xFF3B1F;
        int yellow = (alpha << 24) | 0xFFD400;
        int green = (alpha << 24) | 0x42FF66;
        return lerp(norm < 0.5f ? red : yellow, norm < 0.5f ? yellow : green, norm < 0.5f ? norm * 2.0f : (norm - 0.5f) * 2.0f);
    }

    private static int lerp(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return ((int) Mth.lerp(t, aa, ba) << 24) | ((int) Mth.lerp(t, ar, br) << 16) | ((int) Mth.lerp(t, ag, bg) << 8) | (int) Mth.lerp(t, ab, bb);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static void drawRing(LuminImmediateRenderer.Lines out, Matrix4f m, PoseStack.Pose pose, double x, double y, double z, float radius, float thickness, int segments, int color) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0 / segments, a1 = (i + 1) * Math.PI * 2.0 / segments;
            float x0 = (float) (x + Math.cos(a0) * radius), z0 = (float) (z + Math.sin(a0) * radius);
            float x1 = (float) (x + Math.cos(a1) * radius), z1 = (float) (z + Math.sin(a1) * radius);
            float dx = x1 - x0, dz = z1 - z0, len = Mth.sqrt(dx * dx + dz * dz);
            out.vertex(m, pose, x0, (float) y, z0, color, dx / len, 0, dz / len, thickness);
            out.vertex(m, pose, x1, (float) y, z1, color, dx / len, 0, dz / len, thickness);
        }
    }

    private static void drawSphere(LuminImmediateRenderer.PosColorQuads out, Matrix4f m, double x, double y, double z, float radius, int rings, int segments, int color) {
        for (int r = 0; r < rings; r++) {
            double p0 = Math.PI * r / rings - Math.PI / 2.0, p1 = Math.PI * (r + 1) / rings - Math.PI / 2.0;
            for (int s = 0; s < segments; s++) {
                double a0 = Math.PI * 2.0 * s / segments, a1 = Math.PI * 2.0 * (s + 1) / segments;
                float[] v0 = sphereVertex(x, y, z, radius, p0, a0), v1 = sphereVertex(x, y, z, radius, p0, a1), v2 = sphereVertex(x, y, z, radius, p1, a1), v3 = sphereVertex(x, y, z, radius, p1, a0);
                out.vertex(m, v0[0], v0[1], v0[2], color);
                out.vertex(m, v1[0], v1[1], v1[2], color);
                out.vertex(m, v2[0], v2[1], v2[2], color);
                out.vertex(m, v3[0], v3[1], v3[2], color);
            }
        }
    }

    private static void drawWireSphere(LuminImmediateRenderer.Lines out, Matrix4f m, PoseStack.Pose pose, double x, double y, double z, float radius, int rings, int segments, int color) {
        for (int r = 1; r < rings; r++) {
            double p = Math.PI * r / rings - Math.PI / 2.0, h = Math.cos(p) * radius, yy = y + Math.sin(p) * radius;
            drawRing(out, m, pose, x, yy, z, (float) h, 1.0f, segments, color);
        }
        for (int s = 0; s < segments; s++) {
            double a = Math.PI * 2.0 * s / segments;
            float px = (float) (x + Math.cos(a) * radius), pz = (float) (z + Math.sin(a) * radius);
            out.vertex(m, pose, px, (float) (y - radius), pz, color, 0, 1, 0, 1.0f);
            out.vertex(m, pose, px, (float) (y + radius), pz, color, 0, 1, 0, 1.0f);
        }
    }

    private static float[] sphereVertex(double x, double y, double z, float radius, double pitch, double yaw) {
        float cp = (float) Math.cos(pitch);
        return new float[]{(float) (x + radius * cp * Math.cos(yaw)), (float) (y + radius * Math.sin(pitch)), (float) (z + radius * cp * Math.sin(yaw))};
    }

}
