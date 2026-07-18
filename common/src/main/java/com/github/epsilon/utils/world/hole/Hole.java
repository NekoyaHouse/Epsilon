package com.github.epsilon.utils.world.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class Hole {

    public final BlockPos pos;
    public final HoleType type;
    public final BlockPos[] positions;
    public final Vec3 middle;

    public Hole(BlockPos pos, HoleType type) {
        this.pos = pos;
        this.type = type;
        switch (type) {
            case Single -> {
                this.positions = new BlockPos[]{pos};
                this.middle = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
            case DoubleX -> {
                this.positions = new BlockPos[]{pos, pos.offset(1, 0, 0)};
                this.middle = new Vec3(pos.getX() + 1, pos.getY(), pos.getZ() + 0.5);
            }
            case DoubleZ -> {
                this.positions = new BlockPos[]{pos, pos.offset(0, 0, 1)};
                this.middle = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1);
            }
            case Quad -> {
                this.positions = new BlockPos[]{pos, pos.offset(1, 0, 0), pos.offset(0, 0, 1), pos.offset(1, 0, 1)};
                this.middle = new Vec3(pos.getX() + 1, pos.getY(), pos.getZ() + 1);
            }
            default -> {
                this.positions = new BlockPos[]{};
                this.middle = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
        }
    }

    public BlockPos[] positions() {
        return positions;
    }

}
