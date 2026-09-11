package com.github.epsilon.utils.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class ProjectileSimulator {

    private enum UpdateOrder {
        GRAVITY_DRAG_POSITION,
        POSITION_DRAG_GRAVITY,
        GRAVITY_POSITION_DRAG
    }

    private static final MotionData EGG = throwable(1.5, 0.0, 0.03, EntityTypes.EGG);
    private static final MotionData ENDER_PEARL = throwable(1.5, 0.0, 0.03, EntityTypes.ENDER_PEARL);
    private static final MotionData SNOWBALL = throwable(1.5, 0.0, 0.03, EntityTypes.SNOWBALL);
    private static final MotionData EXPERIENCE_BOTTLE = throwable(0.7, -20.0, 0.07, EntityTypes.EXPERIENCE_BOTTLE);
    private static final MotionData LINGERING_POTION = throwable(0.5, -20.0, 0.05, EntityTypes.LINGERING_POTION);
    private static final MotionData SPLASH_POTION = throwable(0.5, -20.0, 0.05, EntityTypes.SPLASH_POTION);
    private static final MotionData WIND_CHARGE = new MotionData(1.5, 0.0, 0.0, 1.0, 1.0, EntityTypes.WIND_CHARGE, UpdateOrder.GRAVITY_DRAG_POSITION);
    private static final MotionData EXPLOSIVE = new MotionData(0.0, 0.0, 0.0, 1.0, 1.0, EntityTypes.FIREBALL, UpdateOrder.GRAVITY_DRAG_POSITION);
    private static final MotionData ARROW = new MotionData(0.0, 0.0, 0.05, 0.99, 0.6, EntityTypes.ARROW, UpdateOrder.POSITION_DRAG_GRAVITY);
    private static final MotionData TRIDENT = new MotionData(2.5, 0.0, 0.05, 0.99, 0.99, EntityTypes.TRIDENT, UpdateOrder.POSITION_DRAG_GRAVITY);
    private static final MotionData LLAMA_SPIT = new MotionData(0.0, 0.0, 0.06, 0.99, 0.0, EntityTypes.LLAMA_SPIT, UpdateOrder.POSITION_DRAG_GRAVITY);
    private static final MotionData FIREWORK = new MotionData(0.0, 0.0, 0.0, 1.0, 1.0, EntityTypes.FIREWORK_ROCKET, UpdateOrder.GRAVITY_POSITION_DRAG);
    private static final MotionData FISHING_BOBBER = new MotionData(0.0, 0.0, 0.03, 0.92, 0.0, EntityTypes.FISHING_BOBBER, UpdateOrder.GRAVITY_POSITION_DRAG);

    private final Level level;
    private final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

    private Vec3 position = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private Entity collisionSource;
    private Entity ignoredEntity;
    private EntityDimensions dimensions;
    private MotionData motion;
    private boolean inWater;
    private int ticks;
    private int pierceLevel;
    private final Set<Entity> piercedEntities = new HashSet<>();

    public ProjectileSimulator(Level level) {
        this.level = level;
    }

    public static boolean supports(Item item) {
        return item instanceof BowItem || item instanceof CrossbowItem || item instanceof FishingRodItem
                || item instanceof TridentItem || item instanceof SnowballItem || item instanceof EggItem
                || item instanceof EnderpearlItem || item instanceof ExperienceBottleItem
                || item instanceof SplashPotionItem || item instanceof LingeringPotionItem
                || item instanceof WindChargeItem;
    }

    public boolean configureHeld(Entity user, ItemStack stack, double angleOffset, float partialTick, int piercing) {
        Item item = stack.getItem();
        MotionData data;

        if (item instanceof BowItem) {
            if (!(user instanceof LivingEntity living)) return false;
            float charge = BowItem.getPowerForTime(living.getTicksUsingItem());
            if (charge <= 0.1F) {
                if (user instanceof Player player && player.isLocalPlayer()) charge = 1.0F;
                else return false;
            }
            data = ARROW.withPower(charge * 3.0);
        } else if (item instanceof CrossbowItem) {
            return false; // The caller supplies the charged projectile's exact motion data.
        } else if (item instanceof FishingRodItem) {
            configureFishing(user, partialTick);
            return true;
        } else if (item instanceof TridentItem) data = TRIDENT;
        else if (item instanceof SnowballItem) data = SNOWBALL;
        else if (item instanceof EggItem) data = EGG;
        else if (item instanceof EnderpearlItem) data = ENDER_PEARL;
        else if (item instanceof ExperienceBottleItem) data = EXPERIENCE_BOTTLE;
        else if (item instanceof SplashPotionItem) data = SPLASH_POTION;
        else if (item instanceof LingeringPotionItem) data = LINGERING_POTION;
        else if (item instanceof WindChargeItem) data = WIND_CHARGE;
        else return false;

        configureShot(user, angleOffset, partialTick, data, piercing);
        return true;
    }

    public void configureCrossbow(Entity user, boolean firework, double angleOffset, float partialTick, int piercing) {
        MotionData data = firework ? FIREWORK.withPower(1.6) : ARROW.withPower(3.15);
        configureShot(user, angleOffset, partialTick, data, firework ? 0 : piercing);
    }

    public boolean configureFired(Projectile projectile) {
        MotionData data;
        if (projectile instanceof ThrownTrident) data = TRIDENT;
        else if (projectile instanceof AbstractArrow) data = ARROW;
        else if (projectile instanceof ThrownEnderpearl) data = ENDER_PEARL;
        else if (projectile instanceof ThrownExperienceBottle) data = EXPERIENCE_BOTTLE;
        else if (projectile instanceof ThrownSplashPotion) data = SPLASH_POTION;
        else if (projectile instanceof ThrownLingeringPotion) data = LINGERING_POTION;
        else if (projectile instanceof AbstractWindCharge) data = WIND_CHARGE;
        else if (projectile instanceof AbstractHurtingProjectile) data = EXPLOSIVE;
        else if (projectile instanceof LlamaSpit) data = LLAMA_SPIT;
        else if (projectile instanceof FishingHook) data = FISHING_BOBBER;
        else if (projectile instanceof ThrowableProjectile) data = SNOWBALL;
        else return false;

        position = projectile.position();
        velocity = projectile.getDeltaMovement();
        collisionSource = projectile;
        ignoredEntity = projectile.getOwner();
        setMotion(data, projectile.tickCount, 0, projectile.isInWater());
        dimensions = projectile.getDimensions(projectile.getPose());
        if (projectile.isNoGravity()) {
            motion = new MotionData(data.power, data.roll, 0.0, data.airDrag, data.waterDrag, data.entityType, data.updateOrder);
        }
        return velocity.lengthSqr() > 1.0E-8;
    }

    public Vec3 position() {
        return position;
    }

    public Step tick() {
        Vec3 previous = position;
        ticks++;

        switch (motion.updateOrder) {
            case GRAVITY_DRAG_POSITION -> {
                velocity = velocity.add(0.0, -motion.gravity, 0.0).scale(inWater ? motion.waterDrag : motion.airDrag);
                position = position.add(velocity);
                updateWaterState();
            }
            case POSITION_DRAG_GRAVITY -> {
                position = position.add(velocity);
                velocity = velocity.scale(inWater ? motion.waterDrag : motion.airDrag).add(0.0, -motion.gravity, 0.0);
                updateWaterState();
            }
            case GRAVITY_POSITION_DRAG -> {
                updateWaterState();
                velocity = velocity.add(0.0, -motion.gravity, 0.0);
                position = position.add(velocity);
                velocity = velocity.scale(inWater ? motion.waterDrag : motion.airDrag);
            }
        }

        if (position.y < level.getMinY()
                || !level.getChunkSource().hasChunk(SectionPos.posToSectionCoord(position.x), SectionPos.posToSectionCoord(position.z))) {
            return new Step(position, null, true);
        }

        HitResult hit = findCollision(previous, position);
        if (hit.getType() == HitResult.Type.MISS) return new Step(position, null, false);

        position = hit.getLocation();
        if (hit instanceof EntityHitResult && pierceLevel > 0) {
            piercedEntities.add(((EntityHitResult) hit).getEntity());
            pierceLevel--;
            return new Step(position, hit, false);
        }
        return new Step(position, hit, true);
    }

    private void configureShot(Entity user, double angleOffset, float partialTick, MotionData data, int piercing) {
        position = user.getPosition(partialTick).add(0.0, user.getEyeHeight(user.getPose()) - 0.1, 0.0);
        double yaw = Math.toRadians(user.getYRot(partialTick));
        double pitch = Math.toRadians(user.getXRot(partialTick) + data.roll);
        Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));

        if (angleOffset != 0.0) {
            Vec3 axis = user.getUpVector(partialTick).normalize();
            double cos = Math.cos(angleOffset);
            double sin = Math.sin(angleOffset);
            direction = direction.scale(cos).add(axis.cross(direction).scale(sin)).add(axis.scale(axis.dot(direction) * (1.0 - cos)));
        }

        velocity = direction.normalize().scale(data.power);

        collisionSource = user;
        ignoredEntity = user;
        setMotion(data, 0, piercing, false);
    }

    private void configureFishing(Entity user, float partialTick) {
        double yaw = Math.toRadians(user.getYRot(partialTick));
        double pitch = Math.toRadians(user.getXRot(partialTick));
        double yawCos = Math.cos(-yaw - Math.PI);
        double yawSin = Math.sin(-yaw - Math.PI);
        double pitchCos = -Math.cos(-pitch);
        double pitchSin = Math.sin(-pitch);

        position = user.getPosition(partialTick).subtract(yawSin * 0.3, 0.0, yawCos * 0.3).add(0.0, user.getEyeHeight(user.getPose()), 0.0);
        velocity = new Vec3(-yawSin, Mth.clamp(-(pitchSin / pitchCos), -5.0, 5.0), -yawCos);
        double length = velocity.length();
        velocity = velocity.scale(0.6 / length + 0.5);
        collisionSource = user;
        ignoredEntity = user;
        setMotion(FISHING_BOBBER, 0, 0, false);
    }

    private void setMotion(MotionData data, int ticks, int piercing, boolean inWater) {
        motion = data;
        dimensions = data.entityType.getDimensions();
        this.ticks = ticks;
        this.pierceLevel = piercing;
        this.inWater = inWater;
        piercedEntities.clear();
    }

    private HitResult findCollision(Vec3 from, Vec3 to) {
        ClipContext.Fluid fluid = motion.waterDrag == 0.0 ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        HitResult blockHit = level.clipIncludingBorder(new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid, collisionSource));
        Vec3 clippedTo = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        Vec3 delta = clippedTo.subtract(from);
        AABB search = dimensions.makeBoundingBox(from).expandTowards(delta).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, collisionSource, from, clippedTo, search, entity -> entity != ignoredEntity && !piercedEntities.contains(entity) && !entity.isSpectator() && entity.isAlive() && entity.isPickable(), Mth.clamp((ticks - 2) / 20.0F, 0.0F, 0.3F));
        return entityHit != null ? entityHit : blockHit;
    }

    private void updateWaterState() {
        AABB box = dimensions.makeBoundingBox(position).deflate(0.001);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.ceil(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    blockPos.set(x, y, z);
                    FluidState state = level.getFluidState(blockPos);
                    if (state.is(FluidTags.WATER) && y + state.getHeight(level, blockPos) >= box.minY) {
                        inWater = true;
                        return;
                    }
                }
            }
        }
        inWater = false;
    }

    private static MotionData throwable(double power, double roll, double gravity, EntityType<?> type) {
        return new MotionData(power, roll, gravity, 0.99, 0.8, type, UpdateOrder.GRAVITY_DRAG_POSITION);
    }

    private record MotionData(
            double power, double roll, double gravity, double airDrag, double waterDrag, EntityType<?> entityType,
            UpdateOrder updateOrder
    ) {
        private MotionData withPower(double power) {
            return new MotionData(power, roll, gravity, airDrag, waterDrag, entityType, updateOrder);
        }
    }

    public record Step(Vec3 position, HitResult hit, boolean stop) {
    }

}
