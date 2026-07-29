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

}
