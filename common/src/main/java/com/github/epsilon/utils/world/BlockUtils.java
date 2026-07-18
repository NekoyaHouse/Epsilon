package com.github.epsilon.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.phys.AABB;

import static com.github.epsilon.Constants.mc;

public class BlockUtils {

    public static boolean canPlaceAt(BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        return mc.level.getEntities((Entity) null, new AABB(pos), entity -> !(entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow)).isEmpty();
    }

}
