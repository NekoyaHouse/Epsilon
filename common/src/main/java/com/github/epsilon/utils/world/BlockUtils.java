package com.github.epsilon.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.github.epsilon.Constants.mc;

public class BlockUtils {

    public static boolean canBeReplacedWith(BlockState state, ItemStack item) {
        return state.canBeReplaced() && (item.isEmpty() || !item.is(state.getBlock().asItem()));
    }

    /**
     * 判断本地玩家是否可在指定位置放置方块。
     *
     * @param pos 目标位置
     * @return 判断结果
     */
    public static boolean canPlaceAt(BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        return mc.level.getEntities((Entity) null, new AABB(pos), entity -> !(entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow)).isEmpty();
    }

    public static BlockPos getPlacePos(BlockHitResult hit, ItemStack item) {
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (!state.isAir() && canBeReplacedWith(state, item)) {
            return hit.getBlockPos();
        }
        return hit.getBlockPos().relative(hit.getDirection());
    }

    public static boolean isFaceVisible(BlockPos pos, Direction face) {
        Vec3 eyePos = mc.player.getEyePosition();
        AABB box = new AABB(pos);
        return switch (face) {
            case NORTH -> eyePos.z > box.minZ;
            case SOUTH -> eyePos.z < box.maxZ;
            case EAST -> eyePos.x < box.maxX;
            case WEST -> eyePos.x > box.minX;
            case UP -> eyePos.y < box.maxY;
            case DOWN -> eyePos.y > box.minY;
        };
    }

    public static boolean isSolidBlock(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        if (canBeReplacedWith(state, ItemStack.EMPTY)) return false;
        return !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    public static boolean hasBlockingEntity(AABB box) {
        for (Entity entity : mc.level.getEntities(null, box)) {
            if (!entity.isAlive()) continue;
            if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) continue;
            if (entity instanceof EndCrystal) continue;
            return true;
        }
        return false;
    }

    public static boolean isWithinRange(Vec3 point) {
        if (mc.player == null) return false;
        double range = mc.player.blockInteractionRange();
        return point.distanceToSqr(mc.player.getEyePosition()) <= range * range;
    }

    public static Direction getVisibleFace(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        return Direction.getApproximateNearest(mc.player.position().subtract(Vec3.atCenterOf(pos)));
    }

}
