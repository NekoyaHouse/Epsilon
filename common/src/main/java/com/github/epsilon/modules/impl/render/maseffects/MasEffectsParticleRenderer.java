package com.github.epsilon.modules.impl.render.maseffects;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public final class MasEffectsParticleRenderer {

    private static final int MAX_PARTICLES = 8192;
    private static final Set<Item> DIAMOND_ARMOR = Set.of(
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
    );
    private static final Set<Item> NETHERITE_ARMOR = Set.of(
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS
    );

    private static final Identifier[] SHOCKWAVE = textures("shockwave", 5);
    private static final Identifier[] WINDWAVE = textures("windwave", 3);
    private static final Identifier[] REVIVE = textures("revive", 3);
    private static final Identifier[] REVIVE_SPARK = textures("revive_spark", 5);
    private static final Identifier[] DEATH_SKULL = textures("skull", 6);
    private static final Identifier[] DEATH_SPARK = textures("vapor", 4);
    private static final Identifier[] PEARL_TRAIL = textures("trail", 4);
    private static final Identifier DIAMOND_SCRAP = texture("diamond_scrap_0");
    private static final Identifier NETHERITE_SCRAP = texture("netherite_scrap_0");
    private static final Identifier SHIELD_WAVE = texture("shockwave");
    private static final Identifier FLICK = texture("spark_0");
    private static final Identifier FLASH = texture("flash");

    private static final RenderPipeline PARTICLE_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_mas_effects")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> PARTICLE_LAYER = Util.memoize(texture -> RenderType.create(
            "epsilon_mas_effects",
            RenderSetup.builder(PARTICLE_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    ));

    private final Minecraft mc = Minecraft.getInstance();
    private final MasEffects module;
    private final RandomSource random = RandomSource.create();
    private final List<EffectParticle> particles = new ArrayList<>();
    private final ConcurrentLinkedQueue<EffectParticle> pendingParticles = new ConcurrentLinkedQueue<>();

    public MasEffectsParticleRenderer(MasEffects module) {
        this.module = module;
    }

    public void tick() {
        drainPending();
        particles.removeIf(EffectParticle::tick);
    }

    public void render(PoseStack poseStack) {
        drainPending();
        if (particles.isEmpty()) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Map<Identifier, List<EffectParticle>> batches = new LinkedHashMap<>();
        for (EffectParticle particle : particles) {
            if (!particle.shouldRender()) continue;
            batches.computeIfAbsent(particle.texture(), ignored -> new ArrayList<>()).add(particle);
        }

        for (Map.Entry<Identifier, List<EffectParticle>> entry : batches.entrySet()) {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (EffectParticle particle : entry.getValue()) {
                particle.render(poseStack, buffer, partialTick);
            }

            MeshData mesh = buffer.build();
            if (mesh != null) {
                PARTICLE_LAYER.apply(entry.getKey()).draw(mesh);
            }
        }
    }

    public void clear() {
        particles.clear();
        pendingParticles.clear();
    }

    public void spawnSlam(Vec3 position, double size) {
        float clampedSize = Mth.clamp((float) size, 1.0F, 10.0F);
        if (module.isMaceShockwaveEnabled()) {
            add(new ShockwaveParticle(position, SHOCKWAVE, 0.8F, 0.8F, 1.5F * module.getMaceShockwaveSize() * clampedSize, false));
            add(new ShockwaveParticle(position, SHOCKWAVE, 0.4F, 1.0F, 0.35F * module.getMaceShockwaveSize() * clampedSize, false));
            if (!module.isLegacyMaceShockwave()) {
                spawnWindWave(position, 0.8F, 0.8F, 1.75F * module.getMaceShockwaveSize() * clampedSize);
            }
        }

        Vec3 offset = new Vec3(random(-0.6F, 0.6F), random(-1.2F, 1.2F), random(-0.6F, 0.6F));
        if (module.isMaceSparkEnabled()) {
            add(new FlickParticle(position.add(offset.x, offset.y + 0.9F, offset.z), 0.5F));
            add(new FlickParticle(position.add(offset.x, offset.y + 0.9F, offset.z), 0.2F));
        }
        if (module.isMaceFlashEnabled()) {
            add(new FlashParticle(position));
        }
    }

    public void spawnWindWave(Vec3 position, float scaler, float opacity, float size) {
        add(new ShockwaveParticle(position, WINDWAVE, scaler, opacity, size, true));
    }

    public void spawnShieldWave(Player shielder) {
        Vec3 height = new Vec3(0.0, shielder.getDimensions(shielder.getPose()).height() * 0.6F, 0.0);
        Vec3 position = shielder.position().add(height).add(shielder.getLookAngle().scale(0.5));
        add(new ShieldWaveParticle(position));
    }

    public void spawnArmorParticles(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
        armor.put(EquipmentSlot.HEAD, entity.getItemBySlot(EquipmentSlot.HEAD));
        armor.put(EquipmentSlot.CHEST, entity.getItemBySlot(EquipmentSlot.CHEST));
        armor.put(EquipmentSlot.LEGS, entity.getItemBySlot(EquipmentSlot.LEGS));
        armor.put(EquipmentSlot.FEET, entity.getItemBySlot(EquipmentSlot.FEET));

        Vec3 size = new Vec3(
                entity.getDimensions(entity.getPose()).width(),
                entity.getDimensions(entity.getPose()).height() / 4.0F,
                entity.getDimensions(entity.getPose()).width()
        );

        for (Map.Entry<EquipmentSlot, ItemStack> entry : armor.entrySet()) {
            ItemStack stack = entry.getValue();
            Identifier texture = armorTexture(stack);
            if (texture == null) continue;

            double yOffset = switch (entry.getKey()) {
                case HEAD, LEGS -> size.y * 3.0;
                case CHEST -> size.y * 2.0;
                case FEET -> 0.0;
                default -> 0.0;
            };
            spawnScraps(entity.position().add(0.0, yOffset, 0.0), size, texture, random.nextInt(3));
        }
    }

    public void spawnTotem(Entity entity) {
        Vec3 position = entity.position().add(0.0, entity.getDimensions(entity.getPose()).height() / 2.0F, 0.0);
        for (int i = 1; i < 18; i++) {
            add(new ReviveParticle(position, entity.getId(), 2.5F));
        }
        for (int i = 1; i < 100; i++) {
            add(new ReviveSparkParticle(position, entity.getId()));
        }
    }

    public void spawnDeath(Vec3 position) {
        add(new DeathSkullParticle(position));
        for (int i = 0; i < 30; i++) {
            add(new DeathSparkParticle(position));
        }
    }

    public void spawnPearlTrail(ThrownEnderpearl pearl) {
        Vec3 motion = pearl.getKnownMovement().normalize().scale(0.05F);
        Vec3 position = pearl.position();
        for (int i = 0; i < 3; i++) {
            add(new PearlTrailParticle(position, motion));
        }
    }

    private void spawnScraps(Vec3 position, Vec3 size, Identifier texture, int count) {
        for (int i = 0; i <= count; i++) {
            Vec3 offset = new Vec3(
                    random((float) -size.x / 2.0F, (float) size.x / 2.0F),
                    random(0.0F, (float) size.y),
                    random((float) -size.x / 2.0F, (float) size.x / 2.0F)
            );
            Vec3 motion = new Vec3(random(-0.08F, 0.08F), random(0.04F, 0.15F), random(-0.08F, 0.08F));
            add(new ScrapParticle(position.add(offset), motion, texture));
        }
    }

    private Identifier armorTexture(ItemStack stack) {
        if (!(stack.is(ItemTags.HEAD_ARMOR)
                || stack.is(ItemTags.CHEST_ARMOR)
                || stack.is(ItemTags.LEG_ARMOR)
                || stack.is(ItemTags.FOOT_ARMOR))) {
            return null;
        }
        if (DIAMOND_ARMOR.contains(stack.getItem())) return DIAMOND_SCRAP;
        if (NETHERITE_ARMOR.contains(stack.getItem())) return NETHERITE_SCRAP;
        return null;
    }

    private void add(EffectParticle particle) {
        pendingParticles.add(particle);
    }

    private void drainPending() {
        EffectParticle particle;
        while ((particle = pendingParticles.poll()) != null) {
            if (particles.size() >= MAX_PARTICLES) {
                particles.removeFirst();
            }
            particles.add(particle);
        }
    }

    private float random(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private int randomInclusive(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static Identifier texture(String name) {
        return ResourceLocationUtils.getIdentifier("textures/maseffects/particle/" + name + ".png");
    }

    private static Identifier[] textures(String prefix, int count) {
        Identifier[] result = new Identifier[count];
        for (int i = 0; i < count; i++) {
            result[i] = texture(prefix + "_" + i);
        }
        return result;
    }

    private static Quaternionf euler(float x, float y, float z) {
        Quaternionf qx = new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), x);
        Quaternionf qy = new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), y);
        Quaternionf qz = new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 0.0F, 1.0F), z);
        return qx.mul(qy).mul(qz);
    }

    private abstract class EffectParticle {
        protected double xo;
        protected double yo;
        protected double zo;
        protected double x;
        protected double y;
        protected double z;
        protected double xd;
        protected double yd;
        protected double zd;
        protected int age;
        protected final int lifetime;
        protected float gravity;
        protected float friction = 0.98F;
        protected float size = 0.1F;
        protected float alpha = 1.0F;
        protected float red = 1.0F;
        protected float green = 1.0F;
        protected float blue = 1.0F;
        protected boolean physics;
        protected boolean removed;
        private boolean stoppedByCollision;
        private final Identifier[] frames;

        protected EffectParticle(Vec3 position, int lifetime, Identifier... frames) {
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.lifetime = lifetime;
            this.frames = frames;
        }

        private boolean tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) return true;

            yd -= 0.04 * gravity;
            move();
            xd *= friction;
            yd *= friction;
            zd *= friction;
            afterTick();
            return removed;
        }

        protected void afterTick() {
        }

        protected boolean shouldRender() {
            return alpha > 0.0F && size > 0.0F;
        }

        protected Identifier texture() {
            if (frames.length == 1) return frames[0];
            int max = Math.max(1, frameDuration());
            int index = Mth.clamp(frameAge(), 0, max) * (frames.length - 1) / max;
            return frames[index];
        }

        protected int frameAge() {
            return age;
        }

        protected int frameDuration() {
            return lifetime;
        }

        protected void render(PoseStack poseStack, BufferBuilder buffer, float partialTick) {
            renderPlane(poseStack, buffer, partialTick, mc.gameRenderer.getMainCamera().rotation());
        }

        protected void renderPlane(PoseStack poseStack, BufferBuilder buffer, float partialTick, Quaternionf rotation) {
            Camera camera = mc.gameRenderer.getMainCamera();
            double renderX = Mth.lerp(partialTick, xo, x) - camera.position().x;
            double renderY = Mth.lerp(partialTick, yo, y) - camera.position().y;
            double renderZ = Mth.lerp(partialTick, zo, z) - camera.position().z;

            poseStack.pushPose();
            poseStack.translate(renderX, renderY, renderZ);
            poseStack.mulPose(rotation);

            Matrix4f matrix = poseStack.last().pose();
            float halfSize = size;
            int color = ARGB.colorFromFloat(
                    Mth.clamp(alpha, 0.0F, 1.0F),
                    Mth.clamp(red, 0.0F, 1.0F),
                    Mth.clamp(green, 0.0F, 1.0F),
                    Mth.clamp(blue, 0.0F, 1.0F)
            );

            buffer.addVertex(matrix, -halfSize, -halfSize, 0.0F).setUv(0.0F, 1.0F).setColor(color);
            buffer.addVertex(matrix, halfSize, -halfSize, 0.0F).setUv(1.0F, 1.0F).setColor(color);
            buffer.addVertex(matrix, halfSize, halfSize, 0.0F).setUv(1.0F, 0.0F).setColor(color);
            buffer.addVertex(matrix, -halfSize, halfSize, 0.0F).setUv(0.0F, 0.0F).setColor(color);

            poseStack.popPose();
        }

        protected Entity target(int entityId) {
            return mc.level == null ? null : mc.level.getEntity(entityId);
        }

        private void move() {
            if (stoppedByCollision) return;

            Vec3 movement = new Vec3(xd, yd, zd);
            if (!physics || mc.level == null || movement.lengthSqr() >= 10000.0) {
                x += xd;
                y += yd;
                z += zd;
                return;
            }

            AABB box = new AABB(x - 0.1, y, z - 0.1, x + 0.1, y + 0.2, z + 0.1);
            Vec3 adjusted = Entity.collideBoundingBox(null, movement, box, mc.level, List.of());
            x += adjusted.x;
            y += adjusted.y;
            z += adjusted.z;

            if (Math.abs(movement.y) >= 1.0E-5F && Math.abs(adjusted.y) < 1.0E-5F) {
                stoppedByCollision = true;
            }
            if (movement.x != adjusted.x) xd = 0.0;
            if (movement.z != adjusted.z) zd = 0.0;
        }
    }

    private final class ShockwaveParticle extends EffectParticle {
        private final boolean crossed;
        private float scaler;
        private final float sizer;
        private float alphaControl;

        private ShockwaveParticle(Vec3 position, Identifier[] frames, float scaler, float opacity, float size, boolean crossed) {
            super(position, 40, frames);
            this.scaler = scaler;
            this.alphaControl = opacity;
            this.alpha = Mth.clamp(opacity * module.getMaceShockwaveOpacity(), 0.0F, 1.0F);
            this.size = 0.5F;
            this.friction = 0.0F;
            this.sizer = size * 0.1F;
            this.crossed = crossed;
        }

        @Override
        protected void afterTick() {
            size += scaler * sizer;
            scaler = Mth.clamp(scaler - 1.0F / lifetime, 0.0F, 1.0F);
            alphaControl -= 1.0F / lifetime;
            alpha = Mth.clamp(alphaControl * module.getMaceShockwaveOpacity(), 0.0F, 1.0F);
        }

        @Override
        protected int frameAge() {
            return Math.min(age, lifetime / 2);
        }

        @Override
        protected int frameDuration() {
            return lifetime / 2;
        }

        @Override
        protected void render(PoseStack poseStack, BufferBuilder buffer, float partialTick) {
            renderPlane(poseStack, buffer, partialTick, new Quaternionf().rotateX((float) Math.PI / 2.0F));
            if (crossed) {
                renderPlane(poseStack, buffer, partialTick, new Quaternionf().rotateY((float) Math.PI / 4.0F).rotateX((float) Math.PI / 2.0F));
            }
        }
    }

    private final class ShieldWaveParticle extends EffectParticle {
        private float scaler = 1.0F;

        private ShieldWaveParticle(Vec3 position) {
            super(position, 30, SHIELD_WAVE);
            this.size = 0.3F;
            this.friction = 0.0F;
        }

        @Override
        protected void afterTick() {
            size += scaler * 0.2F;
            scaler = Mth.clamp(scaler - 1.0F / lifetime, 0.0F, 1.0F);
            alpha = Mth.clamp(alpha - 1.0F / lifetime, 0.0F, 1.0F);
        }
    }

    private final class FlickParticle extends EffectParticle {
        private final float scale;
        private float time;

        private FlickParticle(Vec3 position, float scale) {
            super(position, 20, FLICK);
            this.scale = scale;
            this.friction = 0.0F;
            this.xd = random(-0.04F, 0.04F);
            this.yd = random(0.02F, 0.1F);
            this.zd = random(-0.04F, 0.04F);
        }

        @Override
        protected void afterTick() {
            alpha = Mth.clamp((1.0F - time) * 1.5F, 0.2F, 1.0F) - 0.2F;
            time += 1.0F / lifetime;
            size = alpha * scale;
        }
    }

    private final class FlashParticle extends EffectParticle {
        private FlashParticle(Vec3 position) {
            super(position, 48 + random.nextInt(12), FLASH);
            this.size = 0.12F;
            this.alpha = 0.99F;
            this.gravity = 0.1F;
            this.friction = 0.91F;
        }

        @Override
        protected void afterTick() {
            if (age > lifetime / 2) {
                alpha = 1.0F - ((float) age - lifetime / 2.0F) / lifetime;
            }
        }
    }

    private final class ScrapParticle extends EffectParticle {
        private ScrapParticle(Vec3 position, Vec3 motion, Identifier texture) {
            super(position, 20, texture);
            this.gravity = 1.0F;
            this.physics = true;
            this.friction = 1.0F;
            this.xd = motion.x;
            this.yd = motion.y;
            this.zd = motion.z;
        }

        @Override
        protected void afterTick() {
            size = (1.0F - (float) age / lifetime) * 0.35F;
        }
    }

    private final class ReviveParticle extends EffectParticle {
        private final int targetId;
        private final float scale;
        private final float rotX;
        private final float rotZ;
        private float rotY;

        private ReviveParticle(Vec3 position, int targetId, float scale) {
            super(position, 40, REVIVE);
            this.targetId = targetId;
            this.scale = scale;
            this.alpha = 0.0F;
            this.size = 0.2F;
            this.friction = 0.0F;
            this.rotX = randomInclusive(-180, 180);
            this.rotY = randomInclusive(-180, 180);
            this.rotZ = randomInclusive(-180, 180);
            if (random.nextBoolean()) {
                this.red = 0.0F;
                this.green = 1.0F;
                this.blue = 0.0F;
            } else {
                this.red = 1.0F;
                this.green = 1.0F;
                this.blue = 0.0F;
            }
        }

        @Override
        protected void afterTick() {
            Entity target = target(targetId);
            if (target != null) {
                x = target.getX();
                y = target.getY() + target.getDimensions(target.getPose()).height() / 2.0F;
                z = target.getZ();
            }

            float opacity = module.getTotemEffectOpacity();
            if (opacity <= 0.0F) {
                alpha = 0.0F;
                size = 0.0F;
                return;
            }

            rotY += 20.0F;
            alpha = Mth.clamp((float) Math.sqrt(Math.sin((double) age / lifetime * Math.PI)) / 1.2F, 0.0F, 1.0F) * opacity;
            size = alpha / opacity * scale;
        }

        @Override
        protected void render(PoseStack poseStack, BufferBuilder buffer, float partialTick) {
            Quaternionf rotation = euler(rotZ, rotX, -rotZ).mul(euler(0.0F, 0.0F, rotY));
            renderPlane(poseStack, buffer, partialTick, rotation);
        }
    }

    private final class ReviveSparkParticle extends EffectParticle {
        private final int targetId;

        private ReviveSparkParticle(Vec3 position, int targetId) {
            super(position, randomInclusive(30, 48), REVIVE_SPARK[random.nextInt(REVIVE_SPARK.length)]);
            this.targetId = targetId;
            this.alpha = module.getTotemEffectOpacity();
            this.size = 0.1F;
            this.xd = randomInclusive(-10, 10) / 25.0F;
            this.yd = randomInclusive(-10, 10) / 25.0F;
            this.zd = randomInclusive(-10, 10) / 25.0F;
            this.friction = 1.0F;
            this.physics = true;
            if (random.nextBoolean()) {
                this.red = 0.0F;
                this.green = 1.0F;
                this.blue = 0.0F;
            } else {
                this.red = 1.0F;
                this.green = 1.0F;
                this.blue = 0.0F;
            }
        }

        @Override
        protected void afterTick() {
            Entity target = target(targetId);
            if (!(target instanceof LivingEntity livingEntity)) {
                alpha = module.getTotemEffectOpacity();
                return;
            }

            Vec3 targetDirection = new Vec3(
                    livingEntity.getX() - x,
                    livingEntity.getY() + livingEntity.getDimensions(livingEntity.getPose()).height() / 2.0F - y,
                    livingEntity.getZ() - z
            ).normalize();

            if (age >= lifetime / 5.0F && age < lifetime / 4.0F) {
                friction = 0.0F;
            }
            if (age >= lifetime / 4.0F && livingEntity.position().distanceTo(new Vec3(x, y, z)) < 20.0) {
                alpha = Mth.clamp(1.0F - (age - lifetime / 1.5F) / 20.0F, 0.0F, 1.0F) * module.getTotemEffectOpacity();
                friction = 1.0F;
                double speed = (age - 15) * 0.05F;
                xd = targetDirection.x * speed;
                yd = targetDirection.y * speed;
                zd = targetDirection.z * speed;
                if (livingEntity.position().distanceTo(new Vec3(x, y, z)) < 2.0) {
                    removed = true;
                }
            }
        }
    }

    private final class DeathSkullParticle extends EffectParticle {
        private DeathSkullParticle(Vec3 position) {
            super(position, 58, DEATH_SKULL);
            this.yd = 0.2;
            this.size = 1.0F;
            this.friction = 1.0F;
        }

        @Override
        protected void afterTick() {
            if (age > 30) {
                alpha -= 1.0F / 30.0F;
            }
            yd *= 0.95F;
        }

        @Override
        protected int frameAge() {
            return Math.min(age, 30);
        }

        @Override
        protected int frameDuration() {
            return 30;
        }
    }

    private final class DeathSparkParticle extends EffectParticle {
        private DeathSparkParticle(Vec3 position) {
            super(position, 20, DEATH_SPARK);
            this.gravity = -0.1F;
            this.friction = 1.0F;
            this.size = randomInclusive(5, 10) * 0.06F;
            this.xd = randomInclusive(-10, 10) * 0.02F;
            this.zd = randomInclusive(-10, 10) * 0.02F;
        }

        @Override
        protected void afterTick() {
            alpha = (1.0F - (float) age / lifetime) * 0.45F;
            xd *= 0.9F;
            zd *= 0.9F;
        }
    }

    private final class PearlTrailParticle extends EffectParticle {
        private float alphaControl = 1.0F;

        private PearlTrailParticle(Vec3 position, Vec3 motion) {
            super(position, 10, PEARL_TRAIL);
            this.xd = motion.x + randomInclusive(-10, 10) * 0.005F;
            this.yd = motion.y + randomInclusive(-10, 10) * 0.005F;
            this.zd = motion.z + randomInclusive(-10, 10) * 0.005F;
            this.friction = 0.98F;
            if (mc.player != null && mc.player.position().distanceTo(position) < 6.0) {
                alphaControl -= 0.8F;
            }
            this.alpha = Mth.clamp(alphaControl * module.getPearlTrailOpacity(), 0.0F, 1.0F);
        }

        @Override
        protected void afterTick() {
            size = (1.0F - (float) age / lifetime) * 0.15F;
            alphaControl -= 0.1F;
            if (mc.player != null && mc.player.position().distanceTo(new Vec3(x, y, z)) < 6.0) {
                alphaControl -= 0.8F;
            }
            alpha = Mth.clamp(alphaControl * module.getPearlTrailOpacity(), 0.0F, 1.0F);
        }
    }

}
