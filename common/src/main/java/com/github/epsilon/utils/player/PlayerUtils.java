package com.github.epsilon.utils.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.AABB;

import static com.github.epsilon.Constants.mc;

public class PlayerUtils {

    public static boolean isEating() {
        return (mc.player.getMainHandItem().getComponents().has(DataComponents.FOOD) || mc.player.getOffhandItem().getComponents().has(DataComponents.FOOD)) && mc.player.isUsingItem();
    }

    public static boolean isInWeb() {
        AABB box = mc.player.getBoundingBox().deflate(1.0E-6);

        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    if (mc.level.getBlockState(mutablePos).getBlock() instanceof WebBlock) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean isInBlock() {
        AABB box = mc.player.getBoundingBox().deflate(1.0E-6);

        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    if (mc.level.getBlockState(mutablePos).isSolidRender()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

}
