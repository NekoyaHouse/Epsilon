package me.sofurry.via.features.block.shape;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class CollisionShapes {

    public static void reloadBlockShapes() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof AnvilBlock || block instanceof BedBlock || block instanceof BrewingStandBlock
                    || block instanceof CarpetBlock || block instanceof CauldronBlock || block instanceof ChestBlock
                    || block instanceof EnderChestBlock || block instanceof EndPortalBlock || block instanceof EndPortalFrameBlock
                    || block instanceof FarmlandBlock || block instanceof FenceBlock || block instanceof FenceGateBlock
                    || block instanceof HopperBlock || block instanceof LadderBlock || block instanceof LeavesBlock
                    || block instanceof LilyPadBlock || block instanceof IronBarsBlock || block instanceof PistonBaseBlock
                    || block instanceof PistonHeadBlock || block instanceof SnowLayerBlock || block instanceof WallBlock
                    || block instanceof CropBlock || block instanceof FlowerBedBlock
            ) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    state.initCache();
                }
            }
        }
    }

}
