package com.github.epsilon.utils.player

import com.github.epsilon.utils.safety.*
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.block.WebBlock

/**
 * 本地玩家相关工具类。
 */
object PlayerUtils {

    /**
     * 播放本地玩家的挥手动画。
     *
     * 26.3 移除了 `ServerboundSwingPacket`：服务端的挥手动画改由攻击、破坏方块等行为广播，
     * 客户端不再有独立的挥手网络包。因此统一改为播放本地动画，保证视觉表现与旧版本一致。
     *
     * @param hand 挥手使用的手
     */
    context(nn: NonNullContext)
    fun swingHand(hand: InteractionHand) {
        player.swing(hand, SwingAnimation.DEFAULT, false)
    }

    /**
     * 判断本地玩家是否正在使用食物。
     */
    context(nn: NonNullContext)
    fun isEating(): Boolean {
        return (player.mainHandItem.components.has(DataComponents.FOOD) ||
                player.offhandItem.components.has(DataComponents.FOOD)) && player.isUsingItem
    }

    /**
     * 判断本地玩家的包围盒是否与蜘蛛网相交。
     */
    context(nn: NonNullContext)
    fun isInWeb(): Boolean {
        val box = player.boundingBox.deflate(1.0E-6)

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
                    if (level.getBlockState(mutablePos).block is WebBlock) {
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
        val box = player.boundingBox.deflate(1.0E-6)

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
                    if (level.getBlockState(mutablePos).isSolidRender) {
                        return true
                    }
                }
            }
        }

        return false
    }

}
