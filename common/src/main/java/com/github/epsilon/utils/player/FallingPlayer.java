package com.github.epsilon.utils.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.github.epsilon.Constants.mc;

public class FallingPlayer {

    private double x;
    private double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float strafe;
    private final float forward;
    private final float jumpMovementFactor;

    public FallingPlayer(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw, float strafe, float forward, float jumpMovementFactor) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
        this.strafe = strafe;
        this.forward = forward;
        this.jumpMovementFactor = jumpMovementFactor;
    }

    public FallingPlayer(Player player) {
        this(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getDeltaMovement().x,
                player.getDeltaMovement().y,
                player.getDeltaMovement().z,
                player.getYRot(),
                0.0F,
                0.0F,
                player.getSpeed()
        );
    }

    private void calculateForTick() {
        float sr = strafe * 0.9800000190734863F;
        float fw = forward * 0.9800000190734863F;
        float movement = sr * sr + fw * fw;

        if (movement >= 1.0E-4F) {
            movement = Mth.sqrt(movement);
            if (movement < 1.0F) {
                movement = 1.0F;
            }

            float fixedJumpFactor = jumpMovementFactor;
            if (mc.player != null && mc.player.isSprinting()) {
                fixedJumpFactor *= 1.3F;
            }

            movement = fixedJumpFactor / movement;
            sr *= movement;
            fw *= movement;

            float sin = Mth.sin(yaw * ((float) Math.PI / 180.0F));
            float cos = Mth.cos(yaw * ((float) Math.PI / 180.0F));
            motionX += sr * cos - fw * sin;
            motionZ += fw * cos + sr * sin;
        }

        motionY -= 0.08;
        motionY *= 0.9800000190734863;
        x += motionX;
        y += motionY;
        z += motionZ;
        motionX *= 0.91;
        motionZ *= 0.91;
    }

    public void calculate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            calculateForTick();
        }
    }

    public BlockPos findCollision(int ticks) {
        for (int i = 0; i < ticks; i++) {
            Vec3 start = new Vec3(x, y, z);
            calculateForTick();
            Vec3 end = new Vec3(x, y, z);
            BlockPos raytracedBlock;
            float halfWidth = mc.player.getBbWidth() / 2.0F;

            if ((raytracedBlock = raytrace(start, end)) != null) return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(halfWidth, 0.0, halfWidth), end)) != null) return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(-halfWidth, 0.0, halfWidth), end)) != null) return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(halfWidth, 0.0, -halfWidth), end)) != null) return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(-halfWidth, 0.0, -halfWidth), end)) != null) return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(halfWidth, 0.0, halfWidth / 2.0F), end)) != null)
                return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(-halfWidth, 0.0, halfWidth / 2.0F), end)) != null)
                return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(halfWidth / 2.0F, 0.0, halfWidth), end)) != null)
                return raytracedBlock;
            if ((raytracedBlock = raytrace(start.add(halfWidth / 2.0F, 0.0, -halfWidth), end)) != null)
                return raytracedBlock;
        }

        return null;
    }

    private BlockPos raytrace(Vec3 start, Vec3 end) {
        BlockHitResult result = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (result.getType() == HitResult.Type.BLOCK && result.getDirection() == Direction.UP) {
            return result.getBlockPos();
        }

        return null;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

}
