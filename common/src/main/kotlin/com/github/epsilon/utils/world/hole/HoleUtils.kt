package com.github.epsilon.utils.world.hole

import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player

object HoleUtils {

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos 目标位置
     * @return 操作结果
     */
    context(nn: NonNullContext)
    fun getHole(pos: BlockPos): Hole {
        return getHole(pos, true, true, true, 3, true)
    }

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos   目标位置
     * @param depth 洞结构需要检查的内部高度
     * @return 操作结果
     */
    context(nn: NonNullContext)
    fun getHole(pos: BlockPos, depth: Int): Hole {
        return getHole(pos, depth, true)
    }

    /**
     * 检测指定位置开始的洞结构。
     *
     * @param pos   目标位置
     * @param depth 洞结构需要检查的内部高度
     * @param floor 是否要求洞底为抗爆方块
     * @return 操作结果
     */
    context(nn: NonNullContext)
    fun getHole(pos: BlockPos, depth: Int, floor: Boolean): Hole {
        return getHole(pos, true, true, true, depth, floor)
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
    context(nn: NonNullContext)
    fun getHole(pos: BlockPos, single: Boolean, doubles: Boolean, quad: Boolean, depth: Int, floor: Boolean): Hole {
        if (!isHole(pos, depth, floor)) {
            return Hole(pos, HoleType.NotHole)
        }

        if (!isBlock(pos.west()) || !isBlock(pos.north())) {
            return Hole(pos, HoleType.NotHole)
        }

        val x = isHole(pos.east(), depth, floor) && isBlock(pos.east().north()) && isBlock(pos.east(2))
        val z = isHole(pos.south(), depth, floor) && isBlock(pos.south().west()) && isBlock(pos.south(2))

        // 单格坑洞
        if (single && !x && !z && isBlock(pos.east()) && isBlock(pos.south())) {
            return Hole(pos, HoleType.Single)
        }

        // 四格坑洞
        if (quad && x && z && isHole(pos.south().east(), depth, floor)
            && isBlock(pos.east().east().south()) && isBlock(pos.south().south().east())
        ) {
            return Hole(pos, HoleType.Quad)
        }

        if (!doubles) {
            return Hole(pos, HoleType.NotHole)
        }

        // 东西向双格坑洞
        if (x && !z && isBlock(pos.south()) && isBlock(pos.south().east())) {
            return Hole(pos, HoleType.DoubleX)
        }

        // 南北向双格坑洞
        if (z && !x && isBlock(pos.east()) && isBlock(pos.south().east())) {
            return Hole(pos, HoleType.DoubleZ)
        }

        return Hole(pos, HoleType.NotHole)
    }

    /**
     * 判断指定位置是否为可作为洞壁的抗爆方块。
     *
     * @param pos 目标位置
     * @return 判断结果
     */
    context(nn: NonNullContext)
    fun isBlock(pos: BlockPos): Boolean {
        return nn.level.isLoaded(pos)
                && !nn.level.getBlockState(pos).getCollisionShape(nn.level, pos).isEmpty
    }

    context(nn: NonNullContext)
    private fun isHole(pos: BlockPos, depth: Int, floor: Boolean): Boolean {
        if (floor && !isBlock(pos.below())) {
            return false
        }

        for (i in 0 until depth) {
            if (isBlock(pos.above(i))) {
                return false
            }
        }
        return true
    }

    /**
     * 判断玩家当前是否位于洞结构中。
     *
     * @param player 玩家
     * @return 判断结果
     */
    context(nn: NonNullContext)
    fun inHole(player: Player): Boolean {
        val pos = player.blockPosition()

        if (getHole(pos, 1).type == HoleType.Single) {
            return true
        }

        // 东西向双格坑洞
        if (getHole(pos, 1).type == HoleType.DoubleX
            || getHole(pos.offset(-1, 0, 0), 1).type == HoleType.DoubleX
        ) {
            return true
        }

        // 南北向双格坑洞
        if (getHole(pos, 1).type == HoleType.DoubleZ
            || getHole(pos.offset(0, 0, -1), 1).type == HoleType.DoubleZ
        ) {
            return true
        }

        // 四格坑洞
        return getHole(pos, 1).type == HoleType.Quad
                || getHole(pos.offset(-1, 0, -1), 1).type == HoleType.Quad
                || getHole(pos.offset(-1, 0, 0), 1).type == HoleType.Quad
                || getHole(pos.offset(0, 0, -1), 1).type == HoleType.Quad
    }

}
