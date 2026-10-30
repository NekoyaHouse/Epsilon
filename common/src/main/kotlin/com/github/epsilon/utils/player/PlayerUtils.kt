package com.github.epsilon.utils.player

import com.github.epsilon.utils.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.block.WebBlock

/**
 * 本地玩家相关工具类。
 *
 * 依赖玩家的方法统一通过 [NonNullContext] 读取上下文；上下文缺失时返回安全默认值。
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
    fun swingHand(hand: InteractionHand) {
        val ctx = NonNullContext.current ?: return
        ctx.player.swing(hand, SwingAnimation.DEFAULT, false)
    }

    /**
     * 判断本地玩家是否正在使用食物。
     */
    fun isEating(): Boolean {
        val ctx = NonNullContext.current ?: return false
        val player = ctx.player
        return (player.mainHandItem.components.has(DataComponents.FOOD) ||
                player.offhandItem.components.has(DataComponents.FOOD)) && player.isUsingItem
    }

    /**
     * 判断本地玩家的包围盒是否与蜘蛛网相交。
     */
    fun isInWeb(): Boolean {
        val ctx = NonNullContext.current ?: return false
        val box = ctx.player.boundingBox.deflate(1.0E-6)

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
                    if (ctx.world.getBlockState(mutablePos).block is WebBlock) {
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
    fun isInBlock(): Boolean {
        val ctx = NonNullContext.current ?: return false
        val box = ctx.player.boundingBox.deflate(1.0E-6)

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
                    if (ctx.world.getBlockState(mutablePos).isSolidRender()) {
                        return true
                    }
                }
            }
        }

        return false
    }

}
