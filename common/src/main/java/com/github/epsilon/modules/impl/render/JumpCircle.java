package com.github.epsilon.modules.impl.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.timer.TimerUtils;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class JumpCircle extends Module {

    public static final JumpCircle INSTANCE = new JumpCircle();

    private JumpCircle() {
        super("Jump Circle", Category.RENDER);
    }

    private enum Mode {
        Default,
        Portal
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Default);
    private final BoolSetting easeOut = boolSetting("Ease Out", true);
    private final DoubleSetting rotateSpeed = doubleSetting("Rotate Speed", 2.0, 0.5, 5.0, 0.1);
    private final DoubleSetting circleScale = doubleSetting("Circle Scale", 1.0, 0.5, 5.0, 0.1);
    private final BoolSetting onlySelf = boolSetting("Only Self", false);

    private final List<Circle> circles = new ArrayList<>();
    private final List<UUID> groundedCache = new ArrayList<>();

    private static final Identifier BUBBLE_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/hitbubble.png");
    private static final Identifier CIRCLE_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/circle.png");

    private static final RenderPipeline JUMP_CIRCLE_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_jump_circle")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    @Override
    protected void onDisable() {
        circles.clear();
        groundedCache.clear();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        for (Player player : mc.level.players()) {
            if (!shouldTrack(player)) {
                groundedCache.remove(player.getUUID());
                continue;
            }

            UUID id = player.getUUID();
            if (player.onGround()) {
                if (!groundedCache.contains(id)) {
                    groundedCache.add(id);
                }
            } else if (groundedCache.remove(id)) {
                circles.add(new Circle(new Vec3(player.getX(), Math.floor(player.getY()) + 0.001, player.getZ()), new TimerUtils()));
            }
        }

        circles.removeIf(c -> c.timer.passedMillise(easeOut.getValue() ? 5000 : 6000));
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (circles.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Identifier texture = mode.is(Mode.Portal) ? BUBBLE_TEXTURE : CIRCLE_TEXTURE;
        LuminImmediateRenderer.PosTexColorQuads renderer = LuminImmediateRenderer.beginPosTexColorQuads(JUMP_CIRCLE_PIPELINE, texture);

        Collections.reverse(circles);
        for (Circle circle : circles) {
            renderCircle(poseStack, renderer, circle);
        }
        Collections.reverse(circles);

        renderer.end();
    }

    private boolean shouldTrack(Player player) {
        return player != null && player.isAlive() && (!onlySelf.getValue() || player == mc.player);
    }

    private void renderCircle(PoseStack poseStack, LuminImmediateRenderer.PosTexColorQuads renderer, Circle circle) {
        float colorAnim = (float) (circle.timer.getMs()) / 6000f;
        float sizeAnim = circleScale.getValue().floatValue() - (float) Math.pow(1 - ((circle.timer.getMs() * (easeOut.getValue() ? 2f : 1f)) / 5000f), 4);

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 pos = circle.pos();

        poseStack.pushPose();
        poseStack.translate(pos.x - camera.position().x, pos.y - camera.position().y, pos.z - camera.position().z);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sizeAnim * rotateSpeed.getValue().floatValue() * 1000.0f));

        float scale = sizeAnim * 2.0f;
        Matrix4f matrix = poseStack.last().pose();

        renderer.vertex(matrix, -sizeAnim, -sizeAnim + scale, 0.0f, 0.0f, 1.0f, applyOpacity(syncColor(270), 1.0f - colorAnim).getRGB());
        renderer.vertex(matrix, -sizeAnim + scale, -sizeAnim + scale, 0.0f, 1.0f, 1.0f, applyOpacity(syncColor(0), 1.0f - colorAnim).getRGB());
        renderer.vertex(matrix, -sizeAnim + scale, -sizeAnim, 0.0f, 1.0f, 0.0f, applyOpacity(syncColor(180), 1.0f - colorAnim).getRGB());
        renderer.vertex(matrix, -sizeAnim, -sizeAnim, 0.0f, 0.0f, 0.0f, applyOpacity(syncColor(90), 1.0f - colorAnim).getRGB());

        poseStack.popPose();
    }

    private Color syncColor(int offset) {
        float hue = Mth.frac((System.currentTimeMillis() + offset * 20L) / 4500.0f);
        Color rainbow = Color.getHSBColor(hue, 0.65f, 1.0f);
        return new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), 255);
    }

    private Color applyOpacity(Color color, float opacity) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * Mth.clamp(opacity, 0.0f, 1.0f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private record Circle(Vec3 pos, TimerUtils timer) {
    }

}
