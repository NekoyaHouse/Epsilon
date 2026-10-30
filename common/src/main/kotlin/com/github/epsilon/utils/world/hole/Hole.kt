package com.github.epsilon.utils.world.hole

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

class Hole(val pos: BlockPos, val type: HoleType) {

    val positions: Array<BlockPos> = when (type) {
        HoleType.Single -> arrayOf(pos)
        HoleType.DoubleX -> arrayOf(pos, pos.offset(1, 0, 0))
        HoleType.DoubleZ -> arrayOf(pos, pos.offset(0, 0, 1))
        HoleType.Quad -> arrayOf(pos, pos.offset(1, 0, 0), pos.offset(0, 0, 1), pos.offset(1, 0, 1))
        else -> emptyArray()
    }

    val middle: Vec3 = when (type) {
        HoleType.Single -> Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        HoleType.DoubleX -> Vec3((pos.x + 1).toDouble(), pos.y.toDouble(), pos.z + 0.5)
        HoleType.DoubleZ -> Vec3(pos.x + 0.5, pos.y.toDouble(), (pos.z + 1).toDouble())
        HoleType.Quad -> Vec3((pos.x + 1).toDouble(), pos.y.toDouble(), (pos.z + 1).toDouble())
        else -> Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
    }

}
