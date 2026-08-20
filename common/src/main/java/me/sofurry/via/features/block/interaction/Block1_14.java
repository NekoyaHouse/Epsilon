package me.sofurry.via.features.block.interaction;

import net.minecraft.world.level.block.*;

public final class Block1_14 {

    public static boolean isExceptBlockForAttachWithPiston(final Block block) {
        return block instanceof ShulkerBoxBlock
                || block instanceof LeavesBlock
                || block instanceof TrapDoorBlock
                || block instanceof StainedGlassBlock
                || block == Blocks.BEACON
                || block == Blocks.CAULDRON
                || block == Blocks.GLASS
                || block == Blocks.GLOWSTONE
                || block == Blocks.ICE
                || block == Blocks.SEA_LANTERN
                || block == Blocks.PISTON
                || block == Blocks.STICKY_PISTON
                || block == Blocks.PISTON_HEAD;
    }

}
