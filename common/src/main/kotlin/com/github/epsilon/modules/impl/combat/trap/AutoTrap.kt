package com.github.epsilon.modules.impl.combat.trap

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.modules.impl.movement.Blink
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.util.*
import kotlin.math.nextDown

object AutoTrap : TrapBase("Auto Trap", EpsilonTranslations.AutoTrap.NO_BLOCK_FOUND, anchorToPlayer = false) {

    private val inheritedSettingCount = settings.size

    private val targetRange by doubleSetting("Target Range", 4.5, 1.0..8.0, 0.1)
    private val placeRange by doubleSetting("Place Range", 4.5, 1.0..6.0, 0.1)
    private val breakRange by doubleSetting("Break Range", 3.0, 1.0..6.0, 0.1, { breakCrystal })
    private val blocks by blockListSetting("Blocks", listOf(Blocks.OBSIDIAN))
    private val standHead by boolSetting("Stand Head", true)
    private val crawlHead by boolSetting("Crawl Head", true)
    private val feet by boolSetting("Feet", true)
    private val chest by boolSetting("Chest", true)
    private val antiStep by boolSetting("Anti Step", false, { standHead || crawlHead })
    private val floor by boolSetting("Floor", false)

    private var target: Player? = null
    private var positions: Set<BlockPos> = emptySet()

    override fun getInfo(): String? = target?.gameProfile?.name()

    init {
        Collections.rotate(settings, settings.size - inheritedSettingCount)

        onEnable {
            target = null
            positions = emptySet()
            clearPlacement()
        }

        // 先更新目标和轮廓，基类才能在同一 tick 放置，并停止转向旧目标。
        safeEventHandler<ClientTickEvent.Pre>(EventPriority.HIGH) {
            val nextTarget = if (Blink.isEnabled || !player.isAlive || player.isSpectator) null else findTarget()
            val nextPositions = nextTarget?.let {
                collectShapes(
                    it.boundingBox,
                    it.isVisuallyCrawling,
                    standHead,
                    crawlHead,
                    feet,
                    chest,
                    antiStep,
                    floor
                )
            } ?: emptySet()
            if (target !== nextTarget || positions != nextPositions) clearPlacement()
            target = nextTarget
            positions = nextPositions
        }
    }

    private fun NonNullContext.findTarget(): Player? = level.players().asSequence()
        .filter { it !== player && it.isAlive && !it.isSpectator && !FriendManager.isFriend(it) }
        .filter { player.distanceToSqr(it) <= targetRange * targetRange }
        .minByOrNull { player.distanceToSqr(it) }

    fun collectShapes(
        box: AABB,
        crawling: Boolean,
        standHead: Boolean,
        crawlHead: Boolean,
        feet: Boolean,
        chest: Boolean,
        antiStep: Boolean,
        floor: Boolean,
    ): Set<BlockPos> {
        val footY = Mth.floor(box.minY)
        val roofY = Mth.ceil(box.maxY)
        val footprint = linkedSetOf<BlockPos>()
        // 上边界不包含在碰撞箱内，恰好贴着格线时不能额外围一圈。
        for (x in Mth.floor(box.minX)..Mth.floor(box.maxX.nextDown())) {
            for (z in Mth.floor(box.minZ)..Mth.floor(box.maxZ.nextDown())) {
                footprint.add(BlockPos(x, footY, z))
            }
        }

        val positions = linkedSetOf<BlockPos>()
        for (origin in footprint) {
            if (if (crawling) crawlHead else standHead) {
                val roof = BlockPos(origin.x, roofY, origin.z)
                positions.add(roof)
                if (antiStep) positions.add(roof.above())
            }
            if (floor) positions.add(origin.below())
            for (direction in Direction.Plane.HORIZONTAL) {
                val side = origin.relative(direction)
                if (side in footprint) continue
                if (feet) positions.add(side)
                if (chest) positions.add(side.above())
            }
        }
        return positions
    }

    override fun NonNullContext.getTargetPositions(): Set<BlockPos> = positions

    override fun NonNullContext.getPlaceRange(): Double = minOf(placeRange, player.blockInteractionRange())

    override fun NonNullContext.getBreakRange(): Double = minOf(breakRange, player.entityInteractionRange())

    override fun getBlockItems(): List<Item> = blocks.map { it.asItem() }

}
