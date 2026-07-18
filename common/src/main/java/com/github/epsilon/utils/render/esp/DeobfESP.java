package com.github.epsilon.utils.render.esp;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.utils.render.animation.Easing;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.epsilon.Constants.mc;

public final class DeobfESP {

    private static final Identifier TEXTURE = ResourceLocationUtils.getIdentifier("textures/hud/deobf.png");

    private static final float TEXTURE_ASPECT_RATIO = 751.0f / 376.0f;
    private static final long INTRO_DURATION_MS = 1600L;
    private static final long KILL_DURATION_MS = 1800L;
    private static final float CONTINUOUS_ROTATION_SPEED = 180.0f;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_deobf_esp")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderType LAYER = RenderType.create("epsilon_deobf_esp", RenderSetup.builder(PIPELINE)
            .withTexture("Sampler0", TEXTURE)
            .sortOnUpload()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.MAIN_TARGET)
            .createRenderSetup());

    private static final Map<Integer, Effect> EFFECTS = new LinkedHashMap<>();

    private DeobfESP() {
    }

    public static void markHit(LivingEntity target) {
        if (target == null || target.isDeadOrDying()) return;

        long now = System.currentTimeMillis();
        Effect effect = EFFECTS.get(target.getId());
        if (effect == null || effect.target != target || effect.killedAtMs >= 0L) {
            EFFECTS.put(target.getId(), new Effect(target, now));
        }
    }

    public static void clear() {
        EFFECTS.clear();
    }

    public static void retainRisingEffects() {
        if (EFFECTS.isEmpty()) return;
        if (mc.level == null) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Iterator<Effect> iterator = EFFECTS.values().iterator();

        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            LivingEntity target = effect.target;

            if (target.level() != mc.level) {
                iterator.remove();
            } else if (effect.killedAtMs < 0L) {
                if (target.isDeadOrDying()) {
                    effect.beginKill(now, partialTick);
                } else {
                    iterator.remove();
                }
            }
        }
    }

    public static void render(PoseStack poseStack, float size, float spins, float wobble, float flyHeight) {
        if (EFFECTS.isEmpty()) return;
        if (mc.level == null) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Camera camera = mc.gameRenderer.getMainCamera();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Iterator<Effect> iterator = EFFECTS.values().iterator();

        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            LivingEntity target = effect.target;

            if (target.level() != mc.level) {
                iterator.remove();
                continue;
            }

            if (effect.killedAtMs < 0L && target.isDeadOrDying()) {
                effect.beginKill(now, partialTick);
            } else if (effect.killedAtMs < 0L && target.isRemoved()) {
                iterator.remove();
                continue;
            }

            if (effect.killedAtMs >= 0L && now - effect.killedAtMs >= KILL_DURATION_MS) {
                iterator.remove();
                continue;
            }

            renderEffect(poseStack, buffer, camera, effect, now, partialTick, size, spins, wobble, flyHeight);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            LAYER.draw(mesh);
        }
    }

    private static void renderEffect(
            PoseStack poseStack, BufferBuilder buffer, Camera camera, Effect effect,
            long now, float partialTick, float size, float spins, float wobble, float flyHeight
    ) {
        float baseWidth = Math.max(0.01f, size);
        float baseHeight = baseWidth * TEXTURE_ASPECT_RATIO;
        float drawWidth;
        float drawHeight;
        float rotation;
        float offsetX;
        float offsetY;
        Color tint;
        Vec3 anchor;

        if (effect.killedAtMs < 0L) {
            float introProgress = progress(now - effect.createdAtMs, INTRO_DURATION_MS);
            float reactionProgress = progress(now - effect.lastHitAtMs, INTRO_DURATION_MS);
            float pop = Easing.EASE_OUT_ELASTIC.getFunction().apply(Mth.clamp(introProgress / 0.28f, 0.0f, 1.0f));
            float damping = 1.0f - reactionProgress;
            float squash = Mth.sin(reactionProgress * Mth.PI * 10.0f) * damping * 0.14f * wobble;

            drawWidth = baseWidth * pop * (1.0f + squash);
            drawHeight = baseHeight * pop * (1.0f - squash * 0.55f);
            rotation = aliveRotation(effect, now, spins, wobble);
            offsetX = Mth.sin(reactionProgress * Mth.PI * 24.0f) * damping * baseWidth * 0.09f * wobble;
            offsetY = -Math.abs(Mth.sin(reactionProgress * Mth.PI * 8.0f)) * damping * baseWidth * 0.08f * wobble;
            tint = hitTint(reactionProgress, 255);
            anchor = livingAnchor(effect.target, partialTick);
        } else {
            float killProgress = progress(now - effect.killedAtMs, KILL_DURATION_MS);
            float flyProgress = Easing.EASE_IN_CUBIC.getFunction().apply(killProgress);
            float scale = 1.0f + Easing.EASE_OUT_CUBIC.getFunction().apply(killProgress) * 0.45f;
            float squash = Mth.sin(killProgress * Mth.PI * 10.0f) * (1.0f - killProgress) * 0.18f * wobble;

            drawWidth = baseWidth * scale * (1.0f + squash);
            drawHeight = baseHeight * scale * (1.0f - squash * 0.55f);
            rotation = aliveRotation(effect, effect.killedAtMs, spins, wobble) + flyProgress * 1440.0f;
            offsetX = Mth.sin(killProgress * Mth.PI * 6.0f) * killProgress * baseWidth * 0.35f * wobble;
            offsetY = 0.0f;

            float fadeProgress = Mth.clamp((killProgress - 0.58f) / 0.42f, 0.0f, 1.0f);
            int alpha = Mth.clamp(Math.round(255.0f * (1.0f - Easing.EASE_IN_CUBIC.getFunction().apply(fadeProgress))), 0, 255);
            float redProgress = 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(killProgress);
            tint = redTint(redProgress, alpha);
            anchor = effect.deathAnchor.add(0.0, drawHeight / 2.0f + flyProgress * flyHeight, 0.0);
        }

        Vec3 relative = anchor.subtract(camera.position());
        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        poseStack.translate(offsetX, offsetY, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));

        Matrix4f matrix = poseStack.last().pose();
        float halfWidth = drawWidth / 2.0f;
        float halfHeight = drawHeight / 2.0f;
        int argb = tint.getRGB();

        buffer.addVertex(matrix, -halfWidth, halfHeight, 0.0f).setUv(0.0f, 1.0f).setColor(argb);
        buffer.addVertex(matrix, halfWidth, halfHeight, 0.0f).setUv(1.0f, 1.0f).setColor(argb);
        buffer.addVertex(matrix, halfWidth, -halfHeight, 0.0f).setUv(1.0f, 0.0f).setColor(argb);
        buffer.addVertex(matrix, -halfWidth, -halfHeight, 0.0f).setUv(0.0f, 0.0f).setColor(argb);

        poseStack.popPose();
    }

    private static Vec3 livingAnchor(LivingEntity target, float partialTick) {
        return target.getEyePosition(partialTick);
    }

    private static float aliveRotation(Effect effect, long now, float spins, float wobble) {
        long aliveElapsed = Math.max(0L, now - effect.createdAtMs);
        float introProgress = progress(aliveElapsed, INTRO_DURATION_MS);
        float reactionProgress = progress(Math.max(0L, now - effect.lastHitAtMs), INTRO_DURATION_MS);
        float introRotation = spins * 360.0f * Easing.EASE_OUT_EXPO.getFunction().apply(introProgress);
        float continuousSeconds = Math.max(0L, aliveElapsed - INTRO_DURATION_MS) / 1000.0f;
        float reactionWobble = Mth.sin(reactionProgress * Mth.PI * 12.0f)
                * (1.0f - reactionProgress) * 28.0f * wobble;
        return introRotation + continuousSeconds * CONTINUOUS_ROTATION_SPEED + reactionWobble;
    }

    private static Color hitTint(float reactionProgress, int alpha) {
        float redProgress = Mth.clamp(Mth.sin(Mth.clamp(reactionProgress / 0.72f, 0.0f, 1.0f) * Mth.PI), 0.0f, 1.0f);
        return redTint(redProgress, alpha);
    }

    private static Color redTint(float redProgress, int alpha) {
        int greenBlue = Mth.clamp(Math.round(Mth.lerp(Mth.clamp(redProgress, 0.0f, 1.0f), 255.0f, 42.0f)), 0, 255);
        return new Color(255, greenBlue, greenBlue, Mth.clamp(alpha, 0, 255));
    }

    private static float progress(long elapsed, long duration) {
        return Mth.clamp(elapsed / (float) duration, 0.0f, 1.0f);
    }

    private static final class Effect {
        private final LivingEntity target;
        private final long createdAtMs;
        private final long lastHitAtMs;
        private long killedAtMs = -1L;
        private Vec3 deathAnchor;

        private Effect(LivingEntity target, long now) {
            this.target = target;
            this.createdAtMs = now;
            this.lastHitAtMs = now;
        }

        private void beginKill(long now, float partialTick) {
            this.killedAtMs = now;
            this.deathAnchor = target.getEyePosition(partialTick);
        }
    }

}
