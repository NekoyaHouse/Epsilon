package com.github.epsilon.modules.impl.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;

public class Hat extends Module {

    public static final Hat INSTANCE = new Hat();

    private Hat() {
        super("Hat", Category.RENDER);
    }

    private enum Mode {
        Astolfo,
        Sexy,
        Fade
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Sexy);
    private final IntSetting points = intSetting("Points", 30, 3, 180, 1);
    private final DoubleSetting size = doubleSetting("Size", 0.5, 0.1, 3.0, 0.1);
    private final DoubleSetting offset = doubleSetting("Offset", 2000.0, 0.0, 5000.0, 100.0);
    private final ColorSetting color = colorSetting("Color", new Color(255, 255, 255), () -> mode.is(Mode.Fade));
    private final ColorSetting secondColor = colorSetting("Second Color", new Color(0, 0, 0), () -> mode.is(Mode.Fade));
    private final BoolSetting onlyThirdPerson = boolSetting("Only Third Person", true);

    private final double[][] positions = new double[181][2];
    private int lastPoints;
    private double lastSize;

    private static final RenderPipeline HAT_CONE_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/hat_cone"))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN)
            .build();

    private static final RenderPipeline HAT_OUTLINE_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/hat_outline"))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private void computeChineseHatPoints(int points, double radius) {
        for (int i = 0; i <= points; i++) {
            double circleX = radius * StrictMath.cos(i * Math.PI * 2 / points);
            double circleZ = radius * StrictMath.sin(i * Math.PI * 2 / points);
            this.positions[i][0] = circleX;
            this.positions[i][1] = circleZ;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        if (this.lastSize != this.size.getValue() || this.lastPoints != this.points.getValue()) {
            this.lastSize = this.size.getValue();
            this.lastPoints = this.points.getValue();
            this.computeChineseHatPoints(this.lastPoints, this.lastSize);
        }

        drawHat(event.getPoseStack(), mc.player);
    }


    public void drawHat(PoseStack stack, Player player) {
        if (player == mc.player && mc.options.getCameraType().isFirstPerson() && onlyThirdPerson.getValue()) {
            return;
        }

        int pointCount = this.points.getValue();
        double radius = this.size.getValue();

        Color[] colors = new Color[181];
        Color[] colorMode = getColorMode();

        for (int i = 0; i < colors.length; ++i) {
            colors[i] = this.fadeBetween(colorMode, this.offset.getValue(), (double) i * ((double) this.offset.getValue() / this.points.getValue()));
        }

        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double x = Mth.lerp(tickDelta, player.xOld, player.getX()) - camera.x;
        double y = Mth.lerp(tickDelta, player.yOld, player.getY()) - camera.y;
        double z = Mth.lerp(tickDelta, player.zOld, player.getZ()) - camera.z;

        stack.pushPose();

        stack.translate(x, y + 1.9, z);

        if (player.isCrouching()) {
            stack.translate(0, -0.2, 0);
        }

        float yaw = Mth.lerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        stack.mulPose(Axis.YN.rotationDegrees(yaw));

        float pitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());
        stack.mulPose(Axis.XP.rotationDegrees(pitch / 3.0f));
        stack.translate(0, 0, pitch / 270.0);

        Matrix4f matrix = stack.last().pose();
        PoseStack.Pose entry = stack.last();

        float lineWidth = 2.0f;
        LuminImmediateRenderer.Lines outlineBuffer = LuminImmediateRenderer.beginLines(HAT_OUTLINE_PIPELINE);

        for (int i = 0; i < pointCount; i++) {
            int next = (i + 1) % pointCount;

            double[] p1 = this.positions[i];
            double[] p2 = this.positions[next];

            float dx = (float) (p2[0] - p1[0]);
            float dz = (float) (p2[1] - p1[1]);
            float len = Mth.sqrt(dx * dx + dz * dz);
            float nx = len == 0.0f ? 1.0f : dx / len;
            float nz = len == 0.0f ? 0.0f : dz / len;

            Color c1 = colors[i];
            Color c2 = colors[next];

            outlineBuffer.vertex(matrix, entry, (float) p1[0], 0.0f, (float) p1[1], withAlpha(c1, 255).getRGB(), nx, 0.0f, nz, lineWidth);
            outlineBuffer.vertex(matrix, entry, (float) p2[0], 0.0f, (float) p2[1], withAlpha(c2, 255).getRGB(), nx, 0.0f, nz, lineWidth);
        }
        outlineBuffer.end();

        LuminImmediateRenderer.PosColorTriangleFan coneBuffer = LuminImmediateRenderer.beginPosColorTriangleFan(HAT_CONE_PIPELINE);

        coneBuffer.vertex(matrix, 0, (float) (radius / 2), 0, new Color(255, 255, 255, 128).getRGB());

        for (int i = 0; i <= pointCount; i++) {
            double[] pos = this.positions[i % pointCount];
            Color clr = colors[i % colors.length];
            coneBuffer.vertex(matrix, (float) pos[0], 0, (float) pos[1], withAlpha(clr, 128).getRGB());
        }
        coneBuffer.end();

        stack.popPose();
    }

    private Color withAlpha(Color source, int alpha) {
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), Mth.clamp(alpha, 0, 255));
    }

    private Color[] getColorMode() {
        return switch (this.mode.getValue()) {
            case Astolfo -> new Color[]{
                    new Color(252, 106, 140), new Color(252, 106, 213),
                    new Color(218, 106, 252), new Color(145, 106, 252),
                    new Color(106, 140, 252), new Color(106, 213, 252),
                    new Color(106, 213, 252), new Color(106, 140, 252),
                    new Color(145, 106, 252), new Color(218, 106, 252),
                    new Color(252, 106, 213), new Color(252, 106, 140)
            };
            case Sexy -> new Color[]{
                    new Color(255, 150, 255), new Color(255, 132, 199),
                    new Color(211, 101, 187), new Color(160, 80, 158),
                    new Color(120, 63, 160), new Color(123, 65, 168),
                    new Color(104, 52, 152), new Color(142, 74, 175),
                    new Color(160, 83, 179), new Color(255, 110, 189),
                    new Color(255, 150, 255)
            };
            case Fade -> new Color[]{
                    this.color.getValue(),
                    this.secondColor.getValue(),
                    this.color.getValue()
            };
        };
    }

    public Color fadeBetween(Color[] table, double speed, double offset) {
        return this.fadeBetween(table, (System.currentTimeMillis() + offset) % speed / speed);
    }

    public Color fadeBetween(Color[] table, double progress) {
        int i = table.length;
        if (progress == 1.0) {
            return table[0];
        }
        if (progress == 0.0) {
            return table[i - 1];
        }
        double max = Math.max(0.0, (1.0 - progress) * (i - 1));
        int min = (int) max;
        return this.fadeBetween(table[min], table[min + 1], max - min);
    }

    public Color fadeBetween(Color start, Color end, double progress) {
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return this.gradient(start, end, progress);
    }

    public Color gradient(Color start, Color end, double progress) {
        double invert = 1.0 - progress;
        return new Color(
                (int) (start.getRed() * invert + end.getRed() * progress),
                (int) (start.getGreen() * invert + end.getGreen() * progress),
                (int) (start.getBlue() * invert + end.getBlue() * progress),
                (int) (start.getAlpha() * invert + end.getAlpha() * progress)
        );
    }

}
