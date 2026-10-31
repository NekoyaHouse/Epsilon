package com.github.epsilon.utils.player

import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.level.block.WebBlock

/**
 * 本地玩家相关工具类。
 */
object PlayerUtils {

    /**
     * 判断本地玩家是否正在使用食物。
     */
    context(nn: NonNullContext)
    fun isEating(): Boolean {
        return (nn.player.mainHandItem.components.has(DataComponents.FOOD) ||
                nn.player.offhandItem.components.has(DataComponents.FOOD)) && nn.player.isUsingItem
    }

    /**
     * 判断本地玩家的包围盒是否与蜘蛛网相交。
     */
    context(nn: NonNullContext)
    fun isInWeb(): Boolean {
        val box = nn.player.boundingBox.deflate(1.0E-6)

        val minX = Mth.floor(box.minX)
        val minY = Mth.floor(box.minY)
        val minZ = Mth.floor(box.minZ)
        val maxX = Mth.floor(box.maxX)
        val maxY = Mth.floor(box.maxY)
        val maxZ = Mth.floor(box.maxZ)

        val mutablePos = BlockPos.MutableBlockPos()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    mutablePos.set(x, y, z)
                    if (nn.level.getBlockState(mutablePos).block is WebBlock) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * 判断本地玩家的包围盒是否与实体方块相交。
     */
    context(nn: NonNullContext)
    fun isInBlock(): Boolean {
        val box = nn.player.boundingBox.deflate(1.0E-6)

        val minX = Mth.floor(box.minX)
        val minY = Mth.floor(box.minY)
        val minZ = Mth.floor(box.minZ)
        val maxX = Mth.floor(box.maxX)
        val maxY = Mth.floor(box.maxY)
        val maxZ = Mth.floor(box.maxZ)

        val mutablePos = BlockPos.MutableBlockPos()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    mutablePos.set(x, y, z)
                    if (nn.level.getBlockState(mutablePos).isSolidRender) {
                        return true
                    }
                }
            }
        }

        return false
    }

}
