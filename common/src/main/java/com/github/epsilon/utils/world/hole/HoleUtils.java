package com.github.epsilon.utils.world.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import static com.github.epsilon.Constants.mc;

public class HoleUtils {

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos 目标位置
     * @return 操作结果
     */
    public static Hole getHole(BlockPos pos) {
        return getHole(pos, true, true, true, 3, true);
    }

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos   目标位置
     * @param depth 洞结构需要检查的内部高度
     * @return 操作结果
     */
    public static Hole getHole(BlockPos pos, int depth) {
        return getHole(pos, depth, true);
    }

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos   目标位置
     * @param depth 洞结构需要检查的内部高度
     * @param floor 是否要求洞底为抗爆方块
     * @return 操作结果
     */
    public static Hole getHole(BlockPos pos, int depth, boolean floor) {
        return getHole(pos, true, true, true, depth, floor);
    }

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos     目标位置
     * @param single  是否检测单格洞
     * @param doubles 是否检测双格洞
     * @param quad    是否检测四格洞
     * @param depth   洞结构需要检查的内部高度
     * @param floor   是否要求洞底为抗爆方块
     * @return 操作结果
     */
    public static Hole getHole(BlockPos pos, boolean single, boolean doubles, boolean quad, int depth, boolean floor) {
        if (!isHole(pos, depth, floor)) {
            return new Hole(pos, HoleType.NotHole);
        }

        if (!isBlock(pos.west()) || !isBlock(pos.north())) {
            return new Hole(pos, HoleType.NotHole);
        }

        boolean x = isHole(pos.east(), depth, floor) && isBlock(pos.east().north()) && isBlock(pos.east(2));
        boolean z = isHole(pos.south(), depth, floor) && isBlock(pos.south().west()) && isBlock(pos.south(2));

        // 单格坑洞
        if (single && !x && !z && isBlock(pos.east()) && isBlock(pos.south())) {
            return new Hole(pos, HoleType.Single);
        }

        // 四格坑洞
        if (quad && x && z && isHole(pos.south().east(), depth, floor)
                && isBlock(pos.east().east().south()) && isBlock(pos.south().south().east())) {
            return new Hole(pos, HoleType.Quad);
        }

        if (!doubles) {
            return new Hole(pos, HoleType.NotHole);
        }

        // 东西向双格坑洞
        if (x && !z && isBlock(pos.south()) && isBlock(pos.south().east())) {
            return new Hole(pos, HoleType.DoubleX);
        }

        // 南北向双格坑洞
        if (z && !x && isBlock(pos.east()) && isBlock(pos.south().east())) {
            return new Hole(pos, HoleType.DoubleZ);
        }

        return new Hole(pos, HoleType.NotHole);
    }

    /**
     * 判断指定位置是否为可作为洞壁的抗爆方块。
     *
     * @param pos 目标位置
     * @return 判断结果
     */
    public static boolean isBlock(BlockPos pos) {
        return mc.level != null
                && mc.level.isLoaded(pos)
                && !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    static boolean isHole(BlockPos pos, int depth, boolean floor) {
        if (floor && !isBlock(pos.below())) {
            return false;
        }

        for (int i = 0; i < depth; i++) {
            if (isBlock(pos.above(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断玩家当前是否位于洞结构中。
     *
     * @param player 玩家
     * @return 判断结果
     */
    public static boolean inHole(Player player) {
        BlockPos pos = player.blockPosition();

        if (getHole(pos, 1).type == HoleType.Single) {
            return true;
        }

        // 东西向双格坑洞
        if (getHole(pos, 1).type == HoleType.DoubleX
                || getHole(pos.offset(-1, 0, 0), 1).type == HoleType.DoubleX) {
            return true;
        }

        // 南北向双格坑洞
        if (getHole(pos, 1).type == HoleType.DoubleZ
                || getHole(pos.offset(0, 0, -1), 1).type == HoleType.DoubleZ) {
            return true;
        }

        // 四格坑洞
        return getHole(pos, 1).type == HoleType.Quad
                || getHole(pos.offset(-1, 0, -1), 1).type == HoleType.Quad
                || getHole(pos.offset(-1, 0, 0), 1).type == HoleType.Quad
                || getHole(pos.offset(0, 0, -1), 1).type == HoleType.Quad;
    }

}
