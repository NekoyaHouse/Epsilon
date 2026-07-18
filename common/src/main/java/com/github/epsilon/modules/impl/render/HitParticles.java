package com.github.epsilon.modules.impl.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.math.MathUtils;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class HitParticles extends Module {

    public static final HitParticles INSTANCE = new HitParticles();

    private HitParticles() {
        super("Hit Particles", Category.RENDER);
    }

    private enum Physics {
        Fall,
        Fly
    }

    private enum Mode {
        Stars,
        Hearts,
        Bloom
    }

    private enum ColorMode {
        Custom,
        Sync
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Stars);
    private final EnumSetting<Physics> physics = enumSetting("Physics", Physics.Fall);
    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Sync);
    private final ColorSetting color = colorSetting("Color", new Color(0, 255, 0, 53), true, () -> colorMode.is(ColorMode.Custom));
    private final BoolSetting onlySelf = boolSetting("Only Self", false);
    private final IntSetting amount = intSetting("Amount", 2, 1, 5, 1);
    private final IntSetting lifeTime = intSetting("Life Time", 2, 1, 10, 1);
    private final IntSetting speed = intSetting("Speed", 2, 1, 20, 1);
    private final DoubleSetting scale = doubleSetting("Scale", 3.0, 1.0, 10.0, 0.1);

    private final List<Particle> particles = new ArrayList<>();

    private static final Identifier STAR_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/star.png");
    private static final Identifier HEART_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/heart.png");
    private static final Identifier BLOOM_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/firefly.png");

    private static final RenderPipeline HIT_PARTICLE_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_hit_particles")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> HIT_PARTICLE_LAYER = Util.memoize(texture -> RenderType.create(
            "epsilon_hit_particles",
            RenderSetup.builder(HIT_PARTICLE_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    ));

    @Override
    protected void onDisable() {
        particles.clear();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        particles.removeIf(Particle::tick);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity)) continue;
            if (onlySelf.getValue() && livingEntity != mc.player) continue;
            if (livingEntity.hurtTime <= 0) continue;

            Color particleColor = resolveColor((int) MathUtils.getRandom(1.0f, 228.0f));
            for (int i = 0; i < amount.getValue(); i++) {
                particles.add(new Particle(
                        (float) livingEntity.getX(),
                        MathUtils.getRandom((float) livingEntity.getY(), (float) (livingEntity.getY() + livingEntity.getBbHeight())),
                        (float) livingEntity.getZ(),
                        particleColor,
                        MathUtils.getRandom(0.0f, 180.0f),
                        MathUtils.getRandom(10.0f, 60.0f)
                ));
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (particles.isEmpty()) return;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Particle particle : particles) {
            particle.renderTexture(event.getPoseStack(), buffer);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            HIT_PARTICLE_LAYER.apply(
                    switch (mode.getValue()) {
                        case Stars -> STAR_TEXTURE;
                        case Hearts -> HEART_TEXTURE;
                        case Bloom -> BLOOM_TEXTURE;
                    }
            ).draw(mesh);
        }
    }

    private Color resolveColor(int offset) {
        return colorMode.is(ColorMode.Sync) ? syncColor(offset) : color.getValue();
    }

    private Color syncColor(int offset) {
        float hue = Mth.frac((System.currentTimeMillis() + offset * 20L) / 4500.0f);
        Color rainbow = Color.getHSBColor(hue, 0.65f, 1.0f);
        return new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), 255);
    }

    private class Particle {
        private float x;
        private float y;
        private float z;
        private float prevX;
        private float prevY;
        private float prevZ;
        private float motionX;
        private float motionY;
        private float motionZ;
        private float rotationAngle;
        private final float rotationSpeed;
        private final long spawnTime;
        private final Color particleColor;
        private long lastRenderTime;

        private Particle(float x, float y, float z, Color particleColor, float rotationAngle, float rotationSpeed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.motionX = MathUtils.getRandom(-speed.getValue() / 50.0f, speed.getValue() / 50.0f);
            this.motionY = MathUtils.getRandom(-speed.getValue() / 50.0f, speed.getValue() / 50.0f);
            this.motionZ = MathUtils.getRandom(-speed.getValue() / 50.0f, speed.getValue() / 50.0f);
            this.rotationAngle = rotationAngle;
            this.rotationSpeed = rotationSpeed;
            this.spawnTime = System.currentTimeMillis();
            this.particleColor = particleColor;
            this.lastRenderTime = spawnTime;
        }

        private boolean tick() {
            double horizontalSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);

            prevX = x;
            prevY = y;
            prevZ = z;

            x += motionX;
            y += motionY;
            z += motionZ;

            if (isSolidBlock(x, y - scale.getValue().floatValue() / 10.0f, z)) {
                motionY = -motionY / 1.1f;
                motionX /= 1.1f;
                motionZ /= 1.1f;
            } else if (isSolidBlock(x - horizontalSpeed, y, z - horizontalSpeed)
                    || isSolidBlock(x + horizontalSpeed, y, z + horizontalSpeed)
                    || isSolidBlock(x + horizontalSpeed, y, z - horizontalSpeed)
                    || isSolidBlock(x - horizontalSpeed, y, z + horizontalSpeed)
                    || isSolidBlock(x + horizontalSpeed, y, z)
                    || isSolidBlock(x - horizontalSpeed, y, z)
                    || isSolidBlock(x, y, z + horizontalSpeed)
                    || isSolidBlock(x, y, z - horizontalSpeed)) {
                motionX = -motionX;
                motionZ = -motionZ;
            }

            if (physics.is(Physics.Fall)) {
                motionY -= 0.035f;
            }

            motionX /= 1.005f;
            motionY /= 1.005f;
            motionZ /= 1.005f;

            return System.currentTimeMillis() - spawnTime > lifeTime.getValue() * 1000L;
        }

        private void renderTexture(PoseStack poseStack, BufferBuilder buffer) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float particleScale = 0.07f;
            float size = scale.getValue().floatValue();
            Vec3 position = interpolatedCameraRelativePosition(partialTick);

            poseStack.pushPose();
            poseStack.translate(position.x, position.y + 0.1, position.z);
            poseStack.scale(particleScale, particleScale, particleScale);
            poseStack.translate(size / 2.0f, size / 2.0f, size / 2.0f);
            applyCameraRotation(poseStack);
            poseStack.mulPose(Axis.ZP.rotationDegrees(nextRotation()));
            poseStack.translate(-size / 2.0f, -size / 2.0f, -size / 2.0f);

            Matrix4f matrix = poseStack.last().pose();
            int argb = particleColor.getRGB();

            buffer.addVertex(matrix, 0.0f, size, 0.0f).setUv(0.0f, 1.0f).setColor(argb);
            buffer.addVertex(matrix, size, size, 0.0f).setUv(1.0f, 1.0f).setColor(argb);
            buffer.addVertex(matrix, size, 0.0f, 0.0f).setUv(1.0f, 0.0f).setColor(argb);
            buffer.addVertex(matrix, 0.0f, 0.0f, 0.0f).setUv(0.0f, 0.0f).setColor(argb);

            poseStack.popPose();
        }

        private Vec3 interpolatedCameraRelativePosition(float partialTick) {
            Camera camera = mc.gameRenderer.getMainCamera();
            double renderX = Mth.lerp(partialTick, prevX, x) - camera.position().x;
            double renderY = Mth.lerp(partialTick, prevY, y) - camera.position().y;
            double renderZ = Mth.lerp(partialTick, prevZ, z) - camera.position().z;
            return new Vec3(renderX, renderY, renderZ);
        }

        private void applyCameraRotation(PoseStack poseStack) {
            Camera camera = mc.gameRenderer.getMainCamera();
            poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        }

        private float nextRotation() {
            long now = System.currentTimeMillis();
            float delta = Math.max(0.0f, (now - lastRenderTime) / 1000.0f);
            lastRenderTime = now;
            rotationAngle += delta * rotationSpeed;
            return rotationAngle;
        }

        private boolean isSolidBlock(double x, double y, double z) {
            BlockState state = mc.level.getBlockState(BlockPos.containing(x, y, z));
            return !state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA);
        }
    }

}
