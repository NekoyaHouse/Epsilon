package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import java.util.*
import kotlin.math.nextDown

object SelfTrap : BlockTrapModule("Self Trap", EpsilonTranslations.SelfTrap.NO_BLOCK_FOUND) {

    private val inheritedSettingCount = settings.size

    private val surround by boolSetting("Surround", false)
    private val trap by boolSetting("Trap", true)
    private val standHead by boolSetting("Stand Head", true)
    private val crawlHead by boolSetting("Crawl Head", true)
    private val floor by boolSetting("Floor", true)

    init {
        // 我绷不住了老铁放下面太难看了
        Collections.rotate(settings, settings.size - inheritedSettingCount)
    }

    context(nn: NonNullContext)
    override fun getTargetPositions(): Set<BlockPos> = collectPositions(
        nn.player.boundingBox, nn.player.isVisuallyCrawling, surround, trap, standHead, crawlHead, floor
    )

    // 取碰撞箱覆盖的全部格子，跨格站立时也不会把玩家占用的格子当作侧墙。
    private fun collectPositions(
        box: AABB,
        crawling: Boolean,
        surround: Boolean,
        trap: Boolean,
        standHead: Boolean,
        crawlHead: Boolean,
        floor: Boolean,
    ): Set<BlockPos> {
        val footY = Mth.floor(box.minY)
        val roofY = Mth.ceil(box.maxY)
        val footprint = linkedSetOf<BlockPos>()
        for (x in Mth.floor(box.minX)..Mth.floor(box.maxX.nextDown())) {
            for (z in Mth.floor(box.minZ)..Mth.floor(box.maxZ.nextDown())) {
                footprint.add(BlockPos(x, footY, z))
            }
        }

        val positions = linkedSetOf<BlockPos>()
        for (origin in footprint) {
            if (floor) positions.add(origin.below())
            for (direction in Direction.Plane.HORIZONTAL) {
                val side = origin.relative(direction)
                if (side in footprint) continue
                if (surround) positions.add(side)
                if (trap) positions.add(side.above())
            }
            if (if (crawling) crawlHead else standHead) {
                positions.add(BlockPos(origin.x, roofY, origin.z))
            }
        }
        return positions
    }

}
